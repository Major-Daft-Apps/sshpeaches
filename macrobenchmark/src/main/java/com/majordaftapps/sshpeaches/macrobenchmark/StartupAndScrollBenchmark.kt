package com.majordaftapps.sshpeaches.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupAndScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
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

    @Test
    fun warmStartupFrameTiming() = benchmarkRule.measureRepeated(
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
            // Force a visible redraw so FrameTimingMetric has renderthread slices to measure.
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.75f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.35f).toInt(),
                20
            )
            device.waitForIdle()
        }

    private fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private companion object {
        const val TARGET_PACKAGE = "com.majordaftapps.sshpeaches"
        const val TARGET_ACTIVITY = "com.majordaftapps.sshpeaches.app.MainActivity"
    }
}
