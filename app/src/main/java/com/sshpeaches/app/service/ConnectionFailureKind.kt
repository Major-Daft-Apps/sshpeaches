package com.majordaftapps.sshpeaches.app.service

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException

enum class ConnectionFailureKind {
    NETWORK
}

internal fun Throwable.connectionFailureKind(): ConnectionFailureKind? {
    var current: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val cause = current ?: return null
        if (cause is SSHException) {
            when (cause.disconnectReason) {
                DisconnectReason.CONNECTION_LOST -> return ConnectionFailureKind.NETWORK
                DisconnectReason.UNKNOWN -> Unit
                else -> return null
            }
        }
        if (cause.isNetworkFailure()) {
            return ConnectionFailureKind.NETWORK
        }
        current = cause.cause
    }
    return null
}

private fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is UnknownHostException,
    is SocketTimeoutException,
    is SocketException,
    is UnresolvedAddressException -> true
    else -> false
}

private const val MAX_CAUSE_DEPTH = 16
