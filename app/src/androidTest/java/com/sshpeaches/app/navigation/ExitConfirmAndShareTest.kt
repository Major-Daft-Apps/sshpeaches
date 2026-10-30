package com.majordaftapps.sshpeaches.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** From the testers' report: Back on Home asks before leaving; Settings can rate and share the app. */
@RunWith(AndroidJUnit4::class)
class ExitConfirmAndShareTest {
    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backOnHomeAsksBeforeLeaving() {
        composeRule.waitForIdle()
        Espresso.pressBackUnconditionally()
        composeRule.onNodeWithTag(UiTestTags.EXIT_CONFIRM_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Leave SSHPeaches?").assertIsDisplayed()
        composeRule.onNodeWithText("Stay").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.EXIT_CONFIRM_DIALOG).fetchSemanticsNodes().isEmpty()
        }
        assertFalse("the app closed", composeRule.activity.isFinishing)
    }

    @Test
    fun settingsCanRateAndShareTheApp() {
        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_RATE_APP)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_RATE_APP).assertIsDisplayed()
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_SHARE_APP, categoryTitle = null)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SHARE_APP).assertIsDisplayed()
    }
}
