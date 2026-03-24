package com.majordaftapps.sshpeaches.app.telemetry

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Release-only telemetry wiring backed by Firebase.
 */
object TelemetryInitializer {
    private const val TAG = "SSHPeachesTelemetry"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false
    @Volatile private var crashReportsEnabled = true
    @Volatile private var analyticsEnabled = true
    @Volatile private var usageReportsEnabled = false
    @Volatile private var crashlytics: FirebaseCrashlytics? = null
    @Volatile private var analytics: FirebaseAnalytics? = null
    @Volatile private var performance: FirebasePerformance? = null

    fun initialize(application: Application) {
        if (initialized) return
        initialized = true

        val app = FirebaseApp.initializeApp(application)
        if (app == null) {
            Log.w(TAG, "Firebase config is missing; release telemetry is disabled.")
            return
        }

        crashReportsEnabled = SettingsStore.getStartupCrashReportsEnabled()
        analyticsEnabled = SettingsStore.getStartupAnalyticsEnabled()
        usageReportsEnabled = SettingsStore.getStartupUsageReportsEnabled()

        crashlytics = FirebaseCrashlytics.getInstance().also {
            it.setCrashlyticsCollectionEnabled(crashReportsEnabled)
        }
        analytics = FirebaseAnalytics.getInstance(application).also {
            it.setAnalyticsCollectionEnabled(analyticsEnabled || usageReportsEnabled)
            it.setUserProperty("usage_reports_opt_in", usageReportsEnabled.toString())
        }
        performance = FirebasePerformance.getInstance().also {
            it.isPerformanceCollectionEnabled = analyticsEnabled
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
                crashlytics?.setCrashlyticsCollectionEnabled(crashEnabled)
                analytics?.setAnalyticsCollectionEnabled(analyticsEnabled || usageEnabled)
                analytics?.setUserProperty("usage_reports_opt_in", usageEnabled.toString())
                performance?.isPerformanceCollectionEnabled = analyticsEnabled
            }
        }
    }

    fun recordNonFatal(action: String, throwable: Throwable) {
        if (!crashReportsEnabled) return
        crashlytics?.setCustomKey("action", action)
        crashlytics?.recordException(throwable)
    }

    fun logUsageEvent(action: String) {
        if (!analyticsEnabled && !usageReportsEnabled) return
        val sanitized = action
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "unknown_action" }
        analytics?.logEvent(
            "app_action",
            Bundle().apply { putString("action", sanitized) }
        )
    }
}
