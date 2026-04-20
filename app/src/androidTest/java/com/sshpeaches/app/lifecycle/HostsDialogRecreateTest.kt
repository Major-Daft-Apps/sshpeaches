package com.majordaftapps.sshpeaches.app.lifecycle

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostsDialogRecreateTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addHostDialog_preservesInputAcrossRecreate() {
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.HOSTS)).performClick()

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).performTextInput("Rotate Me")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).performTextInput("192.168.1.44")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).performTextInput("2202")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_USERNAME_INPUT).performTextInput("battery")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).assertTextContains("Rotate Me")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_HOST_INPUT).assertTextContains("192.168.1.44")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_PORT_INPUT).assertTextContains("2202")
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_USERNAME_INPUT).assertTextContains("battery")
    }
}
