package com.majordaftapps.sshpeaches.app.service

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionFailureKindTest {

    @Test
    fun classifiesSocketAndDnsFailuresAsNetworkErrors() {
        listOf(
            UnknownHostException("unknown host"),
            ConnectException("connection refused"),
            NoRouteToHostException("no route"),
            SocketTimeoutException("timed out"),
            UnresolvedAddressException()
        ).forEach { failure ->
            assertEquals(ConnectionFailureKind.NETWORK, failure.connectionFailureKind())
        }
    }

    @Test
    fun classifiesWrappedNetworkFailures() {
        val failure = RuntimeException(
            "SSH connection failed",
            RuntimeException("socket failed", ConnectException("connection refused"))
        )

        assertEquals(ConnectionFailureKind.NETWORK, failure.connectionFailureKind())
    }

    @Test
    fun classifiesSshConnectionLossAsANetworkError() {
        val failure = ConnectionException(DisconnectReason.CONNECTION_LOST, "connection lost")

        assertEquals(ConnectionFailureKind.NETWORK, failure.connectionFailureKind())
    }

    @Test
    fun followsUnknownSshFailureToItsNetworkCause() {
        val failure = TransportException(
            DisconnectReason.UNKNOWN,
            "SSH connection failed",
            ConnectException("connection refused")
        )

        assertEquals(ConnectionFailureKind.NETWORK, failure.connectionFailureKind())
    }

    @Test
    fun leavesExplicitNonNetworkFailuresUnclassified() {
        assertNull(RuntimeException("Authentication failed").connectionFailureKind())
        assertNull(
            TransportException(
                DisconnectReason.HOST_KEY_NOT_VERIFIABLE,
                "Host key was not accepted",
                ConnectException("connection closed during verification")
            ).connectionFailureKind()
        )
    }
}
