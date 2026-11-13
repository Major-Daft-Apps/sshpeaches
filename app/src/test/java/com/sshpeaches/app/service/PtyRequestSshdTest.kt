package com.majordaftapps.sshpeaches.app.service

import java.util.concurrent.atomic.AtomicInteger
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.RequestHandler
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.channel.ChannelSessionFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The terminal request against a real SSH server that answers late or says no, counting the requests it receives.
 * 0.14.1 sent a second one when the first answer was late; OpenSSH disconnects on that ("you already have a pty").
 */
class PtyRequestSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: SshServer
    private val ptyRequests = AtomicInteger()
    @Volatile private var answerAfterMs = 0L
    @Volatile private var refuseFirst = false
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
            channelFactories = listOf(object : ChannelSessionFactory() {
                override fun createChannel(session: Session): Channel = object : ChannelSession() {
                    override fun handlePtyReq(buffer: Buffer, wantReply: Boolean): RequestHandler.Result {
                        val nth = ptyRequests.incrementAndGet()
                        Thread.sleep(answerAfterMs)
                        if (refuseFirst && nth == 1) return RequestHandler.Result.ReplyFailure
                        return super.handlePtyReq(buffer, wantReply)
                    }
                }
            })
            start()
        }
    }

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.disconnect() } }
        runCatching { server.stop(true) }
    }

    private fun connect(timeoutMs: Int): SSHClient = SSHClient(DefaultConfig()).also { client ->
        clients += client
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect("127.0.0.1", server.port)
        client.authPassword("u", "p")
        client.connection.timeoutMs = timeoutMs
    }

    @Test
    fun aLateAnswerIsNotFollowedByASecondRequest() {
        answerAfterMs = 1_500
        val session = connect(timeoutMs = 400).startSession()
        try {
            requestPty(session, "xterm-256color", 80, 24)
            fail("The request should have timed out")
        } catch (expected: ConnectionException) {
            // The answer was still on its way.
        }
        Thread.sleep(2_500)
        assertEquals("pty requests the server received", 1, ptyRequests.get())
    }

    @Test
    fun aRefusalIsFollowedByThePlainRequest() {
        refuseFirst = true
        val session = connect(timeoutMs = 5_000).startSession()
        requestPty(session, "no-such-terminal", 80, 24)
        assertEquals("pty requests the server received", 2, ptyRequests.get())
        assertTrue(session.isOpen)
        session.startShell().close()
    }

    @Test
    fun aPromptAnswerNeedsOneRequest() {
        val session = connect(timeoutMs = 5_000).startSession()
        requestPty(session, "xterm-256color", 120, 40)
        assertEquals(1, ptyRequests.get())
    }
}
