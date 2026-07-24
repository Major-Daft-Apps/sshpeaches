package com.majordaftapps.sshpeaches.app.ui

import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRouteReadinessTest {

    @Test
    fun serviceNotReadyWithEmptySnapshotsGatesTerminalButDoesNotRedirect() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = false,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOSTS
        )

        assertNull(decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertFalse(decision.consumeRequestedOpenSessionId)

        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.SESSION, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadyWithMissingRequestedSessionRedirectsAndConsumesRequest() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "session-1",
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOSTS
        )

        assertEquals(Routes.HOSTS, decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertTrue(decision.consumeRequestedOpenSessionId)

        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.HOSTS, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadyWithMissingOpenSessionRequestRedirectsAndConsumesWithoutQuickConnectRequest() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "session-1",
            quickConnectRequestSessionId = null,
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.SETTINGS
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.SETTINGS, decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertTrue(decision.consumeRequestedOpenSessionId)
        assertEquals(Routes.SETTINGS, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadyWithMissingRequestedSessionConsumesStaleRequestOffSessionRoute() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.HOSTS,
            requestedOpenSessionId = "session-1",
            quickConnectRequestSessionId = null,
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOME
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.gateTerminalContent)
        assertTrue(decision.consumeRequestedOpenSessionId)
    }

    @Test
    fun serviceReadyWithMatchingLiveSnapshotPreservesTerminalRoute() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "session-1",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = true
                )
            ),
            routeBeforeConnecting = Routes.HOSTS
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.gateTerminalContent)
        assertFalse(decision.consumeRequestedOpenSessionId)

        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.SESSION, rendering.chromeRoute)
        assertTrue(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadyWithMatchingRequestedOpenSessionSnapshotPreservesTerminalRoute() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "session-1",
            quickConnectRequestSessionId = null,
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "session-1",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = true
                )
            ),
            routeBeforeConnecting = Routes.HOSTS
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.gateTerminalContent)
        assertFalse(decision.consumeRequestedOpenSessionId)
        assertEquals(Routes.SESSION, rendering.chromeRoute)
        assertTrue(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadyWithActiveSnapshotMissingTerminalEmulatorGatesTerminalContent() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "session-1",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = false
                )
            ),
            routeBeforeConnecting = Routes.HOSTS
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.HOSTS, decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertEquals(Routes.HOSTS, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun staleSessionRouteRenderingUsesRecoveryChromeBeforeCleanupNavigationCompletes() {
        val decision = StaleSessionRouteDecision(
            gateTerminalContent = true,
            consumeRequestedOpenSessionId = false,
            recoveryRoute = Routes.HOSTS
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.HOSTS, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun serviceReadySessionRouteWithNoRequestGatesTerminalContent() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.CONNECTING,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = null,
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOSTS
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.CONNECTING,
            staleSessionRouteDecision = decision
        )

        assertEquals(Routes.HOSTS, decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertEquals(Routes.HOSTS, rendering.chromeRoute)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun brandNewPendingConnectingRequestCanRenderUntilFirstSnapshotChancePasses() {
        val firstPassDecision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.CONNECTING,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOSTS
        )
        val firstPassRendering = decideSessionRouteRendering(
            currentRoute = Routes.CONNECTING,
            staleSessionRouteDecision = firstPassDecision
        )

        assertNull(firstPassDecision.recoveryRoute)
        assertFalse(firstPassDecision.gateTerminalContent)
        assertEquals(Routes.CONNECTING, firstPassRendering.chromeRoute)
        assertTrue(firstPassRendering.renderTerminalContent)

        val staleAfterSnapshotChanceDecision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.CONNECTING,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.HOSTS
        )
        val staleAfterSnapshotChanceRendering = decideSessionRouteRendering(
            currentRoute = Routes.CONNECTING,
            staleSessionRouteDecision = staleAfterSnapshotChanceDecision
        )

        assertEquals(Routes.HOSTS, staleAfterSnapshotChanceDecision.recoveryRoute)
        assertTrue(staleAfterSnapshotChanceDecision.gateTerminalContent)
        assertEquals(Routes.HOSTS, staleAfterSnapshotChanceRendering.chromeRoute)
        assertFalse(staleAfterSnapshotChanceRendering.renderTerminalContent)
    }

    @Test
    fun staleRouteBeforeConnectingValuesFallBackToHome() {
        val sessionRouteDecision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.CONNECTING
        )
        val invalidRouteDecision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = null,
            quickConnectRequestSessionId = "session-1",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = "not-a-real-route"
        )

        assertEquals(Routes.HOME, sessionRouteDecision.recoveryRoute)
        assertEquals(Routes.HOME, invalidRouteDecision.recoveryRoute)
    }

    @Test
    fun foregroundStaleOpenSessionRequestOnExistingNonSessionRouteOnlyConsumesRequest() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SETTINGS,
            requestedOpenSessionId = "session-1",
            quickConnectRequestSessionId = null,
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = false,
            sessionSnapshots = emptyList(),
            routeBeforeConnecting = Routes.SETTINGS
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.gateTerminalContent)
        assertTrue(decision.consumeRequestedOpenSessionId)
    }

    @Test
    fun explicitMissingNotificationSessionDoesNotFallBackToLastVisibleTerminal() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "disconnected-session",
            quickConnectRequestSessionId = "last-visible-session",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "last-visible-session",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = true
                )
            ),
            routeBeforeConnecting = Routes.HOME
        )

        assertEquals(Routes.HOME, decision.recoveryRoute)
        assertTrue(decision.gateTerminalContent)
        assertTrue(decision.consumeRequestedOpenSessionId)
    }

    @Test
    fun notificationTargetWithRuntimeBackingWaitsForSnapshotInsteadOfShowingLastTerminal() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "selected-session",
            quickConnectRequestSessionId = "last-visible-session",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "last-visible-session",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = true
                )
            ),
            runtimeSessionIds = setOf("last-visible-session", "selected-session"),
            routeBeforeConnecting = Routes.HOME
        )
        val rendering = decideSessionRouteRendering(
            currentRoute = Routes.SESSION,
            staleSessionRouteDecision = decision
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.consumeRequestedOpenSessionId)
        assertTrue(decision.gateTerminalContent)
        assertFalse(rendering.renderTerminalContent)
    }

    @Test
    fun connectingNotificationTargetWithSnapshotReplacesLastVisibleTerminal() {
        val decision = decideStaleSessionRoute(
            sessionServiceReady = true,
            currentRoute = Routes.SESSION,
            requestedOpenSessionId = "selected-session",
            quickConnectRequestSessionId = "last-visible-session",
            pendingConnectingNavigation = false,
            sawSnapshotForCurrentRequest = true,
            sessionSnapshots = listOf(
                SessionRouteSnapshot(
                    sessionId = "last-visible-session",
                    status = SessionRouteSnapshotStatus.ACTIVE,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = true
                ),
                SessionRouteSnapshot(
                    sessionId = "selected-session",
                    status = SessionRouteSnapshotStatus.CONNECTING,
                    requiresTerminalEmulator = true,
                    terminalEmulatorAvailable = false
                )
            ),
            runtimeSessionIds = setOf("last-visible-session", "selected-session"),
            routeBeforeConnecting = Routes.HOME
        )

        assertNull(decision.recoveryRoute)
        assertFalse(decision.consumeRequestedOpenSessionId)
        assertFalse(decision.gateTerminalContent)
    }
}
