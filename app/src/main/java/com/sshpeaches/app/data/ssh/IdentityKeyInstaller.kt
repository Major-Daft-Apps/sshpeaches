package com.majordaftapps.sshpeaches.app.data.ssh

import android.content.Context
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException

object IdentityKeyInstaller {
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
        val publicKey = resolvePublicKey(identityId)
            ?: return@withContext Result(false, "Public key is missing for this identity.")

        val client = runCatching {
            SshClientProvider.createClient(
                context = context,
                host = host,
                autoTrustUnknownHostKey = true,
                onHostKeyPrompt = null
            )
        }.getOrElse { error ->
            return@withContext Result(false, error.message ?: "Unable to initialize SSH client.")
        }

        runCatching {
            client.connect(host.host, host.port)
            authenticate(
                client = client,
                host = host,
                identityId = identityId,
                hostPasswordOverride = hostPasswordOverride,
                identityPassphraseOverride = identityPassphraseOverride
            )
            if (!client.isAuthenticated) {
                return@runCatching Result(false, "Authentication failed for ${host.username}@${host.host}.")
            }
            val installCommand = buildAuthorizedKeyInstallCommand(publicKey)
            client.startSession().use { session ->
                val command = session.exec(installCommand)
                command.join(20, TimeUnit.SECONDS)
                val exitCode = command.exitStatus ?: 0
                if (exitCode != 0) {
                    val errorText = runCatching { command.errorStream.bufferedReader().readText().trim() }
                        .getOrDefault("Unable to install key on remote host.")
                    return@runCatching Result(false, errorText.ifBlank { "Unable to install key on remote host." })
                }
            }
            Result(true, "Key copied to ${host.username}@${host.host}.")
        }.getOrElse { error ->
            val message = when (error) {
                is UserAuthException -> "Authentication failed for ${host.username}@${host.host}."
                else -> error.message ?: "Failed to copy key to host."
            }
            Result(false, message)
        }.also {
            runCatching { client.disconnect() }
        }
    }

    private fun resolvePublicKey(identityId: String): String? {
        val storedPublic = runCatching { SecurityManager.getIdentityPublicKey(identityId) }.getOrNull()
            ?.lineSequence()
            ?.firstOrNull { it.trim().isNotBlank() }
            ?.trim()
        if (!storedPublic.isNullOrBlank()) return storedPublic

        val privateKey = runCatching { SecurityManager.getIdentityKey(identityId) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val derived = SshKeyGenerator.derivePublicKeyFromPrivate(privateKey)?.trim()
        if (!derived.isNullOrBlank()) {
            runCatching { SecurityManager.storeIdentityPublicKey(identityId, derived) }
            return derived
        }
        return null
    }

    private fun authenticate(
        client: SSHClient,
        host: HostConnection,
        identityId: String,
        hostPasswordOverride: String?,
        identityPassphraseOverride: String?
    ) {
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
            val tempFile = writeTempIdentityKey(host.id, privateKey)
            try {
                val keyProvider = if (passphrase.isNullOrBlank()) {
                    client.loadKeys(tempFile.absolutePath)
                } else {
                    client.loadKeys(tempFile.absolutePath, passphrase.toCharArray())
                }
                client.authPublickey(host.username, keyProvider)
                if (client.isAuthenticated) return
            } catch (error: Throwable) {
                lastError = error
            } finally {
                runCatching { tempFile.delete() }
            }
        }
        if (lastError != null) throw lastError!!
        error("Selected identity key is unavailable.")
    }

    private fun authenticateWithPassword(
        client: SSHClient,
        host: HostConnection,
        hostPasswordOverride: String?
    ) {
        val password = hostPasswordOverride?.takeIf { it.isNotBlank() }
            ?: SecurityManager.getHostPassword(host.id)
            ?: error("Password is required to authenticate with ${host.name}.")
        client.authPassword(host.username, password)
    }

    private fun writeTempIdentityKey(hostId: String, privateKey: String): File {
        val file = File.createTempFile("identity_copy_${hostId}_", ".pem")
        val normalized = privateKey.trim().let { if (it.endsWith("\n")) it else "$it\n" }
        file.writeText(normalized, Charsets.UTF_8)
        return file
    }

    private fun buildAuthorizedKeyInstallCommand(publicKey: String): String {
        val escaped = publicKey.replace("'", "'\"'\"'")
        return "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && (grep -qxF '$escaped' ~/.ssh/authorized_keys || echo '$escaped' >> ~/.ssh/authorized_keys) && chmod 600 ~/.ssh/authorized_keys"
    }
}
