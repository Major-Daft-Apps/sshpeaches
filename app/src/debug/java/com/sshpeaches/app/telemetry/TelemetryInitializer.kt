package com.majordaftapps.sshpeaches.app.telemetry

import android.app.Application

/**
 * Debug variant intentionally keeps telemetry disabled.
 */
object TelemetryInitializer {
    fun initialize(application: Application) {
        // No-op on debug builds.
    }

    fun recordNonFatal(action: String, throwable: Throwable) {
        // No-op on debug builds.
    }

    fun logUsageEvent(action: String) {
        // No-op on debug builds.
    }
}
