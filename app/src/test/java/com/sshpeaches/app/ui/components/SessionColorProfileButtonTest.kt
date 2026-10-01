package com.majordaftapps.sshpeaches.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionColorProfileButtonTest {

    @Test
    fun overrideTakesPrecedenceOverRequestProfile() {
        val resolved = resolveSessionTerminalProfileId(
            sessionId = "session-1",
            requestProfileId = "host-profile",
            overrides = mapOf("session-1" to "session-profile")
        )
        assertEquals("session-profile", resolved)
    }

    @Test
    fun requestProfileIsUsedWhenSessionHasNoOverride() {
        val resolved = resolveSessionTerminalProfileId(
            sessionId = "session-1",
            requestProfileId = "host-profile",
            overrides = mapOf("session-2" to "other-profile")
        )
        assertEquals("host-profile", resolved)
    }

    @Test
    fun missingSessionFallsBackToRequestProfile() {
        val resolved = resolveSessionTerminalProfileId(
            sessionId = null,
            requestProfileId = "host-profile",
            overrides = mapOf("session-1" to "session-profile")
        )
        assertEquals("host-profile", resolved)
    }

    @Test
    fun missingEverythingReturnsNull() {
        assertNull(
            resolveSessionTerminalProfileId(
                sessionId = null,
                requestProfileId = null,
                overrides = emptyMap()
            )
        )
    }
}
