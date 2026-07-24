package com.majordaftapps.sshpeaches.app.ui

import com.majordaftapps.sshpeaches.app.ui.navigation.Routes

internal enum class SessionRouteSnapshotStatus {
    CONNECTING,
    ACTIVE,
    ERROR
}

internal data class SessionRouteSnapshot(
    val sessionId: String,
    val status: SessionRouteSnapshotStatus,
    val requiresTerminalEmulator: Boolean,
    val terminalEmulatorAvailable: Boolean
)

internal data class StaleSessionRouteDecision(
    val gateTerminalContent: Boolean,
    val consumeRequestedOpenSessionId: Boolean,
    val recoveryRoute: String?
)

internal data class SessionRouteRenderDecision(
    val chromeRoute: String,
    val renderTerminalContent: Boolean
)

internal fun decideStaleSessionRoute(
    sessionServiceReady: Boolean,
    currentRoute: String,
    requestedOpenSessionId: String?,
    quickConnectRequestSessionId: String?,
    pendingConnectingNavigation: Boolean,
    sawSnapshotForCurrentRequest: Boolean,
    sessionSnapshots: List<SessionRouteSnapshot>,
    runtimeSessionIds: Set<String> = emptySet(),
    routeBeforeConnecting: String
): StaleSessionRouteDecision {
    val isSessionVerticalRoute = currentRoute == Routes.CONNECTING || currentRoute == Routes.SESSION
    val snapshotsBySessionId = sessionSnapshots.associateBy { it.sessionId }
    val requestedOpenSessionSnapshot = requestedOpenSessionId?.let(snapshotsBySessionId::get)
    val requestedOpenSessionIsStale = sessionServiceReady &&
        requestedOpenSessionId != null &&
        when (requestedOpenSessionSnapshot) {
            null -> requestedOpenSessionId !in runtimeSessionIds
            else -> !requestedOpenSessionSnapshot.hasUsableBackingSession()
        }
    val requestedOpenSessionIsAwaitingSnapshot = sessionServiceReady &&
        requestedOpenSessionId != null &&
        requestedOpenSessionSnapshot == null &&
        requestedOpenSessionId in runtimeSessionIds
    // An explicit notification/widget request must take precedence over the terminal that
    // happened to be visible before the intent arrived.
    val targetSessionId = requestedOpenSessionId ?: quickConnectRequestSessionId
    val targetSnapshot = targetSessionId?.let(snapshotsBySessionId::get)
    val hasUsableRouteSession = when (targetSnapshot) {
        null -> targetSessionId != null && targetSessionId in runtimeSessionIds
        else -> targetSnapshot.hasUsableBackingSession()
    }
    val routeHasNoSessionRequest = quickConnectRequestSessionId == null && requestedOpenSessionId == null
    val canRecoverFromCurrentSessionRoute =
        currentRoute == Routes.SESSION || sawSnapshotForCurrentRequest || routeHasNoSessionRequest
    val currentSessionRouteIsStale = sessionServiceReady &&
        isSessionVerticalRoute &&
        !pendingConnectingNavigation &&
        !hasUsableRouteSession &&
        canRecoverFromCurrentSessionRoute
    val shouldRecoverRoute = currentSessionRouteIsStale
    val shouldGateContent = when {
        shouldRecoverRoute -> true
        !isSessionVerticalRoute -> false
        requestedOpenSessionIsAwaitingSnapshot -> true
        pendingConnectingNavigation -> false
        sessionServiceReady -> false
        currentRoute == Routes.SESSION -> true
        quickConnectRequestSessionId != null || requestedOpenSessionId != null -> true
        else -> false
    }

    return StaleSessionRouteDecision(
        gateTerminalContent = shouldGateContent,
        consumeRequestedOpenSessionId = requestedOpenSessionIsStale,
        recoveryRoute = if (shouldRecoverRoute) {
            validatedSessionRecoveryRoute(routeBeforeConnecting)
        } else {
            null
        }
    )
}

internal fun decideSessionRouteRendering(
    currentRoute: String,
    staleSessionRouteDecision: StaleSessionRouteDecision
): SessionRouteRenderDecision {
    val isSessionVerticalRoute = currentRoute == Routes.CONNECTING || currentRoute == Routes.SESSION
    if (!isSessionVerticalRoute) {
        return SessionRouteRenderDecision(
            chromeRoute = currentRoute,
            renderTerminalContent = false
        )
    }
    return if (staleSessionRouteDecision.gateTerminalContent) {
        SessionRouteRenderDecision(
            chromeRoute = staleSessionRouteDecision.recoveryRoute ?: currentRoute,
            renderTerminalContent = false
        )
    } else {
        SessionRouteRenderDecision(
            chromeRoute = currentRoute,
            renderTerminalContent = true
        )
    }
}

internal fun validatedSessionRecoveryRoute(routeBeforeConnecting: String): String =
    routeBeforeConnecting.takeIf { it in sessionRecoveryRoutes } ?: Routes.HOME

private fun SessionRouteSnapshot.hasUsableBackingSession(): Boolean =
    status != SessionRouteSnapshotStatus.ACTIVE ||
        !requiresTerminalEmulator ||
        terminalEmulatorAvailable

private val sessionRecoveryRoutes = setOf(
    Routes.HOME,
    Routes.HELP,
    Routes.HOSTS,
    Routes.UPTIME,
    Routes.IDENTITIES,
    Routes.FORWARDS,
    Routes.SNIPPETS,
    Routes.KEYBOARD,
    Routes.THEME_EDITOR,
    Routes.SETTINGS,
    Routes.OPEN_SOURCE_LICENSES
)
