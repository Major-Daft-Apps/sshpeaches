package com.majordaftapps.sshpeaches.app.settings

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecuritySettingsTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun setPinThenDisablePinUpdatesSecurityStatus() {
        openSettings()
        revealSecurityControl(UiTestTags.SETTINGS_SET_PIN_BUTTON)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_STATUS_TEXT)
            .assertTextContains("PIN lock not configured.")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_SET_PIN_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_INPUT).performTextInput("2468")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_CONFIRM_INPUT).performTextInput("2468")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_SAVE_BUTTON).performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_STATUS_TEXT)
                    .assertTextContains("PIN lock configured.")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DISABLE_PIN_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DISABLE_PIN_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DISABLE_PIN_CONFIRM).performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_STATUS_TEXT)
                    .assertTextContains("PIN lock not configured.")
                true
            }.getOrDefault(false)
        }
        check(
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_DISABLE_PIN_BUTTON).fetchSemanticsNodes().isEmpty()
        ) {
            "Expected disable PIN button to be removed after clearing the PIN."
        }
    }

    @Test
    fun lockedAppShowsOverlayAndUnlocksWithPin() {
        AppStateSeeder.configurePin(pin = "2468", locked = true)
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(UiTestTags.LOCK_SCREEN_OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.LOCK_SCREEN_PIN_INPUT).performTextInput("2468")
        composeRule.onNodeWithTag(UiTestTags.LOCK_SCREEN_UNLOCK_BUTTON).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.LOCK_SCREEN_OVERLAY).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun biometricPreferenceDisplaysWhenPinConfigured() {
        AppStateSeeder.configurePin(pin = "2468", locked = false)
        AppStateSeeder.configureSettings(biometricLock = true)
        composeRule.activityRule.scenario.recreate()

        openSettings()
        revealSecurityControl(UiTestTags.SETTINGS_BIOMETRIC_SWITCH)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_PIN_STATUS_TEXT)
            .assertTextContains("PIN lock configured.")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BIOMETRIC_SWITCH).assertIsOn()
        check(
            composeRule.onAllNodesWithText("Set a PIN to enable biometric unlock.")
                .fetchSemanticsNodes().isEmpty()
        ) {
            "PIN-required biometric warning should disappear once a PIN is configured."
        }
    }

    private fun openSettings() {
        val alreadyVisible = runCatching {
            composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (!alreadyVisible) {
            composeRule.navigateDrawer(Routes.SETTINGS)
        }
    }

    private fun revealSecurityControl(tag: String) {
        composeRule.revealSettingsControl(tag)
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }
}
