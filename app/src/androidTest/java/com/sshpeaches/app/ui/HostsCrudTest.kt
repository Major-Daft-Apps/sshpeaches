package com.majordaftapps.sshpeaches.app.ui

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostsCrudTest {

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
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()
    }

    @Test
    fun addEditDeleteHost_updatesList() {
        composeRule.onNodeWithTag(UiTestTags.HOST_ADD_BUTTON).performClick()

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).performTextInput("QA Host")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).performTextInput("10.0.2.2")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextInput("22")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_USERNAME_INPUT).performTextInput("tester")
        selectPasswordAuth()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CONFIRM_BUTTON).performClick()

        composeRule.onNodeWithText("QA Host").assertIsDisplayed()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Edit", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        val editIcons = composeRule
            .onAllNodesWithContentDescription("Edit", useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (editIcons.isNotEmpty()) {
            composeRule.onAllNodesWithContentDescription("Edit", useUnmergedTree = true)[0].performClick()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Edit")[0].performClick()

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).performTextInput("QA Host Updated")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).performTextInput("10.0.2.3")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CONFIRM_BUTTON).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("QA Host Updated").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("QA Host Updated").assertIsDisplayed()
        composeRule.onAllNodesWithText("QA Host").assertCountEquals(0)

        composeRule.onAllNodesWithText("Delete")[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("QA Host Updated").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("QA Host Updated").assertCountEquals(0)
    }

    @Test
    fun addHost_invalidHostAndPort_showsValidationErrors() {
        composeRule.onNodeWithTag(UiTestTags.HOST_ADD_BUTTON).performClick()

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).performTextInput("Invalid Host")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).performTextInput("bad host^")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextInput("70000")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_USERNAME_INPUT).performTextInput("tester")
        selectPasswordAuth()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CONFIRM_BUTTON).performClick()

        composeRule.onAllNodesWithTag(UiTestTags.HOST_DIALOG_ERROR).assertCountEquals(1)
        composeRule.onAllNodesWithText("Enter a valid port between 1 and 65535.").assertCountEquals(1)

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextInput("22")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CONFIRM_BUTTON).performClick()

        composeRule.onAllNodesWithText("Enter a valid hostname or IP address.").assertCountEquals(1)
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CANCEL_BUTTON).performClick()
    }

    private fun selectPasswordAuth() {
        repeat(2) {
            composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_SCROLL).performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_AUTH_FIELD).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.hostDialogAuthOption("PASSWORD"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(UiTestTags.hostDialogAuthOption("PASSWORD"), useUnmergedTree = true)[0]
            .performClick()
    }
}
