package com.majordaftapps.sshpeaches.app.forwards

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
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
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

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

    /** An imported remote forward says it needs a look until the user turns it on; other forwards never say so. */
    @Test
    fun anImportedRemoteForwardIsMarkedForReviewUntilItIsTurnedOn() {
        val host = com.majordaftapps.sshpeaches.app.data.model.HostConnection(
            id = "review-host", name = "review host", host = "192.0.2.50", username = "bob",
            preferredAuth = com.majordaftapps.sshpeaches.app.data.model.AuthMethod.PASSWORD
        )
        val imported = PortForward(
            id = "review-remote", label = "Imported Remote", type = PortForwardType.REMOTE, sourceHost = "127.0.0.1",
            sourcePort = 8080, destinationHost = "192.168.1.10", destinationPort = 80,
            associatedHosts = listOf(host.id), enabled = false, needsReview = true
        )
        val own = PortForward(
            id = "review-local", label = "Own Local", type = PortForwardType.LOCAL, sourceHost = "127.0.0.1",
            sourcePort = 9090, destinationHost = "127.0.0.1", destinationPort = 90,
            associatedHosts = listOf(host.id), enabled = false
        )
        AppStateSeeder.seedHost(host)
        AppStateSeeder.seedPortForward(imported)
        AppStateSeeder.seedPortForward(own)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.FORWARDS)
        waitForForward("Imported Remote")
        waitForForward("Own Local")

        composeRule.onNodeWithTag(UiTestTags.forwardNeedsReview(imported.id), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(UiTestTags.forwardNeedsReview(own.id)), useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag(UiTestTags.forwardEnabledSwitch(imported.id)).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(UiTestTags.forwardNeedsReview(imported.id)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        val dao = com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
            .get(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).portForwardDao()
        val saved = kotlinx.coroutines.runBlocking { dao.getById(imported.id) }!!
        org.junit.Assert.assertTrue(saved.enabled)
        org.junit.Assert.assertFalse(saved.needsReview)
    }

    private fun waitForForward(label: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
