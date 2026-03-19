package com.majordaftapps.sshpeaches.app.telemetry

import android.app.Application

/**
 * Debug variant intentionally keeps telemetry disabled.
 */
object TelemetryInitializer {
    @Suppress("UNUSED_PARAMETER")
    fun initialize(application: Application) {
        // No-op on debug builds.
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordNonFatal(action: String, throwable: Throwable) {
        // No-op on debug builds.
    }

    @Suppress("UNUSED_PARAMETER")
    fun logUsageEvent(action: String) {
        // No-op on debug builds.
    }
}
