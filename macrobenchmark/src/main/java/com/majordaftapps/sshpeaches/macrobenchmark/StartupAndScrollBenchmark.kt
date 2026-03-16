package com.majordaftapps.sshpeaches.macrobenchmark

import android.content.Intent
import android.os.Build
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupAndScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        assumeSupportedPlatform()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait(launchIntent())
            device.waitForIdle()
        }
    }

    @Test
    fun warmStartupFrameTiming() {
        assumeSupportedPlatform()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait(launchIntent())
            device.waitForIdle()
        }
    }

    private fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun assumeSupportedPlatform() {
        assumeTrue(
            "Macrobenchmark launch completion is unstable on Android 16 (API 36) emulators; run this lane on API 35 or lower.",
            Build.VERSION.SDK_INT <= 35
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.majordaftapps.sshpeaches"
        const val TARGET_ACTIVITY = "com.majordaftapps.sshpeaches.app.MainActivity"
    }
}
