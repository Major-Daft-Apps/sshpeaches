package com.majordaftapps.sshpeaches.app.data.ssh

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.CopyOnWriteArrayList
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.agent.SshAgent
import org.apache.sshd.agent.SshAgentFactory
import org.apache.sshd.agent.SshAgentServer
import org.apache.sshd.agent.local.AgentServerProxy
import org.apache.sshd.agent.local.ProxyAgentFactory
import org.apache.sshd.common.session.ConnectionService
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Agent forwarding against a real SSH server in this process. The server's own agent client (Apache MINA's, which
 * speaks the agent protocol as OpenSSH's tools do) opens an agent channel back to the app's [ForwardedAgent], lists
 * its keys and has it sign; the signatures are checked with the public keys.
 */
class ForwardedAgentSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: SshServer
    private val agentServers = CopyOnWriteArrayList<AgentServerProxy>()
    private val clients = mutableListOf<SSHClient>()
    private val signedWith = CopyOnWriteArrayList<String>()

    private val rsa: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val ecdsa: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    @Before
    fun setUp() {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.root.toPath().resolve("host.ser")).apply { setAlgorithm("EC") }
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == "u" && password == "p" }
            shellFactory = ProcessShellFactory("cat", "cat")
            forwardingFilter = AcceptAllForwardingFilter.INSTANCE
            // What sshd does on "auth-agent-req@openssh.com": keep a way to reach the client's agent.
            agentFactory = object : SshAgentFactory by ProxyAgentFactory() {
                override fun createServer(service: ConnectionService): SshAgentServer =
                    AgentServerProxy(service).also { agentServers += it }
            }
            // How long the server waits for an agent channel it asked for (the refused one below).
            org.apache.sshd.core.CoreModuleProperties.CHANNEL_OPEN_TIMEOUT.set(this, java.time.Duration.ofSeconds(3))
            start()
        }
    }

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.disconnect() } }
        runCatching { server.stop(true) }
    }

    private fun connect(): SSHClient = SSHClient(DefaultConfig()).also { client ->
        clients += client
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect("127.0.0.1", server.port)
        client.authPassword("u", "p")
    }

    private fun agentFor(client: SSHClient) = ForwardedAgent(
        client = client,
        loadKeys = {
            listOf(
                ForwardedAgent.Key("rsa key", rsa.public, rsa.private),
                ForwardedAgent.Key("ecdsa key", ecdsa.public, ecdsa.private)
            )
        },
        onSigned = { signedWith += it }
    )

    /** Opens a shell with the agent offered, and returns the server's handle on that agent. */
    private fun serverSideAgent(client: SSHClient, agent: ForwardedAgent): SshAgent {
        agent.attach()
        val session = client.startSession()
        session.allocateDefaultPTY()
        agent.requestOn(session)
        session.startShell()
        val deadline = System.currentTimeMillis() + 5_000
        while (agentServers.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("the server never saw the agent request", agentServers.isNotEmpty())
        return agentServers.last().createClient()
    }

    @Test
    fun theServerListsTheKeysAndGetsValidSignatures() {
        val client = connect()
        val remote = serverSideAgent(client, agentFor(client))

        val offered = remote.identities.toList()
        assertEquals(listOf("rsa key", "ecdsa key"), offered.map { it.value })
        // Compared in SSH's own form: the server's key classes encode the same key differently from Java's.
        assertArrayEquals(blob(rsa.public), blob(offered[0].key))
        assertArrayEquals(blob(ecdsa.public), blob(offered[1].key))

        val data = "challenge from another server".toByteArray()

        val rsaSigned = remote.sign(null, offered[0].key, "rsa-sha2-512", data)
        assertNotNull(rsaSigned)
        assertEquals("rsa-sha2-512", rsaSigned.key)
        assertTrue(Signature.getInstance("SHA512withRSA").run { initVerify(rsa.public); update(data); verify(rsaSigned.value) })

        val rsa256 = remote.sign(null, offered[0].key, "rsa-sha2-256", data)
        assertEquals("rsa-sha2-256", rsa256.key)
        assertTrue(Signature.getInstance("SHA256withRSA").run { initVerify(rsa.public); update(data); verify(rsa256.value) })

        val ecSigned = remote.sign(null, offered[1].key, "ecdsa-sha2-nistp256", data)
        assertEquals("ecdsa-sha2-nistp256", ecSigned.key)
        // The SSH form is two mpints (r, s); Java wants them as a DER sequence.
        val parts = Buffer.PlainBuffer(ecSigned.value)
        val r = parts.readMPInt()
        val s = parts.readMPInt()
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(ecdsa.public); update(data); verify(der(r, s)) })

        assertEquals(listOf("rsa key", "rsa key", "ecdsa key"), signedWith.toList())
    }

    @Test
    fun aKeyTheAgentDoesNotHoldAndRsaWithSha1AreRefused() {
        val client = connect()
        val remote = serverSideAgent(client, agentFor(client))
        val stranger = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val data = "x".toByteArray()

        assertTrue(runCatching { remote.sign(null, stranger.public, "rsa-sha2-256", data) }.isFailure)
        assertTrue(runCatching { remote.sign(null, rsa.public, "ssh-rsa", data) }.isFailure)
        assertTrue(signedWith.isEmpty())
        // The agent still answers afterwards.
        assertEquals(2, remote.identities.count())
    }

    @Test
    fun withoutTheSettingTheServerCannotReachAnAgent() {
        val client = connect()
        val agent = agentFor(client)
        // Asked for on the session, but never attached: what a host without agent forwarding looks like to a server
        // that tries anyway.
        val session = client.startSession()
        session.allocateDefaultPTY()
        agent.requestOn(session)
        session.startShell()
        val deadline = System.currentTimeMillis() + 5_000
        while (agentServers.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        // The server's attempt to open an agent channel is turned down, so it has nothing to list or sign with.
        assertTrue(runCatching { agentServers.last().createClient().identities.toList() }.isFailure)
        assertTrue(signedWith.isEmpty())
    }

    @Test
    fun otherAgentRequestsAreRefused() {
        val client = connect()
        val agent = agentFor(client)
        // SSH_AGENTC_REMOVE_ALL_IDENTITIES (19), SSH_AGENTC_ADD_IDENTITY (17), SSH_AGENTC_LOCK (22), and nonsense.
        for (type in listOf(19, 17, 22, 0, 200)) {
            assertArrayEquals("request $type", byteArrayOf(5), agent.answer(byteArrayOf(type.toByte(), 0, 0, 0, 1, 1)))
        }
        // The list is still whole.
        assertEquals(12, agent.answer(byteArrayOf(11))[0].toInt())
    }

    private fun blob(key: java.security.PublicKey): ByteArray = Buffer.PlainBuffer().putPublicKey(key).compactData

    private fun der(r: java.math.BigInteger, s: java.math.BigInteger): ByteArray {
        fun int(v: java.math.BigInteger): ByteArray = v.toByteArray().let { byteArrayOf(2, it.size.toByte()) + it }
        val body = int(r) + int(s)
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
