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
 * Debug builds have no Firebase: reports go to Logcat, following the same toggles as release.
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

    fun recordNonFatal(
        action: String,
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
        secrets: Collection<String?> = emptyList()
    ) {
        if (!crashReportsEnabled) return
        // A cancelled coroutine is normal control flow, never a bug report.
        if (throwable is java.util.concurrent.CancellationException) return
        Log.e(TAG, "Debug non-fatal [$action] $context", TelemetrySanitizer.sanitize(throwable, secrets))
    }

    fun breadcrumb(event: String, secrets: Collection<String?> = emptyList()) {
        if (!crashReportsEnabled) return
        Log.i(TAG, "Breadcrumb: ${TelemetrySanitizer.scrub(event, secrets)}")
    }

    fun setState(key: String, value: String) {
        if (!crashReportsEnabled) return
        Log.i(TAG, "State $key=${TelemetrySanitizer.scrub(value)}")
    }

    fun collectionState(): TelemetryCollectionState =
        TelemetryCollectionState(crashReports = crashReportsEnabled, analytics = analyticsEnabled)

    fun logUsageEvent(action: String) {
        if (!analyticsEnabled) return
        Log.i(TAG, "Debug usage event [$action]")
    }
}
