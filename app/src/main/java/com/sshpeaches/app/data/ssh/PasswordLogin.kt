package com.majordaftapps.sshpeaches.app.data.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.userauth.password.Resource

/** A keyboard-interactive question from the server that the password doesn't answer, e.g. a 2FA code. */
data class LoginChallenge(
    val prompt: String,
    /** Whether the server says the answer may be shown while typed (a code), or must be hidden. */
    val echo: Boolean,
    /** Text the server sent with the questions, if any. */
    val instruction: String
)

/** The user dismissed a login question, so the login stops instead of failing as a wrong answer. */
class LoginCanceledException : UserAuthException("Login canceled")

/**
 * Answers keyboard-interactive questions: the first password question gets [password], every
 * other question goes to [askUser] (null means nobody can answer, so it gets an empty reply).
 */
class InteractiveLoginResponder(
    private val password: String?,
    private val askUser: ((LoginChallenge) -> String?)?
) : ChallengeResponseProvider {
    private var instruction = ""
    private var passwordSent = false

    @Volatile
    var canceled = false
        private set

    override fun getSubmethods(): List<String> = emptyList()

    override fun init(resource: Resource<*>?, name: String?, instruction: String?) {
        this.instruction = listOfNotNull(name, instruction).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    override fun getResponse(prompt: String, echo: Boolean): CharArray {
        if (canceled) return CharArray(0)
        if (!echo && !passwordSent && !password.isNullOrEmpty() && isPasswordPrompt(prompt)) {
            passwordSent = true
            return password.toCharArray()
        }
        val ask = askUser ?: return CharArray(0)
        val answer = ask(LoginChallenge(prompt = prompt.trim(), echo = echo, instruction = instruction))
        if (answer == null) {
            canceled = true
            return CharArray(0)
        }
        return answer.toCharArray()
    }

    override fun shouldRetry(): Boolean = false

    companion object {
        fun isPasswordPrompt(prompt: String): Boolean = prompt.contains("password", ignoreCase = true)
    }
}

/**
 * Logs in with [password] by the "password" method, or by "keyboard-interactive" when that is the
 * only way the server takes passwords (PAM, 2FA). A password the server refused is never sent a
 * second time by another method, so a typo costs one failed attempt, not two.
 *
 * @param askUser answers server questions other than the password; called on sshj's reader thread.
 */
fun SSHClient.authPasswordOrInteractive(
    username: String,
    password: String,
    interactiveTimeoutMs: Int = INTERACTIVE_LOGIN_TIMEOUT_MS,
    askUser: ((LoginChallenge) -> String?)? = null
) {
    val knownMethods = userAuth.allowedMethods.filter { it.isNotBlank() }
    if (knownMethods.isEmpty() || "password" in knownMethods) {
        try {
            auth(username, AuthPassword(PasswordUtils.createOneOff(password.toCharArray())))
            return
        } catch (refused: UserAuthException) {
            val allowed = userAuth.allowedMethods
            if ("password" in allowed || "keyboard-interactive" !in allowed) throw refused
        }
    } else if ("keyboard-interactive" !in knownMethods) {
        throw UserAuthException("The server doesn't accept passwords (it allows: ${knownMethods.joinToString()})")
    }
    val responder = InteractiveLoginResponder(password, askUser)
    // sshj waits for the auth result with the transport timeout; a person typing a code needs longer.
    val previousTimeout = transport.timeoutMs
    transport.timeoutMs = maxOf(previousTimeout, interactiveTimeoutMs)
    try {
        auth(username, AuthKeyboardInteractive(responder))
    } catch (failure: UserAuthException) {
        if (responder.canceled) throw LoginCanceledException()
        throw failure
    } finally {
        transport.timeoutMs = previousTimeout
    }
}

const val INTERACTIVE_LOGIN_TIMEOUT_MS = 120_000
