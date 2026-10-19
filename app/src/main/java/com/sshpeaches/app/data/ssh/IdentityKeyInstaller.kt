package com.majordaftapps.sshpeaches.app.data.ssh

import android.content.Context
import android.util.Log
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException

object IdentityKeyInstaller {
    private const val TAG = "IdentityKeyInstaller"

    data class Result(
        val success: Boolean,
        val message: String
    )

    suspend fun install(
        context: Context,
        host: HostConnection,
        identityId: String,
        hostPasswordOverride: String?,
        identityPassphraseOverride: String?
    ): Result = withContext(Dispatchers.IO) {
        debug(
            "START hostId=${host.id} identityId=${identityId.safeId()} auth=${host.preferredAuth} " +
                "hasPasswordOverride=${!hostPasswordOverride.isNullOrBlank()} hasStoredPassword=${host.hasPassword} " +
                "hasIdentityPassphraseOverride=${!identityPassphraseOverride.isNullOrBlank()} " +
                "preferredIdentitySet=${!host.preferredIdentityId.isNullOrBlank()}"
        )
        val publicKey = resolvePublicKey(identityId, identityPassphraseOverride)
            ?: return@withContext Result(false, "Public key is missing for this identity.").also {
                debug("FAIL hostId=${host.id} reason=missing-public-key identityId=${identityId.safeId()}")
            }
        debug(
            "PUBLIC_KEY_READY hostId=${host.id} identityId=${identityId.safeId()} " +
                "algorithm=${publicKey.substringBefore(' ', missingDelimiterValue = "unknown")}"
        )

        val client = runCatching {
            SshClientProvider.createClient(
                context = context,
                host = host,
                autoTrustUnknownHostKey = false,
                onHostKeyPrompt = null
            )
        }.getOrElse { error ->
            debug("FAIL hostId=${host.id} phase=create-client error=${error.safeSummary(host)}")
            return@withContext Result(false, error.message ?: "Unable to initialize SSH client.")
        }

        runCatching {
            debug("CONNECT_START hostId=${host.id} port=${host.port}")
            client.connect(host.host, host.port)
            debug("CONNECT_OK hostId=${host.id}")
            authenticate(
                client = client,
                host = host,
                identityId = identityId,
                hostPasswordOverride = hostPasswordOverride,
                identityPassphraseOverride = identityPassphraseOverride
            )
            if (!client.isAuthenticated) {
                debug("FAIL hostId=${host.id} phase=auth reason=not-authenticated")
                return@runCatching Result(false, "Authentication failed for ${host.username}@${host.host}.")
            }
            debug("AUTH_OK hostId=${host.id}")
            val installCommand = buildAuthorizedKeyInstallCommand(publicKey)
            debug("INSTALL_COMMAND_START hostId=${host.id}")
            client.startSession().use { session ->
                val command = session.exec(installCommand)
                command.join(20, TimeUnit.SECONDS)
                val exitCode = command.exitStatus ?: 0
                if (exitCode != 0) {
                    val errorText = runCatching { command.errorStream.bufferedReader().readText().trim() }
                        .getOrDefault("Unable to install key on remote host.")
                    debug(
                        "FAIL hostId=${host.id} phase=install-command exitCode=$exitCode " +
                            "remoteError=${errorText.sanitizeForLog(host).replace(publicKey, "<public-key>")}"
                    )
                    return@runCatching Result(false, errorText.ifBlank { "Unable to install key on remote host." })
                }
            }
            debug("SUCCESS hostId=${host.id}")
            Result(true, "Key copied to ${host.username}@${host.host}.")
        }.getOrElse { error ->
            val message = when (error) {
                is UserAuthException -> "Authentication failed for ${host.username}@${host.host}."
                else -> (
                    error.message
                        ?.takeIf {
                        it.contains("verification", ignoreCase = true) ||
                            it.contains("known host", ignoreCase = true) ||
                            it.contains("host key", ignoreCase = true)
                        }
                        ?.let {
                            "Host key is not trusted for ${host.username}@${host.host}. Verify the server key in a normal session first, then retry."
                        }
                        ?: error.message
                        ?: "Failed to copy key to host."
                    )
            }
            debug("FAIL hostId=${host.id} phase=exception error=${error.safeSummary(host)} result=${message.sanitizeForLog(host)}")
            Result(false, message)
        }.also {
            runCatching { client.disconnect() }
        }
    }

