package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator
import org.apache.sshd.server.auth.keyboard.UserAuthKeyboardInteractiveFactory
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PasswordLoginSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var server: SshServer? = null
    private var client: SSHClient? = null

    @After
    fun tearDown() {
        runCatching { client?.disconnect() }
        runCatching { server?.stop(true) }
    }

    @Test
    fun passwordServerLogsInAndAWrongPasswordIsOneAttempt() {
        val attempts = AtomicInteger()
        startServer {
            passwordAuthenticator = PasswordAuthenticator { _, password, _ ->
                attempts.incrementAndGet()
                password == PASSWORD
            }
        }

        try {
            connect().authPasswordOrInteractive(USER, "wrong")
            fail("wrong password logged in")
        } catch (_: UserAuthException) {
        }
        // sshj's own authPassword would retry the same password over keyboard-interactive.
        assertEquals(1, attempts.get())

        connect().authPasswordOrInteractive(USER, PASSWORD)
        assertTrue(client!!.isAuthenticated)
    }

    @Test
    fun keyboardInteractiveOnlyServerGetsThePasswordAndAsksTheUserForTheCode() {
        val questions = CopyOnWriteArrayList<LoginChallenge>()
        startTwoFactorServer()

        connect().authPasswordOrInteractive(USER, PASSWORD) { challenge ->
            questions += challenge
            CODE
        }

        assertTrue(client!!.isAuthenticated)
        assertEquals(1, questions.size)
        assertEquals("Verification code:", questions.single().prompt)
        assertTrue(questions.single().echo)
        assertEquals("Two-step login", questions.single().instruction)
    }

    @Test
    fun dismissingTheCodeQuestionCancelsTheLogin() {
        startTwoFactorServer()

        try {
            connect().authPasswordOrInteractive(USER, PASSWORD) { null }
            fail("login without a code succeeded")
        } catch (_: LoginCanceledException) {
        }
    }

    @Test
    fun keyboardInteractiveOnlyServerRejectsAWrongPassword() {
        startTwoFactorServer()

        try {
            connect().authPasswordOrInteractive(USER, "wrong") { CODE }
            fail("wrong password logged in")
        } catch (failure: UserAuthException) {
            assertTrue(failure !is LoginCanceledException)
        }
    }

    private fun startTwoFactorServer() = startServer {
        passwordAuthenticator = null
        userAuthFactories = listOf(UserAuthKeyboardInteractiveFactory.INSTANCE)
        keyboardInteractiveAuthenticator = object : KeyboardInteractiveAuthenticator {
            override fun generateChallenge(
                session: ServerSession?,
                username: String?,
                lang: String?,
                subMethods: String?
            ) = InteractiveChallenge().apply {
                interactionInstruction = "Two-step login"
                addPrompt("Password: ", false)
                addPrompt("Verification code: ", true)
            }

            override fun authenticate(session: ServerSession?, username: String?, responses: List<String>?) =
                username == USER && responses == listOf(PASSWORD, CODE)
        }
    }

    private fun startServer(configure: SshServer.() -> Unit) {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("hostkey").toPath())
            configure()
            start()
        }
    }

    private fun connect(): SSHClient {
        runCatching { client?.disconnect() }
        val target = server!!
        val host = HostConnection(
            id = "kbd-interactive",
            name = "Keyboard-interactive test",
            host = "127.0.0.1",
            port = target.port,
            username = USER,
            preferredAuth = AuthMethod.PASSWORD
        )
        return SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile(),
            host = host
        ).also {
            it.connect(host.host, host.port)
            client = it
        }
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
        const val CODE = "246810"
    }
}
