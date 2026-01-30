package com.sshpeaches.app.data.ssh

import android.content.Context
import java.io.File
import net.schmizz.sshj.SSHClient
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
    fun createClient(context: Context, host: HostConnection): SSHClient {
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return SSHClient().apply {
            addHostKeyVerifier(OpenSSHKnownHosts(knownHostsFile))
            connectTimeout = 10_000
            timeout = 20_000
        }
    }
}
