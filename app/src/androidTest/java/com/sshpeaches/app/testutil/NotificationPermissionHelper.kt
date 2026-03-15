package com.majordaftapps.sshpeaches.app.testutil

import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry

object NotificationPermissionHelper {
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
