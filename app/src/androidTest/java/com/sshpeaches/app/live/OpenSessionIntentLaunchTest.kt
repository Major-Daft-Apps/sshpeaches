package com.majordaftapps.sshpeaches.app.live

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.launchMainActivityThroughFramework
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.widget.WidgetSessionStore
import com.termux.view.TerminalView
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class OpenSessionIntentLaunchTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @Test
    fun openSessionIntentColdLaunch_restoresExistingLiveTerminal() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )

        val baseIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = seedPasswordHost("Live Open Session")

        val sessionId = ActivityScenario.launch<MainActivity>(baseIntent).use {
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
            waitForSessionId(host.name)
        }

        ActivityScenario.launch<MainActivity>(
            Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java
            ).apply {
                action = SessionService.ACTION_OPEN_SESSION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(SessionService.EXTRA_HOST_ID, sessionId)
            }
        ).use {
            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
        }
    }

    @Test
    fun launcherRelaunchShowsOpenSessionAndResumesItFromHome() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )

        val baseIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = seedPasswordHost("Live Home Resume")

        val sessionId = ActivityScenario.launch<MainActivity>(baseIntent).use {
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            waitForSessionId(host.name)
        }

        ActivityScenario.launch<MainActivity>(baseIntent).use {
            waitForTag(UiTestTags.SCREEN_HOME)
            composeRule.onNodeWithTag(UiTestTags.openSessionAction(sessionId, "open"))
                .assertIsDisplayed()
                .performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
        }
    }

    @Test
    fun staleOpenSessionIntentAfterBackgroundStop_returnsHomeOrHostsWithoutTerminalPane() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = false
        )

        val baseIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = seedPasswordHost("Live Stale Notification Resume")

        ActivityScenario.launch<MainActivity>(baseIntent).use { scenario ->
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            val sessionId = waitForSessionId(host.name)

            scenario.moveToState(Lifecycle.State.CREATED)
            waitForSessionToDisappear(sessionId)

            launchMainActivityThroughFramework(
                Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MainActivity::class.java
                ).apply {
                    action = SessionService.ACTION_OPEN_SESSION
                    putExtra(SessionService.EXTRA_HOST_ID, sessionId)
                }
            )

            assertRecoveredToHomeOrHostsWithoutTerminal()
        }
    }

    @Test
    fun foregroundStaleOpenSessionIntent_preservesHostsWithoutTerminalPane() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )

        val baseIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = seedPasswordHost("Live Foreground Stale Intent")

        ActivityScenario.launch<MainActivity>(baseIntent).use {
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            val sessionId = waitForSessionId(host.name)
            composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
            waitForTag(UiTestTags.SCREEN_HOSTS)

            stopSession(sessionId)
            waitForSessionToDisappear(sessionId)

            launchMainActivityThroughFramework(
                Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MainActivity::class.java
                ).apply {
                    action = SessionService.ACTION_OPEN_SESSION
                    putExtra(SessionService.EXTRA_HOST_ID, sessionId)
                }
            )

            assertRouteWithoutTerminal(UiTestTags.SCREEN_HOSTS)
        }
    }

    @Test
    fun launcherRelaunchAfterBackgroundStop_returnsHomeOrHostsWithoutTerminalPane() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = false
        )

        val baseIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = seedPasswordHost("Live Stale Launcher Resume")

        ActivityScenario.launch<MainActivity>(baseIntent).use { scenario ->
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            val sessionId = waitForSessionId(host.name)

            scenario.moveToState(Lifecycle.State.CREATED)
            waitForSessionToDisappear(sessionId)

            launchMainActivityThroughFramework(baseIntent)

            assertRecoveredToHomeOrHostsWithoutTerminal()
        }
    }

    @Test
    fun parallelConnectingNotificationsOpenTheirOwnSessions() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val firstSessionId = "notification-a|SSH|${UUID.randomUUID()}"
        val secondSessionId = "notification-b|SSH|${UUID.randomUUID()}"
        val firstHost = livePromptHost(
            name = "Notification Session A",
            username = "notification-a"
        )
        val secondHost = livePromptHost(
            name = "Notification Session B",
            username = "notification-b"
        )

        val notificationManager =
            targetContext.getSystemService(NotificationManager::class.java)
        lateinit var service: SessionService
        lateinit var firstNotification: Notification
        ActivityScenario.launch<MainActivity>(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).use { scenario ->
            service = waitForSessionService(scenario)
            service.startSession(
                requestedSessionId = firstSessionId,
                host = firstHost,
                mode = com.majordaftapps.sshpeaches.app.data.model.ConnectionMode.SSH,
                passwordOverride = null,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false,
                allowPasswordSave = false
            )

            composeRule.waitUntil(30_000) {
                val byId = service.sessionsFlow().value.associateBy { it.hostId }
                byId[firstSessionId]?.status == SessionService.SessionStatus.CONNECTING
            }
            firstNotification = waitForSessionNotification(
                notificationManager,
                firstHost.name
            )
        }

        try {
            firstNotification.contentIntent.send()
            waitForSessionHost(firstHost)

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            check(device.pressHome()) { "Could not background MainActivity." }
            waitForMainActivityToStop()

            // Publish the second CONNECTING notification while MainActivity's normal
            // STARTED collector is stopped. This reproduces the stale cached-list race.
            service.startSession(
                requestedSessionId = secondSessionId,
                host = secondHost,
                mode = com.majordaftapps.sshpeaches.app.data.model.ConnectionMode.SSH,
                passwordOverride = null,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false,
                allowPasswordSave = false
            )
            composeRule.waitUntil(30_000) {
                val byId = service.sessionsFlow().value.associateBy { it.hostId }
                byId[firstSessionId]?.status == SessionService.SessionStatus.CONNECTING &&
                    byId[secondSessionId]?.status == SessionService.SessionStatus.CONNECTING
            }
            val secondNotification = waitForSessionNotification(
                notificationManager,
                secondHost.name
            )

            secondNotification.contentIntent.send()
            waitForSessionHost(secondHost)

            firstNotification.contentIntent.send()
            waitForSessionHost(firstHost)
        } finally {
            service.stopSession(firstSessionId)
            service.stopSession(secondSessionId)
        }
    }

    @Test
    fun foregroundOpenActionsSwitchTheVisibleActiveTerminal() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = true
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val firstSessionId = "foreground-notification-a|SSH|${UUID.randomUUID()}"
        val secondSessionId = "foreground-notification-b|SSH|${UUID.randomUUID()}"
        val firstHost = livePromptHost(name = "Foreground Session A")
        val secondHost = livePromptHost(name = "Foreground Session B")
        val notificationManager =
            targetContext.getSystemService(NotificationManager::class.java)
        lateinit var service: SessionService
        lateinit var firstNotification: Notification
        lateinit var secondNotification: Notification

        ActivityScenario.launch<MainActivity>(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).use { scenario ->
            service = waitForSessionService(scenario)
            service.startSession(
                requestedSessionId = firstSessionId,
                host = firstHost,
                mode = com.majordaftapps.sshpeaches.app.data.model.ConnectionMode.SSH,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false,
                allowPasswordSave = false
            )
            service.startSession(
                requestedSessionId = secondSessionId,
                host = secondHost,
                mode = com.majordaftapps.sshpeaches.app.data.model.ConnectionMode.SSH,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false,
                allowPasswordSave = false
            )

            composeRule.waitUntil(30_000) {
                val sessionsById = service.sessionsFlow().value.associateBy { it.hostId }
                sessionsById[firstSessionId]?.status == SessionService.SessionStatus.ACTIVE &&
                    sessionsById[secondSessionId]?.status == SessionService.SessionStatus.ACTIVE &&
                    service.resolveTerminalEmulator(firstSessionId) != null &&
                    service.resolveTerminalEmulator(secondSessionId) != null
            }
            check(
                service.resolveTerminalEmulator(firstSessionId) !==
                    service.resolveTerminalEmulator(secondSessionId)
            ) {
                "Parallel active sessions unexpectedly shared a terminal emulator."
            }
            firstNotification = waitForSessionNotification(notificationManager, firstHost.name)
            secondNotification = waitForSessionNotification(notificationManager, secondHost.name)
        }

        try {
            notificationOpenAction(firstNotification).send()
            waitForVisibleTerminalEmulator(service, firstSessionId)

            notificationOpenAction(secondNotification).send()
            waitForVisibleTerminalEmulator(service, secondSessionId)

            notificationOpenAction(firstNotification).send()
            waitForVisibleTerminalEmulator(service, firstSessionId)
        } finally {
            service.stopSession(firstSessionId)
            service.stopSession(secondSessionId)
        }
    }

    @Test
    fun visibleConnectionShowsItsShellWithoutLeavingAndReopening() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = true
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val host = livePromptHost("Visible Shell Transition")
        AppStateSeeder.seedHost(host)

        ActivityScenario.launch<MainActivity>(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).use { scenario ->
            val service = waitForSessionService(scenario)
            openDrawer()
            composeRule.onNodeWithTag(
                UiTestTags.drawerItem(Routes.HOSTS),
                useUnmergedTree = true
            ).performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.PASSWORD_PROMPT_DIALOG)
            val sessionId = waitForSessionId(host.name)
            check(
                service.sessionsFlow().value
                    .firstOrNull { it.hostId == sessionId }
                    ?.status == SessionService.SessionStatus.CONNECTING
            ) {
                "Session was not held on the visible Connecting screen before authentication."
            }
            check(!hasTag(UiTestTags.CONNECTING_TERMINAL_PANEL)) {
                "Terminal was shown before the session authenticated."
            }
            composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_INPUT)
                .performTextInput(LiveBackendConfig.password)
            composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_CONFIRM).performClick()

            composeRule.waitUntil(30_000) {
                service.sessionsFlow().value
                    .firstOrNull { it.hostId == sessionId }
                    ?.status == SessionService.SessionStatus.ACTIVE &&
                    service.resolveTerminalEmulator(sessionId) != null
            }
            waitForVisibleTerminalEmulator(
                service = service,
                sessionId = sessionId,
                timeoutMillis = 30_000
            )
        }
    }

    private fun openDrawer() {
        val wideDrawerVisible = runCatching {
            composeRule.onNodeWithTag(
                UiTestTags.DRAWER_SCROLL_CONTAINER,
                useUnmergedTree = true
            ).assertIsDisplayed()
        }.isSuccess
        if (!wideDrawerVisible) {
            composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(UiTestTags.DRAWER_SCROLL_CONTAINER, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSessionId(expectedTitle: String, timeoutMillis: Long = 15_000): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var sessionId: String? = null
        composeRule.waitUntil(timeoutMillis) {
            sessionId = WidgetSessionStore.read(context)
                .firstOrNull { it.title == expectedTitle }
                ?.sessionId
            sessionId != null
        }
        return checkNotNull(sessionId) { "No widget session id found for '$expectedTitle'." }
    }

    private fun waitForSessionToDisappear(sessionId: String, timeoutMillis: Long = 15_000) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.waitUntil(timeoutMillis) {
            WidgetSessionStore.read(context).none { it.sessionId == sessionId }
        }
    }

    private fun stopSession(sessionId: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startService(
            Intent(context, SessionService::class.java).apply {
                action = SessionService.ACTION_STOP_SESSION
                putExtra(SessionService.EXTRA_HOST_ID, sessionId)
            }
        )
    }

    private fun assertRecoveredToHomeOrHostsWithoutTerminal(timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            hasTag(UiTestTags.SCREEN_HOME) || hasTag(UiTestTags.SCREEN_HOSTS)
        }
        check(!hasTag(UiTestTags.SCREEN_CONNECTING)) {
            "Stale connecting screen was composed after foregrounding without a backing session."
        }
        check(!hasTag(UiTestTags.CONNECTING_TERMINAL_PANEL)) {
            "Stale terminal panel was composed after foregrounding without a backing session."
        }
    }

    private fun assertRouteWithoutTerminal(routeTag: String, timeoutMillis: Long = 15_000) {
        waitForTag(routeTag, timeoutMillis)
        check(!hasTag(UiTestTags.SCREEN_CONNECTING)) {
            "Stale open-session intent navigated away from the visible non-session route."
        }
        check(!hasTag(UiTestTags.CONNECTING_TERMINAL_PANEL)) {
            "Stale terminal panel was composed after foregrounding without a backing session."
        }
    }

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun waitForSessionService(
        scenario: ActivityScenario<MainActivity>,
        timeoutMillis: Long = 15_000
    ): SessionService {
        val serviceStateField = MainActivity::class.java
            .getDeclaredField("sessionServiceState")
            .apply { isAccessible = true }
        var service: SessionService? = null
        composeRule.waitUntil(timeoutMillis) {
            scenario.onActivity { activity ->
                @Suppress("UNCHECKED_CAST")
                val state = serviceStateField.get(activity) as MutableState<SessionService?>
                service = state.value
            }
            service != null
        }
        return checkNotNull(service) { "MainActivity did not bind SessionService." }
    }

    private fun waitForSessionNotification(
        notificationManager: NotificationManager,
        hostName: String,
        timeoutMillis: Long = 15_000
    ): Notification {
        var notification: Notification? = null
        composeRule.waitUntil(timeoutMillis) {
            notification = notificationManager.activeNotifications
                .map { it.notification }
                .firstOrNull {
                    it.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        .toString()
                        .startsWith("$hostName •")
                }
            notification != null
        }
        return checkNotNull(notification) { "No notification found for '$hostName'." }
    }

    private fun waitForSessionHost(host: HostConnection, timeoutMillis: Long = 15_000) {
        val sessionHost = "${host.username}@${host.host}:${host.port}"
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(sessionHost, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForMainActivityToStop(timeoutMillis: Long = 15_000) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(timeoutMillis) {
            var stopped = false
            instrumentation.runOnMainSync {
                stopped = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.STOPPED)
                    .any { it is MainActivity }
            }
            stopped
        }
    }

    private fun notificationOpenAction(notification: Notification) =
        notification.actions
            .single { it.title.toString() == "Open" }
            .actionIntent

    private fun waitForVisibleTerminalEmulator(
        service: SessionService,
        sessionId: String,
        timeoutMillis: Long = 15_000
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(timeoutMillis) {
            var matchesExpectedSession = false
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .singleOrNull()
                val terminalView = activity
                    ?.window
                    ?.decorView
                    ?.findTerminalViewDescendant()
                matchesExpectedSession =
                    terminalView?.mEmulator === service.resolveTerminalEmulator(sessionId)
            }
            matchesExpectedSession
        }
    }

    private fun View.findTerminalViewDescendant(): TerminalView? {
        if (this is TerminalView) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findTerminalViewDescendant()?.let { return it }
        }
        return null
    }

    private fun livePromptHost(
        name: String,
        username: String = LiveBackendConfig.username
    ): HostConnection =
        HostConnection(
            id = "live-notification-${UUID.randomUUID()}",
            name = name,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = username,
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = false
        )

    private fun seedPasswordHost(name: String): HostConnection {
        val host = HostConnection(
            id = "live-open-${UUID.randomUUID()}",
            name = name,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true,
            favorite = true
        )
        AppStateSeeder.seedHost(host, LiveBackendConfig.password)
        return host
    }
}
