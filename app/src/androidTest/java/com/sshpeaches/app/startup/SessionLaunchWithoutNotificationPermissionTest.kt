package com.majordaftapps.sshpeaches.app.startup

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionLaunchWithoutNotificationPermissionTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @Test
    fun widgetConnectIntentDoesNotCrashWhenNotificationsAreRevoked() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val packageName = targetContext.packageName
        val host = HostConnection(
            id = "widget-no-notification-${UUID.randomUUID()}",
            name = "Widget No Notifications",
            host = "127.0.0.1",
            port = 9,
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true,
            defaultMode = ConnectionMode.SSH
        )
        AppStateSeeder.seedHost(host, password = "widget-secret")

        uiAutomation.executeShellCommand("cmd appops set $packageName POST_NOTIFICATION ignore").close()

        try {
            ActivityScenario.launch<MainActivity>(
                Intent(targetContext, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_WIDGET_CONNECT
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(MainActivity.EXTRA_WIDGET_HOST_ID, host.id)
                    putExtra(MainActivity.EXTRA_WIDGET_MODE, ConnectionMode.SSH.name)
                }
            ).use { scenario ->
                waitForScenarioState(scenario, Lifecycle.State.RESUMED)
                check(scenario.state == Lifecycle.State.RESUMED) {
                    "Session launch should not destroy MainActivity when notifications are revoked; state=${scenario.state}"
                }
                SystemClock.sleep(2_000)
                check(scenario.state == Lifecycle.State.RESUMED) {
                    "Session launch regressed after connect start when notifications are revoked; state=${scenario.state}"
                }
            }
        } finally {
            uiAutomation.executeShellCommand("cmd appops set $packageName POST_NOTIFICATION allow").close()
        }
    }

    private fun waitForScenarioState(
        scenario: ActivityScenario<MainActivity>,
        expectedState: Lifecycle.State
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (scenario.state == expectedState) {
                return
            }
            SystemClock.sleep(100)
        }
    }
}
