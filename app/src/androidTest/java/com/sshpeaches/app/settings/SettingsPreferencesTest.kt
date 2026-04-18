package com.majordaftapps.sshpeaches.app.settings

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.state.TerminalBellMode
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun themeModeAndTogglesPersistAcrossRecreate() {
        AppStateSeeder.configureSettings(
            themeMode = ThemeMode.DARK,
            appIcon = AppIconOption.PEACH_LIGHT
        )
        composeRule.activityRule.scenario.recreate()
        openSettings()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_THEME_MODE_FIELD).assertTextContains("Dark")
        composeRule.onNodeWithTag(UiTestTags.settingsAppIconOption("Orange Peach")).assertIsSelected()
        composeRule.onNodeWithTag(UiTestTags.settingsAppIconOption("White Peach")).performClick()
        composeRule.onNodeWithTag(UiTestTags.settingsAppIconOption("White Peach")).assertIsSelected()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).performClick()
        scrollToTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT).performTextInput("16")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT).assertTextContains("16")
        scrollToTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
            .performTextInput("mosh-server new -s -l LANG=C.UTF-8")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
            .assertTextContains("mosh-server new -s -l LANG=C.UTF-8")

        scrollToTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH)
        val diagnosticsInitiallyOn = SettingsStore.defaultDiagnosticsEnabled
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).performClick()
        if (diagnosticsInitiallyOn) {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
        } else {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_THEME_MODE_FIELD).assertTextContains("Dark")
        composeRule.onNodeWithTag(UiTestTags.settingsAppIconOption("White Peach")).assertIsSelected()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH).assertIsOff()
        scrollToTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT).assertTextContains("16")
        scrollToTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
            .assertTextContains("mosh-server new -s -l LANG=C.UTF-8")
        scrollToTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH)
        if (diagnosticsInitiallyOn) {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOff()
        } else {
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH).assertIsOn()
        }
    }

    @Test
    fun exportQrWithoutSecrets_generatesQrDialog() {
        AppStateSeeder.configureSettings(includeSecretsInQr = false)
        composeRule.activityRule.scenario.recreate()

        openSettings()
        scrollToTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_EXPORT_DIALOG)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_INCLUDE_SECRETS_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_EXPORT_QR_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_QR_DIALOG).assertIsDisplayed()
    }

    @Test
    fun exportQrWithSecretsValidatesPassphraseBeforeGenerating() {
        AppStateSeeder.configureSettings(includeSecretsInQr = true)
        composeRule.activityRule.scenario.recreate()

        openSettings()
        scrollToTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_EXPORT_DIALOG)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_INCLUDE_SECRETS_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_PASSPHRASE_INPUT).performTextInput("abc")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT).performTextInput("abc")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_ERROR)
            .assertTextContains(
                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
            )

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_PASSPHRASE_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_PASSPHRASE_INPUT).performTextInput("peaches-1234")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT)
            .performTextInput("mismatch-123")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_ERROR)
            .assertTextContains("Passphrases do not match.")

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT)
            .performTextInput("peaches-1234")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON)
                .fetchSemanticsNodes().isEmpty()
        }
        check(
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON)
                .fetchSemanticsNodes().isEmpty()
        ) {
            "Expected export dialog to close after a valid protected export."
        }
    }

    @Test
    fun exportQrDialog_preservesEntriesAcrossRecreate() {
        AppStateSeeder.configureSettings(includeSecretsInQr = true)
        composeRule.activityRule.scenario.recreate()

        openSettings()
        scrollToTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_EXPORT_DIALOG)

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_PASSPHRASE_INPUT)
            .performTextInput("restore-pass")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT)
            .performTextInput("restore-pass")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_EXPORT_DIALOG)
                .fetchSemanticsNodes().isEmpty()
        }
        check(
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_EXPORT_DIALOG)
                .fetchSemanticsNodes().isEmpty()
        ) {
            "Expected the recreated export dialog to retain valid passphrases and close after generate."
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

    private fun scrollToTag(tag: String) {
        composeRule.waitForIdle()
        repeat(16) {
            val revealed = runCatching {
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                composeRule.waitForIdle()
                true
            }.getOrDefault(false)
            val visible = runCatching {
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
                true
            }.getOrDefault(false)
            if (revealed && visible) return
            if (visible) return
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SETTINGS_SCROLL_CONTAINER, useUnmergedTree = true)
                    .performTouchInput { swipeUp() }
            }.getOrElse {
                composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS, useUnmergedTree = true)
                    .performTouchInput { swipeUp() }
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
