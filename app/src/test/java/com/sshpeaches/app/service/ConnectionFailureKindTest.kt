package com.majordaftapps.sshpeaches.app.service

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFailureKindTest {

    @Test
    fun transportExceptionIsFatalForSftpSession() {
        assertTrue(
            TransportException(
                DisconnectReason.PROTOCOL_ERROR,
                "invalid packet length: 262172"
            ).isFatalSftpTransportFailure()
        )
    }

    @Test
    fun wrappedTransportExceptionIsFatalForSftpSession() {
        assertTrue(
            IllegalStateException(
                "SFTP transfer failed",
                TransportException(DisconnectReason.CONNECTION_LOST, "connection lost")
            ).isFatalSftpTransportFailure()
        )
    }

    @Test
    fun ordinarySftpOperationErrorIsNotFatalForSftpSession() {
        assertFalse(IllegalArgumentException("permission denied").isFatalSftpTransportFailure())
    }

    @Test
    fun classifiesSocketAndDnsFailuresBySpecificCause() {
        mapOf(
            UnknownHostException("unknown host") to ConnectionFailureKind.UNKNOWN_HOST,
            UnresolvedAddressException() to ConnectionFailureKind.UNKNOWN_HOST,
            ConnectException(
                "failed to connect to /10.0.0.5 (port 22): isConnected failed: ECONNREFUSED (Connection refused)"
            ) to ConnectionFailureKind.REFUSED,
            ConnectException(
                "failed to connect to /10.0.0.5 (port 22): isConnected failed: EHOSTUNREACH (No route to host)"
            ) to ConnectionFailureKind.UNREACHABLE,
            NoRouteToHostException("no route") to ConnectionFailureKind.UNREACHABLE,
            SocketTimeoutException("timed out") to ConnectionFailureKind.UNREACHABLE,
            SocketException("Software caused connection abort") to ConnectionFailureKind.NETWORK
        ).forEach { (failure, expected) ->
            assertEquals(failure.toString(), expected, failure.connectionFailureKind())
            assertTrue(failure.toString(), expected.isNetwork)
        }
    }

    @Test
    fun classifiesWrappedNetworkFailures() {
        val failure = RuntimeException(
            "SSH connection failed",
            RuntimeException("socket failed", ConnectException("connection refused"))
        )

        assertEquals(ConnectionFailureKind.REFUSED, failure.connectionFailureKind())
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

        assertEquals(ConnectionFailureKind.REFUSED, failure.connectionFailureKind())
    }

    @Test
    fun classifiesRejectedHostKeyBeforeItsSocketCause() {
        val failure = TransportException(
            DisconnectReason.HOST_KEY_NOT_VERIFIABLE,
            "Host key was not accepted",
            ConnectException("connection closed during verification")
        )

        assertEquals(ConnectionFailureKind.HOST_KEY_REJECTED, failure.connectionFailureKind())
        assertFalse(ConnectionFailureKind.HOST_KEY_REJECTED.isNetwork)
    }

    @Test
    fun usesTheKindAttachedWhereTheFailureWasThrown() {
        val keyFailure = ConnectionFailure(
            "Identity authentication failed.",
            ConnectionFailureKind.AUTH_KEY,
            UserAuthException("Exhausted available authentication methods")
        )

        assertEquals(ConnectionFailureKind.AUTH_KEY, RuntimeException("wrapped", keyFailure).connectionFailureKind())
        assertEquals(
            ConnectionFailureKind.AUTH,
            UserAuthException("Exhausted available authentication methods").connectionFailureKind()
        )
    }

    @Test
    fun leavesUnexplainedFailuresUnclassified() {
        assertNull(RuntimeException("Connection canceled while waiting for password.").connectionFailureKind())
        assertNull(
            TransportException(DisconnectReason.PROTOCOL_ERROR, "bad packet").connectionFailureKind()
        )
    }
}
