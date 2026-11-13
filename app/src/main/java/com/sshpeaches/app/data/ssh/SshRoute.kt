package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import java.io.IOException
import net.schmizz.sshj.Config
import net.schmizz.sshj.SSHClient

/**
 * An SSH client that may be connected through other hosts (jump hosts, OpenSSH's ProxyJump). The connections it
 * rides on are its [carriers], first hop first; they are closed with it, so every place that closes a client
 * closes the whole route.
 */
class RoutedSSHClient(config: Config) : SSHClient(config) {
    @Volatile
    var carriers: List<SSHClient> = emptyList()
        internal set

    /** The agent this connection offers its server, when the host has agent forwarding on; let go on disconnect. */
    @Volatile
    var forwardedAgent: ForwardedAgent? = null

    /** Whether this connection goes through at least one jump host. */
    val viaJumpHost: Boolean get() = carriers.isNotEmpty()

    override fun disconnect() {
        try {
            forwardedAgent?.detach()
            forwardedAgent = null
            super.disconnect()
        } finally {
            closeCarriers()
        }
    }

    /**
     * Cuts the connection without a goodbye, for a link known to be dead: closes this client's socket, or the
     * first hop's when it has none of its own.
     */
    fun cut() {
        runCatching { socket?.close() }
        carriers.forEach { hop -> runCatching { hop.socket?.close() } }
    }

    private fun closeCarriers() {
        val hops = carriers
        carriers = emptyList()
        hops.asReversed().forEach { hop -> runCatching { hop.disconnect() } }
    }
}

/** The route to a host can't be worked out or opened; the message is for the user. */
class SshRouteException(message: String, cause: Throwable? = null) : IOException(message, cause)

object SshRoute {
    /** Jump hosts in a row, as many as anyone chains in practice; also what stops a loop that slipped through. */
    const val MAX_JUMPS = 5

    /**
     * The jump hosts [host] is reached through, first hop first; empty for a direct connection. [find] returns a
     * saved host by id. Throws [SshRouteException] for a missing host, a loop or a chain that is too long.
     */
    fun jumpsFor(host: HostConnection, find: (String) -> HostConnection?): List<HostConnection> {
        val jumps = ArrayList<HostConnection>()
        val seen = HashSet<String>().apply { add(host.id) }
        var next = host.jumpHostId?.takeIf { it.isNotBlank() }
        while (next != null) {
            if (!seen.add(next)) {
                throw SshRouteException("${host.name}'s jump hosts lead back to a host already on the way. Change the jump host of one of them.")
            }
            val jump = find(next)
                ?: throw SshRouteException("The jump host for ${jumps.lastOrNull()?.name ?: host.name} is no longer saved. Choose another in the host's settings.")
            jumps += jump
            if (jumps.size > MAX_JUMPS) {
                throw SshRouteException("${host.name} is reached through more than $MAX_JUMPS jump hosts.")
            }
            next = jump.jumpHostId?.takeIf { it.isNotBlank() }
        }
        return jumps.asReversed().toList()
    }

    /** Whether choosing [candidate] as [host]'s jump host would make a loop. */
    fun wouldLoop(host: HostConnection, candidate: HostConnection, find: (String) -> HostConnection?): Boolean {
        if (candidate.id == host.id) return true
        return runCatching { jumpsFor(candidate, find) }.getOrNull()?.any { it.id == host.id } ?: true
    }

    /**
     * Connects [client] to [host], directly or through [jumps] (first hop first). Each jump host gets its own
     * client from [newClient] (so its host key is checked under its own name) and is logged in by [logIn], which
     * throws when it can't. On failure every hop opened so far is closed.
     */
    fun connect(
        client: SSHClient,
        host: HostConnection,
        jumps: List<HostConnection> = emptyList(),
        newClient: (HostConnection) -> SSHClient = { throw SshRouteException("No way to open a jump host from here.") },
        logIn: (SSHClient, HostConnection) -> Unit = { _, _ -> }
    ) {
        if (jumps.isEmpty()) {
            client.connect(host.host, host.port)
            return
        }
        val routed = client as? RoutedSSHClient
            ?: throw SshRouteException("This connection can't go through a jump host.")
        val hops = ArrayList<SSHClient>()
        try {
            var previous: SSHClient? = null
            for (jump in jumps) {
                val hop = newClient(jump)
                hops += hop
                val before = previous
                try {
                    if (before == null) hop.connect(jump.host, jump.port)
                    else hop.connectVia(before.newDirectConnection(jump.host, jump.port))
                } catch (error: Exception) {
                    throw SshRouteException("Couldn't reach the jump host ${jump.name}: ${error.message ?: error.javaClass.simpleName}", error)
                }
                logIn(hop, jump)
                if (!hop.isAuthenticated) throw SshRouteException("The jump host ${jump.name} refused the login.")
                previous = hop
            }
            val last = previous!!
            val tunnel = try {
                last.newDirectConnection(host.host, host.port)
            } catch (error: Exception) {
                throw SshRouteException(
                    "The jump host ${jumps.last().name} couldn't open a connection to ${host.host}:${host.port}: " +
                        (error.message ?: error.javaClass.simpleName),
                    error
                )
            }
            routed.connectVia(tunnel)
            routed.carriers = hops
        } catch (error: Throwable) {
            hops.asReversed().forEach { hop -> runCatching { hop.disconnect() } }
            throw error
        }
    }

    /**
     * Logs in to the jump host [jump] with what is saved for it (its key, then its password), asking nobody: for
     * connections made behind the scenes. Throws [SshRouteException] when that isn't enough.
     */
    fun logInWithSavedCredentials(hop: SSHClient, jump: HostConnection) {
        fun withKey() {
            val identityId = jump.preferredIdentityId?.takeIf { it.isNotBlank() } ?: return
            val privateKey = runCatching { SecurityManager.getIdentityKey(identityId) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
            val passphrase = runCatching { SecurityManager.getIdentityKeyPassphrase(identityId) }.getOrNull()
            val publicKey = runCatching { SecurityManager.getIdentityPublicKey(identityId) }.getOrNull()
            runCatching {
                val provider = Ed25519IdentityKeyProvider.load(hop, privateKey, publicKey, passphrase)
                    ?: hop.loadPrivateKeyFromMemory(privateKey, passphrase)
                hop.authPublickey(jump.username, provider)
            }
        }
        fun withPassword() {
            val password = runCatching { SecurityManager.getHostPassword(jump.id) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
            runCatching { hop.authPasswordOrInteractive(jump.username, password) }
        }
        if (jump.preferredAuth != AuthMethod.PASSWORD) withKey()
        if (!hop.isAuthenticated && jump.preferredAuth != AuthMethod.IDENTITY) withPassword()
        if (!hop.isAuthenticated) {
            throw SshRouteException("The jump host ${jump.name} needs a saved password or key for this. Open a session through it once, or save one.")
        }
    }

    /**
     * Connects [client] to [host] through a jump connection that is already open and stays someone else's (the
     * session's last hop): for extra connections to the same host, which must not ask the user anything.
     */
    fun connectThrough(client: SSHClient, host: HostConnection, lastHop: SSHClient?) {
        if (lastHop == null) client.connect(host.host, host.port)
        else client.connectVia(lastHop.newDirectConnection(host.host, host.port))
    }
}
