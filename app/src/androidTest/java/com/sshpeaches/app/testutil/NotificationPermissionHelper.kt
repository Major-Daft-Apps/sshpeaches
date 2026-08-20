package com.majordaftapps.sshpeaches.app.testutil

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.TestRule

object NotificationPermissionHelper {
    /**
     * POST_NOTIFICATIONS is a runtime permission only on Android 13 and newer.
     * GrantPermissionRule delegates directly to UiAutomation and throws on older
     * platform releases where the permission is unknown, so use a no-op rule there.
     */
    fun grantRule(): TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    fun revoke() {
        runShell("cmd appops set ${targetPackage()} POST_NOTIFICATION ignore")
        waitForNotificationsEnabled(expected = false)
    }

    fun grant() {
        runShell("cmd appops set ${targetPackage()} POST_NOTIFICATION allow")
        waitForNotificationsEnabled(expected = true)
    }

    private fun targetPackage(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private fun waitForNotificationsEnabled(expected: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled() == expected) {
                return
            }
            SystemClock.sleep(100)
        }
    }
}
