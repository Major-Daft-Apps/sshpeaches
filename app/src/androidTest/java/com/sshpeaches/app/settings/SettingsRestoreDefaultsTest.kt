package com.majordaftapps.sshpeaches.app.settings

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRestoreDefaultsTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun restoreDefaultsResetsSecurityToggles() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = true,
            autoTrustHostKey = true,
            diagnostics = true
        )
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.SETTINGS), useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(20_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_HOST_KEY_PROMPT_SWITCH).assertIsOn()
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_AUTO_TRUST_HOST_KEY_SWITCH).assertIsOn()
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
                true
            }.getOrDefault(false)
        }

        repeat(8) {
            val buttonVisible = runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
            if (buttonVisible) return@repeat
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_SCROLL_CONTAINER)
                .performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_BUTTON).performClick()
        composeRule.waitUntil(20_000) {
            composeRule
                .onAllNodesWithTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_CONFIRM)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_CONFIRM)
            .performClick()

        composeRule.waitUntil(20_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_HOST_KEY_PROMPT_SWITCH).assertIsOn()
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_AUTO_TRUST_HOST_KEY_SWITCH).assertIsOff()
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_HOST_KEY_PROMPT_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_AUTO_TRUST_HOST_KEY_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
    }
}
