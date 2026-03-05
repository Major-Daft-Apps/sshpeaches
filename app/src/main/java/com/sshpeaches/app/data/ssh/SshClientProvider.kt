package com.majordaftapps.sshpeaches.app.data.ssh

import android.content.Context
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts

/**
 * Minimal SSHJ provider. Callers are responsible for threading/coroutine dispatch.
 */
object SshClientProvider {

    private val knownHostsWriteLock = Any()

    data class HostKeyPrompt(
        val host: String,
        val port: Int,
        val fingerprint: String,
        val keyChanged: Boolean
    )

    /**
     * Create a configured SSHClient for the given host.
     *
     * [autoTrustUnknownHostKey] controls unknown-host TOFU behavior.
     * Host-key changes always invoke [onHostKeyPrompt] when available.
     */
    fun createClient(
        context: Context,
        host: HostConnection,
        loggerFactory: LoggerFactory? = null,
        autoTrustUnknownHostKey: Boolean = true,
        onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)? = null
    ): SSHClient {
        val config = DefaultConfig()
        loggerFactory?.let { config.setLoggerFactory(it) }
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return SSHClient(config).apply {
            addHostKeyVerifier(
                InteractiveKnownHosts(
                    file = knownHostsFile,
                    host = host.host,
                    port = host.port,
                    autoTrustUnknownHostKey = autoTrustUnknownHostKey,
                    onHostKeyPrompt = onHostKeyPrompt
                )
            )
            connectTimeout = 10_000
            timeout = 20_000
        }
    }

    /**
     * Removes stored known-host entries for [host]:[port].
     * Returns true when the operation succeeds (including when no matching entry exists).
     */
    fun clearKnownHostEntry(
        context: Context,
        host: String,
        port: Int
    ): Boolean {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) return false
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) return true
        return runCatching {
            synchronized(knownHostsWriteLock) {
                val knownHosts = OpenSSHKnownHosts(knownHostsFile)
                val hostCandidates = buildSet {
                    add(normalizedHost)
                    add("[$normalizedHost]:$port")
                }
                val keyTypes = KeyType.values().filterNot { it == KeyType.UNKNOWN }
                val toRemove = knownHosts.entries().filter { entry ->
                    hostCandidates.any { candidate ->
                        keyTypes.any { keyType ->
                            runCatching { entry.appliesTo(keyType, candidate) }.getOrDefault(false)
                        }
                    }
                }
                if (toRemove.isNotEmpty()) {
                    knownHosts.entries().removeAll(toRemove.toSet())
                    knownHosts.write()
                }
            }
            true
        }.getOrElse { false }
    }

    private class InteractiveKnownHosts(
        file: File,
        private val host: String,
        private val port: Int,
        private val autoTrustUnknownHostKey: Boolean,
        private val onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)?
    ) : OpenSSHKnownHosts(file) {

        override fun hostKeyUnverifiableAction(hostname: String, key: PublicKey): Boolean {
            if (autoTrustUnknownHostKey) {
                return rememberAcceptedHostKey(hostname, key, replaceExisting = false)
            }
            val prompt = HostKeyPrompt(
                host = host,
                port = port,
                fingerprint = fingerprintSha256(key),
                keyChanged = false
            )
            val trusted = onHostKeyPrompt?.invoke(prompt) ?: false
            if (!trusted) return false
            return rememberAcceptedHostKey(hostname, key, replaceExisting = false)
        }

        // Always ask for changed keys when callback exists; never silently trust changed keys.
        override fun hostKeyChangedAction(hostname: String, key: PublicKey): Boolean {
            val prompt = HostKeyPrompt(
                host = host,
                port = port,
                fingerprint = fingerprintSha256(key),
                keyChanged = true
            )
            val trusted = onHostKeyPrompt?.invoke(prompt) ?: false
            if (!trusted) return false
            return rememberAcceptedHostKey(hostname, key, replaceExisting = true)
        }

        private fun rememberAcceptedHostKey(
            hostname: String,
            key: PublicKey,
            replaceExisting: Boolean
        ): Boolean {
            return runCatching {
                synchronized(knownHostsWriteLock) {
                    val keyType = KeyType.fromKey(key)
                    if (keyType == KeyType.UNKNOWN) return false
                    if (replaceExisting) {
                        entries().removeAll { entry ->
                            runCatching { entry.appliesTo(keyType, hostname) }.getOrDefault(false)
                        }
                    }
                    entries().add(OpenSSHKnownHosts.HostEntry(null, hostname, keyType, key))
                    if (replaceExisting) {
                        write()
                    } else {
                        write(entries().last())
                    }
                }
                true
            }.getOrElse { error ->
                log.warn("Failed to persist accepted host key for {}", hostname, error)
                true
            }
        }
    }

    private fun fingerprintSha256(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        val value = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$value"
    }
}
