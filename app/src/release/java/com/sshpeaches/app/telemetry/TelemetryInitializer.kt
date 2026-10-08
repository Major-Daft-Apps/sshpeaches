package com.majordaftapps.sshpeaches.app.telemetry

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    fun initialize(application: Application) {
        if (initialized) return
        initialized = true

        if (android.os.Build.FINGERPRINT?.startsWith("robolectric") == true) {
            // JVM unit tests have no Firebase runtime; Crashlytics' uncaught
            // exception handler would kill the test JVM.
            Log.i(TAG, "Robolectric detected; release telemetry is disabled in tests.")
            return
        }

        val app = FirebaseApp.initializeApp(application)
        if (app == null) {
            Log.w(TAG, "Firebase config is missing; release telemetry is disabled.")
            return
        }

        crashReportsEnabled = SettingsStore.getStartupCrashReportsEnabled()
        analyticsEnabled = SettingsStore.getStartupAnalyticsEnabled()
        usageReportsEnabled = SettingsStore.getStartupUsageReportsEnabled()

        crashlytics = FirebaseCrashlytics.getInstance().also {
            applyCrashReports(it, crashReportsEnabled)
        }
        analytics = FirebaseAnalytics.getInstance(application).also {
            applyAnalytics(it, analyticsEnabled, usageReportsEnabled)
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
                crashlytics?.let { applyCrashReports(it, crashEnabled) }
                analytics?.let { applyAnalytics(it, analyticsEnabled, usageEnabled) }
            }
        }
    }

    /**
     * Reports an unexpected error that didn't crash the app. Only with "Crash reports" on, and
     * scrubbed first: no host names, addresses, usernames, or paths ([secrets] adds known ones).
     */
    fun recordNonFatal(
        action: String,
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
        secrets: Collection<String?> = emptyList()
    ) {
        if (!crashReportsEnabled) return
        // A cancelled coroutine is normal control flow, never a bug report.
        if (throwable is java.util.concurrent.CancellationException) return
        val reporter = crashlytics ?: return
        reporter.setCustomKey("action", action)
        context.forEach { (key, value) -> reporter.setCustomKey(key, TelemetrySanitizer.scrub(value, secrets)) }
        reporter.recordException(TelemetrySanitizer.sanitize(throwable, secrets))
    }

    /** A scrubbed line in the log Crashlytics attaches to the next crash or non-fatal report. */
    fun breadcrumb(event: String, secrets: Collection<String?> = emptyList()) {
        if (!crashReportsEnabled) return
        crashlytics?.log(TelemetrySanitizer.scrub(event, secrets))
    }

    /** App state attached to later reports, such as the number of open sessions. */
    fun setState(key: String, value: String) {
        if (!crashReportsEnabled) return
        crashlytics?.setCustomKey(key, TelemetrySanitizer.scrub(value))
    }

    /**
     * "Crash reports" off also discards reports recorded but not yet sent, so nothing collected
     * before the switch leaves the phone afterwards.
     */
    private fun applyCrashReports(reporter: FirebaseCrashlytics, enabled: Boolean) {
        reporter.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) reporter.deleteUnsentReports()
    }

    /** "Usage analytics" alone controls Firebase Analytics; "Send usage reports" is the weekly upload. */
    private fun applyAnalytics(analytics: FirebaseAnalytics, enabled: Boolean, usageReports: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
        analyticsCollectionApplied = enabled
        if (enabled) analytics.setUserProperty("usage_reports_opt_in", usageReports.toString())
    }

    @Volatile private var analyticsCollectionApplied: Boolean? = null

    /** What each SDK was last told, for tests and support. Null where it isn't initialized. */
    fun collectionState(): TelemetryCollectionState = TelemetryCollectionState(
        crashReports = crashlytics?.isCrashlyticsCollectionEnabled,
        analytics = analyticsCollectionApplied
    )

    fun logUsageEvent(action: String) {
        if (!analyticsEnabled) return
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
