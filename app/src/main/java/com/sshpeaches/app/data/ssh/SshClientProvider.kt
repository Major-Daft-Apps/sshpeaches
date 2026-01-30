package com.sshpeaches.app.data.ssh

import com.hierynomus.sshj.SSHClient
import com.hierynomus.sshj.transport.verification.PromiscuousVerifier
import com.sshpeaches.app.data.model.HostConnection

/**
 * Minimal SSHJ provider. Callers are responsible for threading/coroutine dispatch.
 * This keeps connection setup in one place; higher layers can add auth, port forwards, etc.
 */
object SshClientProvider {

    /**
     * Create a configured SSHClient for the given host.
     * Does not connect; caller must invoke connect() and auth.
     */
    fun createClient(host: HostConnection): SSHClient {
        return SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier()) // TODO: replace with real verification
            connectTimeout = 10_000
            timeout = 20_000
        }
    }
}
