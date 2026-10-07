package com.majordaftapps.sshpeaches.app.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the release build (real Firebase), each data-collection switch must change what the SDKs do:
 * Crash reports → Crashlytics, Usage analytics → Analytics (alone), Send usage reports → the weekly
 * upload job.
 */
@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class TelemetryTogglesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun restoreDefaults() = runBlocking {
        SettingsStore.setCrashReportsEnabled(true)
        SettingsStore.setAnalyticsEnabled(true)
        SettingsStore.setUsageReportsEnabled(false)
    }

    @Test
    fun eachSwitchControlsItsOwnCollection() = runBlocking {
        SettingsStore.init(context)

        set(crash = false, analytics = false, usage = true)
        // Usage reports on must not switch Analytics back on.
        awaitState(crash = false, analytics = false, usageJobScheduled = true)

        set(crash = true, analytics = true, usage = false)
        awaitState(crash = true, analytics = true, usageJobScheduled = false)

        set(crash = false, analytics = true, usage = false)
        awaitState(crash = false, analytics = true, usageJobScheduled = false)
    }

    private suspend fun set(crash: Boolean, analytics: Boolean, usage: Boolean) {
        SettingsStore.setCrashReportsEnabled(crash)
        SettingsStore.setAnalyticsEnabled(analytics)
        SettingsStore.setUsageReportsEnabled(usage)
    }

    private fun awaitState(crash: Boolean, analytics: Boolean, usageJobScheduled: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var last: String
        while (true) {
            val state = TelemetryInitializer.collectionState()
            val scheduled = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("sshpeaches_diagnostics_upload").get()
                .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            last = "crash=${state.crashReports} analytics=${state.analytics} usageJob=$scheduled"
            if (state.crashReports == crash && state.analytics == analytics && scheduled == usageJobScheduled) return
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(200)
        }
        assertEquals("crash=$crash analytics=$analytics usageJob=$usageJobScheduled", last)
    }
}
