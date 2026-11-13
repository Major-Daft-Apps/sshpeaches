package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Connections through jump hosts, against real SSH servers in this process: the target only ever sees the last jump
 * host, each hop logs in with its own account, and closing the client closes the whole route.
 */
class JumpHostSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val servers = mutableListOf<SshServer>()
    private val clients = CopyOnWriteArrayList<SSHClient>()
    /** "user@server" for every login a server accepted, in order. */
    private val logins = CopyOnWriteArrayList<String>()

    private fun server(name: String, forwarding: Boolean): SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(temp.root.toPath().resolve("$name.ser")).apply { setAlgorithm("EC") }
        passwordAuthenticator = PasswordAuthenticator { user, password, _ ->
            (user == "user-$name" && password == "pass-$name").also { if (it) logins += "$user@$name" }
        }
        shellFactory = ProcessShellFactory("cat", "cat")
        forwardingFilter = if (forwarding) AcceptAllForwardingFilter.INSTANCE else RejectAllForwardingFilter.INSTANCE
        start()
        servers += this
    }

    private fun hostFor(name: String, server: SshServer, jump: String? = null) = HostConnection(
        id = name, name = name, host = "127.0.0.1", port = server.port, username = "user-$name",
        preferredAuth = AuthMethod.PASSWORD, jumpHostId = jump
    )

    private fun newClient(): RoutedSSHClient = RoutedSSHClient(DefaultConfig()).also {
        it.addHostKeyVerifier(PromiscuousVerifier())
        clients += it
    }

    private fun connect(target: HostConnection, jumps: List<HostConnection>, jumpPassword: (HostConnection) -> String = { "pass-${it.name}" }): RoutedSSHClient {
        val client = newClient()
        SshRoute.connect(
            client = client,
            host = target,
            jumps = jumps,
            newClient = { newClient() },
            logIn = { hop, jump -> hop.authPassword(jump.username, jumpPassword(jump)) }
        )
        client.authPassword(target.username, "pass-${target.name}")
        return client
    }

    private fun echo(client: SSHClient, text: String): String {
        client.startSession().use { session ->
            val shell = session.startShell()
            shell.outputStream.apply { write("$text\n".toByteArray()); flush() }
            val buffer = ByteArray(256)
            val seen = StringBuilder()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && !seen.contains(text)) {
                if (shell.inputStream.available() > 0) {
                    val n = shell.inputStream.read(buffer)
                    if (n > 0) seen.append(String(buffer, 0, n))
                } else {
                    Thread.sleep(20)
                }
            }
            return seen.toString().trim()
        }
    }

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.disconnect() } }
        servers.forEach { runCatching { it.stop(true) } }
    }

    @Test
    fun aSessionRunsThroughOneJumpHost() {
        val bastion = server("bastion", forwarding = true)
        val inside = server("inside", forwarding = false)
        val client = connect(hostFor("inside", inside, jump = "bastion"), listOf(hostFor("bastion", bastion)))

        assertTrue(client.isConnected && client.isAuthenticated && client.viaJumpHost)
        assertEquals("hello through the bastion", echo(client, "hello through the bastion"))
        assertEquals(listOf("user-bastion@bastion", "user-inside@inside"), logins.toList())
        // The target was reached from the bastion's side: this client has no socket of its own.
        assertEquals(null, client.socket)
        assertEquals(1, client.carriers.size)
    }

    @Test
    fun aChainOfJumpHostsIsWalkedInOrder() {
        val outer = server("outer", forwarding = true)
        val middle = server("middle", forwarding = true)
        val inside = server("inside", forwarding = false)
        val client = connect(
            hostFor("inside", inside, jump = "middle"),
            listOf(hostFor("outer", outer), hostFor("middle", middle, jump = "outer"))
        )
        assertEquals("two hops", echo(client, "two hops"))
        assertEquals(listOf("user-outer@outer", "user-middle@middle", "user-inside@inside"), logins.toList())
        assertEquals(2, client.carriers.size)
    }

    @Test
    fun closingTheClientClosesItsJumpConnections() {
        val bastion = server("bastion", forwarding = true)
        val inside = server("inside", forwarding = false)
        val client = connect(hostFor("inside", inside, jump = "bastion"), listOf(hostFor("bastion", bastion)))
        val hop = client.carriers.single()
        assertTrue(hop.isConnected)

        client.disconnect()

        assertFalse(hop.isConnected)
        assertTrue(client.carriers.isEmpty())
        val deadline = System.currentTimeMillis() + 5_000
        while (bastion.activeSessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("the bastion still has a session", bastion.activeSessions.isEmpty())
    }

    @Test
    fun aDeadJumpConnectionEndsTheSessionBehindIt() {
        val bastion = server("bastion", forwarding = true)
        val inside = server("inside", forwarding = false)
        val client = connect(hostFor("inside", inside, jump = "bastion"), listOf(hostFor("bastion", bastion)))
        val session = client.startSession()
        val shell = session.startShell()

        client.cut()

        val ended = runCatching {
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                if (shell.inputStream.read() < 0) return@runCatching true
            }
            false
        }.getOrDefault(true)
        assertTrue("the shell behind a dead jump host never ended", ended)
        // join() rethrows what killed the transport: the closed socket under the jump host.
        runCatching { client.transport.join(5, TimeUnit.SECONDS) }
        assertFalse(client.transport.isRunning)
    }

    @Test
    fun aJumpHostThatRefusesToForwardIsNamed() {
        val bastion = server("bastion", forwarding = false)
        val inside = server("inside", forwarding = false)
        val error = runCatching {
            connect(hostFor("inside", inside, jump = "bastion"), listOf(hostFor("bastion", bastion)))
        }.exceptionOrNull()
        assertTrue("got $error", error is SshRouteException)
        assertTrue(error!!.message!!.contains("bastion couldn't open a connection to 127.0.0.1:${inside.port}"))
        // Nothing is left open on the bastion.
        val deadline = System.currentTimeMillis() + 5_000
        while (bastion.activeSessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(bastion.activeSessions.isEmpty())
        assertEquals(listOf("user-bastion@bastion"), logins.toList())
    }

    @Test
    fun aJumpHostThatRefusesTheLoginStopsTheRoute() {
        val bastion = server("bastion", forwarding = true)
        val inside = server("inside", forwarding = false)
        val error = runCatching {
            connect(hostFor("inside", inside, jump = "bastion"), listOf(hostFor("bastion", bastion))) { "wrong" }
        }.exceptionOrNull()
        assertTrue("got $error", error != null)
        assertTrue(logins.isEmpty())
        val deadline = System.currentTimeMillis() + 5_000
        while (bastion.activeSessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(bastion.activeSessions.isEmpty())
    }

    @Test
    fun anExtraConnectionRidesOnTheSessionsLastHop() {
        val bastion = server("bastion", forwarding = true)
        val inside = server("inside", forwarding = false)
        val target = hostFor("inside", inside, jump = "bastion")
        val session = connect(target, listOf(hostFor("bastion", bastion)))

        val extra = newClient()
        SshRoute.connectThrough(extra, target, session.carriers.last())
        extra.authPassword(target.username, "pass-inside")
        assertEquals("second connection", echo(extra, "second connection"))
        // One login on the bastion carries both.
        assertEquals(1, logins.count { it.endsWith("@bastion") })

        extra.disconnect()
        assertTrue("closing the extra connection must leave the session's route open", session.carriers.single().isConnected)
        assertEquals("still here", echo(session, "still here"))
    }
}
