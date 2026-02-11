package com.sshpeaches.app.data.ssh

import android.content.Context
import java.io.File
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import com.sshpeaches.app.data.model.HostConnection
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts

import android.util.Log

private const val TAG = "CW/SshClientProvider"

/**
 * Minimal SSHJ provider. Callers are responsible for threading/coroutine dispatch.
 */
object SshClientProvider {

    /**
     * Create a configured SSHClient for the given host.
     * Does not connect; caller must invoke connect() and auth.
     */
    fun createClient(context: Context, host: HostConnection): SSHClient {
        Log.i(TAG, "SSH create_client host=${host.host}:${host.port}")
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return SSHClient().apply {
            addHostKeyVerifier(OpenSSHKnownHosts(knownHostsFile))
            connectTimeout = 10_000
            timeout = 20_000
        }
    }
}
