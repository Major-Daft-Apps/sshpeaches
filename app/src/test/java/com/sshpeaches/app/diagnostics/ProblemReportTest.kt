package com.majordaftapps.sshpeaches.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportTest {

    @Test
    fun everyFieldIsScrubbedAndTheLogIsCapped() {
        val log = List(ProblemReport.MAX_LOG_LINES + 50) { "line $it: connecting to 192.168.1.20 as alice" } +
            "Authentication failed for alice@homeserver.lan"
        val report = ProblemReport.create(
            summary = "Connection failed: Can't reach homeserver",
            details = mapOf("Message" to "failed to connect to /192.168.1.20 (port 22)", "Mode" to "SSH"),
            log = log,
            note = "it worked yesterday from 10.0.0.9",
            secrets = listOf("homeserver", "alice")
        )

        val all = listOf(report.summary, report.note) + report.details.values + report.logLines
        listOf("192.168.1.20", "alice", "homeserver", "10.0.0.9").forEach { secret ->
            assertFalse("leaked $secret", all.any { it.contains(secret) })
        }
        assertEquals(ProblemReport.MAX_LOG_LINES, report.logLines.size)
        assertTrue(report.logLines.last().startsWith("Authentication failed for"))
        assertEquals("SSH", report.details["Mode"])
    }
}
