package com.majordaftapps.sshpeaches.app.service

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.keepalive.KeepAliveRunner
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A connection that dies silently (a NAT entry dropped while the phone slept: no reset, no network change) must be
 * noticed, or typing goes nowhere until the session is killed. The proxy here stops forwarding without closing either
 * side, which is what such a link looks like.
 */
class ConnectionLivenessTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: SshServer
    private lateinit var proxy: BlackholeProxy
    private val clients = mutableListOf<SSHClient>()

    @Before
    fun setUp() {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.root.toPath().resolve("host.ser")).apply {
                setAlgorithm("EC")
            }
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == "u" && password == "p" }
            shellFactory = ProcessShellFactory("cat", "cat")
            start()
        }
        proxy = BlackholeProxy(server.port)
    }

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.socket?.close() } }
        proxy.close()
        runCatching { server.stop(true) }
    }

    @Test
    fun replyCheckedKeepaliveKillsASilentlyDeadLinkSoTheShellReaderFails() {
        val client = connect(keepAliveIntervalSeconds = 1, maxUnanswered = 2)
        val shell = client.startSession().apply { allocateDefaultPTY() }.startShell()
        shell.outputStream.apply { write("ping\n".toByteArray()); flush() }
        assertTrue(readUntil(shell.inputStream, "ping"))

        proxy.blackhole.set(true)
        val readerFailed = AtomicBoolean(false)
        val reader = Thread {
            try {
                while (shell.inputStream.read(ByteArray(256)) >= 0) Unit
            } catch (_: IOException) {
                readerFailed.set(true)
            }
        }.apply { isDaemon = true; start() }
        // Typing into the dead link still "succeeds", as it did in the bug.
        shell.outputStream.apply { write("lost\n".toByteArray()); flush() }

        reader.join(15_000)
        assertTrue("The shell reader never noticed the dead connection", readerFailed.get())
    }

    @Test
    fun heartbeatKeepaliveNeverNoticesTheDeadLink() {
        // What 0.11.5 used: SSH_MSG_IGNORE gets no reply, so nothing can time out.
        val client = connect(keepAliveIntervalSeconds = 1, maxUnanswered = null)
        val shell = client.startSession().apply { allocateDefaultPTY() }.startShell()
        proxy.blackhole.set(true)
        Thread.sleep(5_000)
        assertTrue(client.isConnected)
        assertEquals(0, shell.inputStream.available())
    }

    @Test
    fun livenessProbeTellsALiveServerFromADeadLink() {
        val client = connect(keepAliveIntervalSeconds = 0, maxUnanswered = null)
        client.startSession().apply { allocateDefaultPTY() }.startShell()

        // MINA, like OpenSSH, refuses keepalive@openssh.com: a refusal is an answer.
        val refusal = runCatching {
            client.connection.sendGlobalRequest("keepalive@openssh.com", true, ByteArray(0))
                .retrieve(5, TimeUnit.SECONDS)
        }.exceptionOrNull()
        assertTrue("Expected a refusal, got $refusal", refusal == null || isGlobalRequestRefusal(refusal))

        proxy.blackhole.set(true)
        val timeout = runCatching {
            client.connection.sendGlobalRequest("keepalive@openssh.com", true, ByteArray(0))
                .retrieve(1, TimeUnit.SECONDS)
        }.exceptionOrNull()
        assertTrue(timeout is ConnectionException && timeout.cause is TimeoutException)
        assertFalse(isGlobalRequestRefusal(timeout!!))
    }

    @Test
    fun closingTheSocketUnblocksTheShellReader() {
        // What the service does when the probe or an input write times out.
        val client = connect(keepAliveIntervalSeconds = 0, maxUnanswered = null)
        val shell = client.startSession().apply { allocateDefaultPTY() }.startShell()
        proxy.blackhole.set(true)
        val readerFailed = AtomicBoolean(false)
        val reader = Thread {
            try {
                while (shell.inputStream.read(ByteArray(256)) >= 0) Unit
            } catch (_: IOException) {
                readerFailed.set(true)
            }
        }.apply { isDaemon = true; start() }
        Thread.sleep(200)
        client.socket.close()
        reader.join(5_000)
        assertTrue(readerFailed.get())
    }

    @Test
    fun globalRequestRefusalIsRecognisedOnlyForTheRefusalMessage() {
        assertTrue(isGlobalRequestRefusal(ConnectionException("Global request [<<global req for keepalive@openssh.com>>] failed")))
        assertFalse(isGlobalRequestRefusal(ConnectionException("Broken transport; encountered EOF")))
        assertFalse(isGlobalRequestRefusal(ConnectionException("Global request [x] failed", IOException("eof"))))
        assertFalse(isGlobalRequestRefusal(IOException("Global request [x] failed")))
    }

    private fun connect(keepAliveIntervalSeconds: Int, maxUnanswered: Int?): SSHClient {
        val config = DefaultConfig().apply {
            if (maxUnanswered != null) keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
        }
        return SSHClient(config).apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connection.keepAlive.keepAliveInterval = keepAliveIntervalSeconds
            if (maxUnanswered != null) (connection.keepAlive as KeepAliveRunner).maxAliveCount = maxUnanswered
            connect("127.0.0.1", proxy.port)
            authPassword("u", "p")
            clients += this
        }
    }

    private fun readUntil(input: InputStream, text: String): Boolean {
        val seen = StringBuilder()
        val deadline = System.currentTimeMillis() + 5_000
        val buffer = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            if (input.available() == 0) {
                Thread.sleep(20)
                continue
            }
            val read = input.read(buffer)
            if (read < 0) return false
            seen.append(String(buffer, 0, read))
            if (seen.contains(text)) return true
        }
        return false
    }

    /** Forwards one connection to the server; with [blackhole] set it swallows everything and closes nothing. */
    private class BlackholeProxy(targetPort: Int) : AutoCloseable {
        val blackhole = AtomicBoolean(false)
        private val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val sockets = mutableListOf<Socket>()
        val port: Int = listener.localPort

        init {
            Thread {
                runCatching {
                    while (true) {
                        val inbound = listener.accept()
                        val outbound = Socket(InetAddress.getLoopbackAddress(), targetPort)
                        synchronized(sockets) { sockets += inbound; sockets += outbound }
                        pump(inbound.getInputStream(), outbound.getOutputStream())
                        pump(outbound.getInputStream(), inbound.getOutputStream())
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun pump(from: InputStream, to: OutputStream) {
            Thread {
                runCatching {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = from.read(buffer)
                        if (read < 0) break
                        if (blackhole.get()) continue
                        to.write(buffer, 0, read)
                        to.flush()
                    }
                }
            }.apply { isDaemon = true; start() }
        }

        override fun close() {
            runCatching { listener.close() }
            synchronized(sockets) { sockets.forEach { runCatching { it.close() } } }
        }
    }
}
