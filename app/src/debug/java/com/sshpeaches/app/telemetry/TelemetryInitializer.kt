package com.majordaftapps.sshpeaches.app.telemetry

import android.app.Application
import android.util.Log
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Debug builds keep telemetry signals enabled locally without Firebase wiring.
 */
object TelemetryInitializer {
    private const val TAG = "SSHPeachesTelemetry"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false
    @Volatile private var crashReportsEnabled = SettingsStore.defaultCrashReportsEnabled
    @Volatile private var analyticsEnabled = SettingsStore.defaultAnalyticsEnabled
    @Volatile private var usageReportsEnabled = SettingsStore.defaultUsageReportsEnabled

    @Suppress("UNUSED_PARAMETER")
    fun initialize(application: Application) {
        if (initialized) return
        initialized = true

        crashReportsEnabled = true
        analyticsEnabled = true
        usageReportsEnabled = true

        Log.i(
            TAG,
            "Debug telemetry forcing all local telemetry on; Firebase remains disabled in debug."
        )

        scope.launch {
            SettingsStore.setCrashReportsEnabled(true)
            SettingsStore.setAnalyticsEnabled(true)
            SettingsStore.setDiagnosticsEnabled(true)
            SettingsStore.setUsageReportsEnabled(true)
        }

        scope.launch {
            combine(
                SettingsStore.crashReportsEnabled,
                SettingsStore.analyticsEnabled,
                SettingsStore.usageReportsEnabled
            ) { crashEnabled, analyticsEnabled, usageEnabled ->
                Triple(crashEnabled, analyticsEnabled, usageEnabled)
            }.collect { (crashEnabled, analyticsEnabled, usageEnabled) ->
                crashReportsEnabled = crashEnabled
                this@TelemetryInitializer.analyticsEnabled = analyticsEnabled
                usageReportsEnabled = usageEnabled
                Log.i(
                    TAG,
                    "Debug telemetry updated: crash=$crashEnabled analytics=$analyticsEnabled usage=$usageEnabled"
                )
            }
        }
    }

    fun recordNonFatal(action: String, throwable: Throwable) {
        if (!crashReportsEnabled) return
        Log.e(TAG, "Debug non-fatal [$action]", throwable)
    }

    fun logUsageEvent(action: String) {
        if (!analyticsEnabled && !usageReportsEnabled) return
        Log.i(TAG, "Debug usage event [$action]")
    }
}
