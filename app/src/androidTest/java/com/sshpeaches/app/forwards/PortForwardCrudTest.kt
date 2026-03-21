package com.majordaftapps.sshpeaches.app.forwards

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortForwardCrudTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addPortForward_showsInList() {
        val host = sampleHost(id = "host-forward-add", name = "QA Host", address = "qa.internal")
        AppStateSeeder.seedHost(host)
        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FORWARDS).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.FORWARDS)).performClick()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_LABEL_INPUT).performTextInput("QA Tunnel")
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_BIND_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_BIND_INPUT).performTextInput("127.0.0.1")
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_SOURCE_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_SOURCE_PORT_INPUT).performTextInput("8081")
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_HOST_FIELD).performClick()
        composeRule.onNodeWithTag(UiTestTags.forwardHostOption(host.id)).performClick()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_DEST_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_DEST_PORT_INPUT).performTextInput("443")
        composeRule.onNodeWithText("Add").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("QA Tunnel").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onAllNodesWithContentDescription("More actions", useUnmergedTree = true)[0]
            .performClick()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test
    fun editAndDeleteSeededPortForward() {
        val host = sampleHost(id = "host-forward-edit", name = "Seed Host", address = "seed.internal")
        AppStateSeeder.seedHost(host)
        AppStateSeeder.seedPortForward(
            PortForward(
                id = "seed-forward",
                label = "Seed Tunnel",
                type = PortForwardType.LOCAL,
                sourceHost = "127.0.0.1",
                sourcePort = 8081,
                destinationHost = host.host,
                destinationPort = 443,
                associatedHosts = listOf(host.id)
            )
        )
        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithText("Seed Tunnel").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.forwardOverflowButton("seed-forward")).performClick()
        composeRule.onNodeWithTag(UiTestTags.forwardOverflowAction("seed-forward", "edit")).performClick()
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_LABEL_INPUT)
            .performTextReplacement("QA Tunnel Updated")
        composeRule.onNodeWithTag(UiTestTags.FORWARD_DIALOG_DEST_PORT_INPUT)
            .performTextReplacement("8443")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("QA Tunnel Updated").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(UiTestTags.forwardOverflowButton("seed-forward")).performClick()
        composeRule.onNodeWithTag(UiTestTags.forwardOverflowAction("seed-forward", "delete")).performClick()
        composeRule.onNodeWithTag(UiTestTags.DELETE_CONFIRM_BUTTON).performClick()
        composeRule.onAllNodesWithText("QA Tunnel Updated").assertCountEquals(0)
    }

    private fun sampleHost(id: String, name: String, address: String): HostConnection =
        HostConnection(
            id = id,
            name = name,
            host = address,
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
}
