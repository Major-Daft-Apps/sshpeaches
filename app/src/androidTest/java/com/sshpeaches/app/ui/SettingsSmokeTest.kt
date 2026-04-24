package com.majordaftapps.sshpeaches.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {

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
        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun toggleSettingsAndDropdowns_reflectsChanges() {
        AppStateSeeder.configureSettings(allowBackgroundSessions = true)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.SETTINGS)

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BACKGROUND_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).assertIsOff()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).assertIsOff()

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_THEME_MODE_FIELD)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_THEME_MODE_FIELD).performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_THEME_MODE_FIELD).assertTextContains("Dark")

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH)
        val diagnosticsInitiallyOn = runCatching {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
            true
        }.getOrDefault(false)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                if (diagnosticsInitiallyOn) {
                    composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
                } else {
                    composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
                }
                true
            }.getOrDefault(false)
        }
        if (diagnosticsInitiallyOn) {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
        } else {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
        }
    }

    @Test
    fun exportQrDialogShowsPassphraseFieldsWhenSecretsEnabled() {
        AppStateSeeder.configureSettings(includeSecretsInQr = true)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
        composeRule.onNodeWithText("Export via QR").performClick()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_INCLUDE_SECRETS_SWITCH).assertIsOn()
        composeRule.onNodeWithText("Export passphrase").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm passphrase").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }
}
