package com.majordaftapps.sshpeaches.app.telemetry

import java.net.ConnectException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySanitizerTest {

    @Test
    fun removesAddressesHostsUsersPathsAndUrls() {
        val text = "failed to connect to /10.0.2.2 (port 56321) from /192.168.1.7 (port 40122) after 10000ms: " +
            "isConnected failed: ECONNREFUSED (Connection refused); alice@web.example.com " +
            "opened /home/alice/projects/api/.env and ~/notes.md via https://example.com/x?y=1 " +
            "on nas.local and fe80::1ff:fe23:4567:890a, key SHA256:AgJLb+ozVg2H/EIUiACI0uZ/6p3zhGRlr7YLk1i5uNw"
        val scrubbed = TelemetrySanitizer.scrub(text)

        listOf("10.0.2.2", "192.168.1.7", "alice", "example.com", "/home", ".env", "notes.md", "nas.local", "fe80", "AgJLb").forEach {
            assertFalse("leaked $it in: $scrubbed", scrubbed.contains(it))
        }
        assertTrue(scrubbed.contains("ECONNREFUSED (Connection refused)"))
        assertTrue(scrubbed.contains("port 56321"))
        assertTrue(scrubbed.contains("after 10000ms"))
    }

    @Test
    fun removesNamedSecretsSuchAsTheSessionsHostAndUser() {
        val scrubbed = TelemetrySanitizer.scrub("Authentication failed for tester on homeserver", listOf("tester", "homeserver"))
        assertEquals("Authentication failed for <redacted> on <redacted>", scrubbed)
    }

    @Test
    fun keepsTimesOfDayAndPlainErrorText() {
        assertEquals("Timed out at 12:34:56 waiting for input", TelemetrySanitizer.scrub("Timed out at 12:34:56 waiting for input"))
        assertEquals(
            "PBKDF2WithHmacSHA256 SecretKeyFactory not available",
            TelemetrySanitizer.scrub("PBKDF2WithHmacSHA256 SecretKeyFactory not available")
        )
    }

    @Test
    fun sanitizedThrowablesKeepTypesAndStackFramesButNotDetails() {
        val original = IllegalStateException(
            "SSH connection failed",
            ConnectException("failed to connect to /10.0.0.5 (port 22): isConnected failed: EHOSTUNREACH")
        )
        val reported = TelemetrySanitizer.sanitize(original)

        assertEquals("java.lang.IllegalStateException: SSH connection failed", reported.message)
        assertArrayEquals(original.stackTrace, reported.stackTrace)
        val cause = reported.cause!!
        assertTrue(cause.message, cause.message!!.startsWith("java.net.ConnectException: failed to connect to /<ip> (port 22)"))
        assertFalse(cause.message!!.contains("10.0.0.5"))
        assertArrayEquals(original.cause!!.stackTrace, cause.stackTrace)
    }
}
