package com.majordaftapps.sshpeaches.app.live

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.widget.WidgetSessionStore
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
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

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
    fun launcherRelaunchShowsOpenSessionAndResumesItFromFavorites() {
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
        val host = seedPasswordHost("Live Favorites Resume")

        val sessionId = ActivityScenario.launch<MainActivity>(baseIntent).use {
            openDrawer()
            composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            waitForSessionId(host.name)
        }

        ActivityScenario.launch<MainActivity>(baseIntent).use {
            waitForTag(UiTestTags.SCREEN_FAVORITES)
            composeRule.onNodeWithTag(UiTestTags.openSessionAction(sessionId, "open"))
                .assertIsDisplayed()
                .performClick()

            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
        }
    }

    private fun openDrawer() {
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
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
