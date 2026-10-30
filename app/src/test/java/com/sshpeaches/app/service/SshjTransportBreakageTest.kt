package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshjTransportBreakageTest {
    private fun thrownAt(className: String) = ArrayIndexOutOfBoundsException("length=256; index=-5").apply {
        stackTrace = arrayOf(
            StackTraceElement(className, "encode", "Encoder.java", 120),
            StackTraceElement("net.schmizz.sshj.sftp.SFTPEngine", "request", "SFTPEngine.java", 157)
        )
    }

    @Test
    fun aFailureInsideSshjsTransportIsABrokenConnection() {
        val error = thrownAt("net.schmizz.sshj.transport.Encoder")
        assertTrue(error.isSshjTransportBreakage())
        assertEquals(ConnectionFailureKind.NETWORK, error.connectionFailureKind())
        assertTrue(java.io.IOException("listing failed", error).isFatalSftpTransportFailure())
    }

    @Test
    fun theAppsOwnBugsStayBugs() {
        val error = thrownAt("com.majordaftapps.sshpeaches.app.service.SessionService")
        assertFalse(error.isSshjTransportBreakage())
        assertNull(error.connectionFailureKind())
    }
}
