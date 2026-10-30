package com.majordaftapps.sshpeaches.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferSettings
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoostSettingsTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun stored() = runBlocking { SettingsStore.sftpTransferSettings.first() }

    private fun waitForStored(condition: (SftpTransferSettings) -> Boolean) {
        composeRule.waitUntil(5_000) { condition(stored()) }
    }

    @Test
    fun boostSettingsExplainThemselvesAndPersist() {
        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_SWITCH)
        // On by default.
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_SWITCH).assertIsOn()

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_INFO_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_INFO_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("60000-61000", substring = true).assertExists()
        composeRule.onNodeWithText("Mosh without installing anything", substring = true).assertExists()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_BOOST_INFO_DIALOG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_SWITCH).performClick()
        waitForStored { !it.boost }
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_SWITCH).assertIsOff()

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH).performClick()
        waitForStored { !it.boostTerminal }

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH).performClick()
        waitForStored { !it.boostTcpFallback }

        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_PORTS_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).assertTextContains("60000-61000")
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).performTextInput("4500-4510")
        waitForStored { it.boostPorts == "4500-4510" }
        // 4500-451099 isn't a valid range: shown as an error and not saved.
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).performTextInput("99")
        composeRule.onNodeWithText("between 1024 and 65535", substring = true).assertExists()
        composeRule.waitForIdle()
        assertEquals("4500-4510", stored().boostPorts)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).performTextInput("4500-4510")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // Still on Advanced settings: revealing the switch goes back to Settings.
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_SWITCH).assertIsOff()
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH).assertIsOff()
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH).assertIsOff()
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_BOOST_PORTS_INPUT)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT).assertTextContains("4500-4510")
    }
}
