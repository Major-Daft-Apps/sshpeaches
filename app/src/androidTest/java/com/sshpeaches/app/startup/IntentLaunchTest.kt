package com.majordaftapps.sshpeaches.app.startup

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.widget.WidgetSessionStore
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentLaunchTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun widgetConnectIntentStartsSessionLaunchPath() {
        val host = seedWidgetHost("Widget Host")
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        WidgetSessionStore.write(targetContext, emptyList())

        ActivityScenario.launch<MainActivity>(
            widgetConnectIntent(targetContext, host.id)
        ).use { scenario ->
            waitForWidgetSession(targetContext, host.name)

            val sessions = WidgetSessionStore.read(targetContext)
            check(sessions.any { it.title.contains(host.name) }) {
                "Widget-connect launch did not publish an open-session entry for ${host.name}"
            }
            waitForScenarioState(scenario, Lifecycle.State.RESUMED)
            check(scenario.state == Lifecycle.State.RESUMED) {
                "Widget-connect launch did not keep MainActivity resumed"
            }
        }
    }

    @Test
    fun widgetConnectLaunchPathStillPublishesSessionAfterRecreate() {
        val host = seedWidgetHost("Widget Recreate Host")
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        WidgetSessionStore.write(targetContext, emptyList())

        ActivityScenario.launch<MainActivity>(
            widgetConnectIntent(targetContext, host.id)
        ).use { scenario ->
            waitForWidgetSession(targetContext, host.name)
            scenario.recreate()
            waitForWidgetSession(targetContext, host.name)

            val sessions = WidgetSessionStore.read(targetContext)
            check(sessions.any { it.title.contains(host.name) }) {
                "Widget-connect recreate path lost the open-session entry for ${host.name}"
            }
            waitForScenarioState(scenario, Lifecycle.State.RESUMED)
            check(scenario.state == Lifecycle.State.RESUMED) {
                "Widget-connect recreate path did not keep MainActivity resumed"
            }
        }
    }

    @Test
    fun widgetConnectLaunchPathStillPublishesSessionAfterImmediateRecreate() {
        val host = seedWidgetHost("Widget Early Recreate Host")
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        WidgetSessionStore.write(targetContext, emptyList())

        ActivityScenario.launch<MainActivity>(
            widgetConnectIntent(targetContext, host.id)
        ).use { scenario ->
            scenario.recreate()
            waitForWidgetSession(targetContext, host.name)

            val sessions = WidgetSessionStore.read(targetContext)
            check(sessions.any { it.title.contains(host.name) }) {
                "Widget-connect early recreate path lost the open-session entry for ${host.name}"
            }
            waitForScenarioState(scenario, Lifecycle.State.RESUMED)
            check(scenario.state == Lifecycle.State.RESUMED) {
                "Widget-connect early recreate path did not keep MainActivity resumed"
            }
        }
    }

    private fun seedWidgetHost(name: String): HostConnection {
        val host = HostConnection(
            id = "widget-${UUID.randomUUID()}",
            name = name,
            host = "127.0.0.1",
            port = 9,
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true,
            defaultMode = ConnectionMode.SSH
        )
        AppStateSeeder.seedHost(host, password = "widget-secret")
        return host
    }

    private fun widgetConnectIntent(targetContext: android.content.Context, hostId: String): Intent =
        Intent(targetContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_WIDGET_CONNECT
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(MainActivity.EXTRA_WIDGET_HOST_ID, hostId)
            putExtra(MainActivity.EXTRA_WIDGET_MODE, ConnectionMode.SSH.name)
        }

    private fun waitForWidgetSession(targetContext: android.content.Context, hostName: String) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val sessions = WidgetSessionStore.read(targetContext)
            if (sessions.any { it.title.contains(hostName) }) {
                return
            }
            SystemClock.sleep(250)
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
