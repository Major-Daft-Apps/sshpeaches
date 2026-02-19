package com.sshpeaches.app.data.ssh

import android.content.Context
import java.io.File
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import com.sshpeaches.app.data.model.HostConnection
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts

/**
 * Minimal SSHJ provider. Callers are responsible for threading/coroutine dispatch.
 * This keeps connection setup in one place; higher layers can add auth, port forwards, etc.
 */
object SshClientProvider {

    /**
     * Create a configured SSHClient for the given host.
     * Does not connect; caller must invoke connect() and auth.
     */
    fun createClient(
        context: Context,
        host: HostConnection,
        loggerFactory: LoggerFactory? = null
    ): SSHClient {
        val config = DefaultConfig()
        loggerFactory?.let { config.setLoggerFactory(it) }
        // Android's BC provider often lacks X25519; keep compatible KEX factories to avoid
        // handshake failure "no such algorithm: X25519 for provider BC".
        val compatibleKex = config.keyExchangeFactories.filterNot { factory ->
            factory.name.contains("curve25519", ignoreCase = true)
        }
        if (compatibleKex.isNotEmpty()) {
            config.keyExchangeFactories = compatibleKex
        }
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return SSHClient(config).apply {
            addHostKeyVerifier(OpenSSHKnownHosts(knownHostsFile))
            connectTimeout = 10_000
            timeout = 20_000
        }
    }
}
