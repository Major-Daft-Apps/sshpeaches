package com.majordaftapps.sshpeaches.app.service

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException

/** Why a connection failed, so the failure screen can say what to try. */
enum class ConnectionFailureKind(val isNetwork: Boolean = false) {
    /** The host name didn't resolve. */
    UNKNOWN_HOST(isNetwork = true),
    /** The server answered but nothing accepts connections on the port. */
    REFUSED(isNetwork = true),
    /** No answer: timeout, no route, or the phone is offline. */
    UNREACHABLE(isNetwork = true),
    /** An established connection dropped. */
    NETWORK(isNetwork = true),
    /** The server's host key was not trusted, so the connection stopped before login. */
    HOST_KEY_REJECTED,
    /** The server rejected the password. */
    AUTH_PASSWORD,
    /** The server rejected the identity key. */
    AUTH_KEY,
    /** The host logs in with a key, but none is selected or it is missing. */
    KEY_MISSING,
    /** Login was rejected and the method is unknown. */
    AUTH,
    /** SSH worked but mosh-server could not be started. */
    MOSH_SERVER
}

/** A failure whose kind is known where it is thrown. */
internal class ConnectionFailure(
    message: String,
    val kind: ConnectionFailureKind,
    cause: Throwable? = null
) : RuntimeException(message, cause)

internal fun Throwable.connectionFailureKind(): ConnectionFailureKind? {
    var current: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val cause = current ?: return null
        if (cause is ConnectionFailure) return cause.kind
        if (cause is UserAuthException) return ConnectionFailureKind.AUTH
        if (cause is SSHException) {
            when (cause.disconnectReason) {
                DisconnectReason.CONNECTION_LOST -> return ConnectionFailureKind.NETWORK
                DisconnectReason.HOST_KEY_NOT_VERIFIABLE -> return ConnectionFailureKind.HOST_KEY_REJECTED
                DisconnectReason.UNKNOWN -> Unit
                else -> return null
            }
        }
        cause.networkFailureKind()?.let { return it }
        current = cause.cause
    }
    return null
}

/**
 * A transport failure means SSHJ can no longer safely use the underlying SSH
 * connection. Retrying an SFTP operation on that connection can corrupt the
 * protocol stream, so the SFTP session must be closed instead.
 */
internal fun Throwable.isFatalSftpTransportFailure(): Boolean {
    var current: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val cause = current ?: return false
        if (cause is TransportException) return true
        current = cause.cause
    }
    return false
}

private fun Throwable.networkFailureKind(): ConnectionFailureKind? = when (this) {
    is UnknownHostException,
    is UnresolvedAddressException -> ConnectionFailureKind.UNKNOWN_HOST
    // Android reports ECONNREFUSED in the message, e.g. "isConnected failed: ECONNREFUSED (Connection refused)".
    is ConnectException ->
        if (message.orEmpty().contains("refused", ignoreCase = true)) {
            ConnectionFailureKind.REFUSED
        } else {
            ConnectionFailureKind.UNREACHABLE
        }
    is NoRouteToHostException,
    is SocketTimeoutException -> ConnectionFailureKind.UNREACHABLE
    is SocketException -> ConnectionFailureKind.NETWORK
    else -> null
}

private const val MAX_CAUSE_DEPTH = 16