    private fun resolvePublicKey(identityId: String, identityPassphraseOverride: String?): String? {
        // The key installed must be the one the app logs in with, so derive it from the private key when there is
        // one. A stored public key is only a fallback (e.g. an encrypted key without its passphrase): it can come
        // from an import and needn't match.
        val privateKey = runCatching { SecurityManager.getIdentityKey(identityId) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        if (privateKey != null) {
            val passphrase = identityPassphraseOverride?.takeIf { it.isNotBlank() }
                ?: runCatching { SecurityManager.getIdentityKeyPassphrase(identityId) }.getOrNull()
            val derived = SshKeyGenerator.derivePublicKeyFromPrivate(
                privateKeyMaterial = privateKey,
                passphrase = passphrase
            )?.trim()
            if (!derived.isNullOrBlank()) {
                runCatching { SecurityManager.storeIdentityPublicKey(identityId, derived) }
                return derived
            }
        }
        return runCatching { SecurityManager.getIdentityPublicKey(identityId) }.getOrNull()
            ?.lineSequence()
            ?.firstOrNull { it.trim().isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun authenticate(
        client: SSHClient,
        host: HostConnection,
        identityId: String,
        hostPasswordOverride: String?,
        identityPassphraseOverride: String?
    ) {
        debug(
            "AUTH_START hostId=${host.id} method=${host.preferredAuth} " +
                "hasPasswordOverride=${!hostPasswordOverride.isNullOrBlank()} hasStoredPassword=${host.hasPassword}"
        )
        when (host.preferredAuth) {
            AuthMethod.IDENTITY -> authenticateWithIdentity(client, host, identityId, identityPassphraseOverride)
            AuthMethod.PASSWORD -> authenticateWithPassword(client, host, hostPasswordOverride)
            AuthMethod.PASSWORD_AND_IDENTITY -> {
                runCatching {
                    authenticateWithIdentity(client, host, identityId, identityPassphraseOverride)
                }
                if (!client.isAuthenticated) {
                    authenticateWithPassword(client, host, hostPasswordOverride)
                }
            }
        }
    }

    private fun authenticateWithIdentity(
        client: SSHClient,
        host: HostConnection,
        identityId: String,
        identityPassphraseOverride: String?
    ) {
        val candidates = listOf(identityId, host.preferredIdentityId.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
        var lastError: Throwable? = null
        candidates.forEach { authIdentityId ->
            val privateKey = SecurityManager.getIdentityKey(authIdentityId) ?: return@forEach
            val passphrase = identityPassphraseOverride?.takeIf { it.isNotBlank() }
                ?: runCatching { SecurityManager.getIdentityKeyPassphrase(authIdentityId) }.getOrNull()
            val publicKey = runCatching { SecurityManager.getIdentityPublicKey(authIdentityId) }.getOrNull()
            debug(
                "AUTH_IDENTITY_ATTEMPT hostId=${host.id} identityId=${authIdentityId.safeId()} " +
                    "hasPassphrase=${!passphrase.isNullOrBlank()}"
            )
            try {
                val keyProvider = Ed25519IdentityKeyProvider.load(
                    client = client,
                    privateKeyMaterial = privateKey,
                    publicKeyMaterial = publicKey,
                    passphrase = passphrase
                ) ?: client.loadPrivateKeyFromMemory(privateKey, passphrase)
                client.authPublickey(host.username, keyProvider)
                if (client.isAuthenticated) return
            } catch (error: Throwable) {
                lastError = error
                debug("AUTH_IDENTITY_ERROR hostId=${host.id} identityId=${authIdentityId.safeId()} error=${error.safeSummary(host)}")
            }
        }
        debug("AUTH_IDENTITY_FAIL hostId=${host.id} hadError=${lastError != null}")
        if (lastError != null) throw lastError!!
        error("Selected identity key is unavailable.")
    }

    private fun authenticateWithPassword(
        client: SSHClient,
        host: HostConnection,
        hostPasswordOverride: String?
    ) {
        debug(
            "AUTH_PASSWORD_ATTEMPT hostId=${host.id} hasPasswordOverride=${!hostPasswordOverride.isNullOrBlank()} " +
                "hasStoredPassword=${host.hasPassword}"
        )
        val password = hostPasswordOverride?.takeIf { it.isNotBlank() }
            ?: SecurityManager.getHostPassword(host.id)
            ?: error("Password is required to authenticate with ${host.name}.")
        client.authPasswordOrInteractive(host.username, password)
    }

    private fun buildAuthorizedKeyInstallCommand(publicKey: String): String {
        val escaped = publicKey.replace("'", "'\"'\"'")
        return "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && (grep -qxF '$escaped' ~/.ssh/authorized_keys || echo '$escaped' >> ~/.ssh/authorized_keys) && chmod 600 ~/.ssh/authorized_keys"
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private fun String.safeId(): String = take(8)

    private fun Throwable.safeSummary(host: HostConnection): String {
        return generateSequence(this) { it.cause }
            .take(4)
            .joinToString(" <- ") { error ->
                val location = error.stackTrace.firstOrNull()?.let { frame ->
                    "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
                }
                val messagePart = error.message?.sanitizeForLog(host)?.takeIf { it.isNotBlank() }
                listOfNotNull(error.javaClass.name, messagePart, location?.let { "at $it" }).joinToString(" | ")
            }
        }

    private fun String.sanitizeForLog(host: HostConnection): String {
        var sanitized = this
        listOf(host.host to "<host>", host.username to "<user>", host.name to "<host-name>")
            .filter { (value, _) -> value.isNotBlank() }
            .forEach { (value, replacement) -> sanitized = sanitized.replace(value, replacement) }
        return sanitized
            .replace(Regex("(?i)(password|passphrase|secret)\\s*[:=]\\s*\\S+"), "\$1=<redacted>")
            .replace(Regex("-----BEGIN [^-]+-----[\\s\\S]*?-----END [^-]+-----"), "<private-key>")
    }
}
