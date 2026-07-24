package com.majordaftapps.sshpeaches.app.startup

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.launchMainActivityThroughFramework
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.widget.HostWidgets
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

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @Test
    fun staleOpenSessionIntentColdLaunchLandsOnHomeAndConsumesRequest() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch<MainActivity>(
            openSessionIntent(targetContext, "missing-cold-session")
        ).use { scenario ->
            waitForTag(UiTestTags.SCREEN_HOME)
            waitForRequestedOpenSessionCleared(scenario)

            composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
            assertNoTag(UiTestTags.SCREEN_CONNECTING)
            assertNoTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        }
    }

    @Test
    fun staleOpenSessionIntentFromNonSessionRouteKeepsCurrentRouteAndConsumesRequest() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch<MainActivity>(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).use { scenario ->
            waitForTag(UiTestTags.SCREEN_HOME)
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.SETTINGS), useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            waitForTag(UiTestTags.SCREEN_SETTINGS)

            launchMainActivityThroughFramework(
                openSessionIntent(targetContext, "missing-foreground-session")
            )
            waitForRequestedOpenSessionCleared(scenario)

            composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()
            assertNoTag(UiTestTags.SCREEN_CONNECTING)
            assertNoTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        }
    }

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
    fun forgedWidgetConnectIntentWithoutTokenDoesNotStartSessionLaunchPath() {
        val host = seedWidgetHost("Forged Widget Host")
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        WidgetSessionStore.write(targetContext, emptyList())

        ActivityScenario.launch<MainActivity>(
            widgetConnectIntent(targetContext, host.id, trusted = false)
        ).use { scenario ->
            waitForScenarioState(scenario, Lifecycle.State.RESUMED)
            SystemClock.sleep(1_000)

            val sessions = WidgetSessionStore.read(targetContext)
            check(sessions.none { it.title.contains(host.name) }) {
                "Forged widget-connect intent should not publish an open-session entry for ${host.name}"
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

    private fun openSessionIntent(targetContext: android.content.Context, sessionId: String): Intent =
        Intent(targetContext, MainActivity::class.java).apply {
            action = SessionService.ACTION_OPEN_SESSION
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(SessionService.EXTRA_HOST_ID, sessionId)
        }

    private fun openDrawer() {
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.DRAWER_SCROLL_CONTAINER, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun assertNoTag(tag: String) {
        check(
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        ) { "Unexpected node with tag '$tag' was composed." }
    }

    private fun waitForRequestedOpenSessionCleared(scenario: ActivityScenario<MainActivity>) {
        val stateField = MainActivity::class.java.getDeclaredField("requestedOpenSessionHostId").apply {
            isAccessible = true
        }
        composeRule.waitUntil(10_000) {
            var cleared = false
            scenario.onActivity { activity ->
                @Suppress("UNCHECKED_CAST")
                val state = stateField.get(activity) as MutableState<String?>
                cleared = state.value == null
            }
            cleared
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

    private fun widgetConnectIntent(
        targetContext: android.content.Context,
        hostId: String,
        trusted: Boolean = true
    ): Intent =
        Intent(targetContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_WIDGET_CONNECT
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(MainActivity.EXTRA_WIDGET_HOST_ID, hostId)
            putExtra(MainActivity.EXTRA_WIDGET_MODE, ConnectionMode.SSH.name)
            if (trusted) {
                HostWidgets.putActionToken(this, targetContext)
            }
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
