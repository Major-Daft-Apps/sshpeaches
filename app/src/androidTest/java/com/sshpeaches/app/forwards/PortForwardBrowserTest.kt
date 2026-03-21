package com.majordaftapps.sshpeaches.app.forwards

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortForwardBrowserTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.waitForIdle()
        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FORWARDS).assertIsDisplayed()
    }

    @Test
    fun searchFavoriteAndShareWorkForPortForward() {
        val docsTunnel = PortForward(
            id = "forward-docs",
            label = "Docs Tunnel",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 8080,
            destinationHost = "docs.internal",
            destinationPort = 443
        )
        val metricsTunnel = PortForward(
            id = "forward-metrics",
            label = "Metrics Tunnel",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 9090,
            destinationHost = "metrics.internal",
            destinationPort = 8443
        )
        AppStateSeeder.seedPortForward(docsTunnel)
        AppStateSeeder.seedPortForward(metricsTunnel)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.FORWARDS)

        waitForForward("Docs Tunnel")
        waitForForward("Metrics Tunnel")

        composeRule.onNodeWithTag(UiTestTags.FORWARD_SEARCH_INPUT)
            .performTextReplacement("docs.internal")
        composeRule.onNodeWithText("Docs Tunnel").assertIsDisplayed()
        composeRule.onAllNodesWithText("Metrics Tunnel").assertCountEquals(0)

        composeRule.onNodeWithTag(UiTestTags.forwardFavorite(docsTunnel.id)).performClick()
        composeRule.onNodeWithTag(UiTestTags.forwardShare(docsTunnel.id)).performClick()
        composeRule.onNodeWithText("Share Docs Tunnel").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.navigateDrawer(Routes.HOME)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        waitForForward("Docs Tunnel")
        composeRule.onNodeWithText("Docs Tunnel").assertIsDisplayed()
    }

    private fun waitForForward(label: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
