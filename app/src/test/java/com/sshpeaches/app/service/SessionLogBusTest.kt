package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.service.SessionLogBus.LogLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogBusTest {

    @Test
    fun debugLogsRequireSessionDiagnostics() {
        assertFalse(
            shouldPublishSshLog(
                level = LogLevel.DEBUG,
                messageFormat = "Key exchange complete",
                diagnosticsEnabled = false
            )
        )
        assertTrue(
            shouldPublishSshLog(
                level = LogLevel.DEBUG,
                messageFormat = "Key exchange complete",
                diagnosticsEnabled = true
            )
        )
    }

    @Test
    fun highFrequencyTransportLogsAreSuppressedWithDiagnosticsEnabled() {
        listOf(
            "Received packet {}",
            "Received packet #{}: {}",
            "Consuming by {} down to {}",
            "Increasing by {} up to {}",
            "{} Sending after interval [{} seconds]"
        ).forEach { message ->
            assertFalse(
                message,
                shouldPublishSshLog(
                    level = LogLevel.DEBUG,
                    messageFormat = message,
                    diagnosticsEnabled = true
                )
            )
        }
    }

    @Test
    fun userRelevantLevelsAreNeverSuppressed() {
        listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR).forEach { level ->
            assertTrue(
                shouldPublishSshLog(
                    level = level,
                    messageFormat = "Received packet CHANNEL_DATA",
                    diagnosticsEnabled = false
                )
            )
        }
    }
}
