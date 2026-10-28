package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.sftp.SftpLeaseSource
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferLease
import java.io.IOException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient

/**
 * The connections a session's file transfers run on, opened next to the session's own connection so that browsing,
 * renaming and editing never wait behind file data, and a transfer can use several connections at once.
 *
 * Connections are opened on demand, up to [maxConnections], and kept for the next transfer; past [keepWarm] they close
 * after [idleTimeoutMs] unused. If the first one can't be opened (the server allows one login at a time, or asks
 * questions only a person can answer), transfers fall back to one SFTP channel on the session's own connection.
 */
internal class SftpTransferPool(
    private val maxConnections: () -> Int,
    private val openConnection: () -> SSHClient,
    private val sessionClient: () -> SSHClient?,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
    private val onFallback: (Throwable) -> Unit = {},
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val keepWarm: Int = 1,
    private val now: () -> Long = System::currentTimeMillis
) : SftpLeaseSource, AutoCloseable {

    private class Connection(val ssh: SSHClient, val sftp: SFTPClient) {
        var idleSince = 0L
        @Volatile var dropped = false
        val healthy: Boolean get() = !dropped && ssh.isConnected && ssh.isAuthenticated
    }

    private val lock = Object()
    private val idle = ArrayDeque<Connection>()
    private val all = mutableSetOf<Connection>()
    private var opening = 0
    private var limit = Int.MAX_VALUE
    private var everOpened = false
    private var useSessionConnection = false
    private var sessionLeaseOut = false
    private var closed = false

    /** Whether transfers have fallen back to the session's own connection. */
    val usingSessionConnection: Boolean get() = synchronized(lock) { useSessionConnection }

    override fun acquire(stop: () -> Boolean): SftpTransferLease? {
        while (true) {
            var openNew = false
            var lendSession = false
            synchronized(lock) {
                while (true) {
                    if (closed) throw IOException("The session has closed.")
                    if (stop()) return null
                    while (idle.isNotEmpty()) {
                        val connection = idle.removeFirst()
                        if (connection.healthy) return lease(connection)
                        all -= connection
                        closeQuietly(connection)
                    }
                    if (useSessionConnection) {
                        if (!sessionLeaseOut) {
                            sessionLeaseOut = true
                            lendSession = true
                            break
                        }
                    } else if (all.size + opening < minOf(maxConnections().coerceAtLeast(1), limit)) {
                        opening += 1
                        openNew = true
                        break
                    }
                    lock.wait(WAIT_SLICE_MS)
                }
            }
            if (lendSession) return lendSessionChannel()
            if (openNew) {
                val connection = try {
                    connect()
                } catch (error: Throwable) {
                    synchronized(lock) {
                        opening -= 1
                        when {
                            // Some servers allow only a few logins per user: make do with the ones that worked.
                            all.isNotEmpty() || opening > 0 -> limit = maxOf(1, all.size + opening)
                            // Every connection is gone (the network dropped): the next attempt may open them all.
                            everOpened -> {
                                limit = Int.MAX_VALUE
                                lock.notifyAll()
                                throw error
                            }
                            !everOpened -> {
                                useSessionConnection = true
                                onFallback(error)
                            }
                        }
                        lock.notifyAll()
                    }
                    continue
                }
                synchronized(lock) {
                    opening -= 1
                    if (closed) {
                        closeQuietly(connection)
                        throw IOException("The session has closed.")
                    }
                    everOpened = true
                    all += connection
                    return lease(connection)
                }
            }
        }
    }

    /**
     * Drops every connection, in use or idle: after the network changes they run over a route that is gone, and
     * would only be noticed as dead after the stall timeout. Transfers using them retry on new connections at once.
     * Also forgets a lowered connection limit and a fallback to the session's connection, which a flaky moment may
     * have caused.
     */
    fun dropAll() {
        val toClose = synchronized(lock) {
            limit = Int.MAX_VALUE
            if (!everOpened) useSessionConnection = false
            all.forEach { it.dropped = true }
            idle.clear()
            all.toList().also { all.clear() }
        }
        toClose.forEach(::closeQuietly)
    }

    /** Opens one connection in the background so the first transfer starts without waiting for a login. */
    fun warmUp() {
        synchronized(lock) {
            if (closed || useSessionConnection || all.isNotEmpty() || opening > 0) return
            opening += 1
        }
        val connection = runCatching { connect() }
        synchronized(lock) {
            opening -= 1
            connection.onSuccess {
                if (closed) {
                    closeQuietly(it)
                } else {
                    everOpened = true
                    all += it
                    it.idleSince = now()
                    idle.addLast(it)
                }
            }
            lock.notifyAll()
        }
        connection.onSuccess { scheduleIdleCheck() }
    }

    override fun close() {
        val toClose = synchronized(lock) {
            closed = true
            lock.notifyAll()
            all.toList().also {
                all.clear()
                idle.clear()
            }
        }
        toClose.forEach(::closeQuietly)
    }

    private fun connect(): Connection {
        val ssh = openConnection()
        return try {
            Connection(ssh, ssh.newSFTPClient())
        } catch (error: Throwable) {
            runCatching { ssh.disconnect() }
            throw error
        }
    }

    private fun lease(connection: Connection): SftpTransferLease = object : SftpTransferLease {
        private var returned = false
        override val sftp: SFTPClient = connection.sftp

        override fun release() = giveBack(discard = false)

        override fun discard() = giveBack(discard = true)

        private fun giveBack(discard: Boolean) {
            val dropped = synchronized(lock) {
                if (returned) return
                returned = true
                val drop = discard || closed || !connection.healthy
                if (drop) {
                    all -= connection
                } else {
                    connection.idleSince = now()
                    idle.addLast(connection)
                }
                lock.notifyAll()
                drop
            }
            if (dropped) closeQuietly(connection) else scheduleIdleCheck()
        }
    }

    private fun lendSessionChannel(): SftpTransferLease {
        val channel = try {
            sessionClient()?.newSFTPClient() ?: throw IOException("The session is not connected.")
        } catch (error: Throwable) {
            synchronized(lock) {
                sessionLeaseOut = false
                lock.notifyAll()
            }
            throw error
        }
        return object : SftpTransferLease {
            private var returned = false
            override val sftp: SFTPClient = channel
            override val shared: Boolean = true

            override fun release() = giveBack()

            override fun discard() = giveBack()

            private fun giveBack() {
                synchronized(lock) {
                    if (returned) return
                    returned = true
                    sessionLeaseOut = false
                    lock.notifyAll()
                }
                // Closing sends a message on the session's connection; if that connection is stuck, don't wait for it.
                Thread({ runCatching { channel.close() } }, "sftp-channel-close").start()
            }
        }
    }

    private fun scheduleIdleCheck() {
        schedule(idleTimeoutMs) { closeIdle() }
    }

    private fun closeIdle() {
        val toClose = synchronized(lock) {
            val cutoff = now() - idleTimeoutMs
            val keep = (keepWarm - (all.size - idle.size)).coerceAtLeast(0)
            val stale = idle.filter { it.idleSince <= cutoff }.drop(keep)
            idle.removeAll(stale.toSet())
            all.removeAll(stale.toSet())
            stale
        }
        toClose.forEach(::closeQuietly)
    }

    /**
     * Closes the socket first: on a network that died, a write blocks once the send buffer is full, and sshj's
     * keepalive and disconnect then wait behind it forever. With the socket closed they fail at once.
     */
    private fun closeQuietly(connection: Connection) {
        runCatching { connection.ssh.socket?.close() }
        runCatching { connection.sftp.close() }
        runCatching { connection.ssh.disconnect() }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 60_000L
        private const val WAIT_SLICE_MS = 100L
    }
}
