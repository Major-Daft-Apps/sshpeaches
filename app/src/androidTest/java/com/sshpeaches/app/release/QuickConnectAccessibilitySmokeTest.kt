package com.majordaftapps.sshpeaches.app.release

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.ui.QuickConnectSheet
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.ReleaseTestHostActivity
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class QuickConnectAccessibilitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ReleaseTestHostActivity>()

    @Test
    fun quickConnectControlsRemainFocusableAndClickable() {
        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.DARK) {
                QuickConnectSheet(
                    onDismiss = {},
                    portForwards = emptyList(),
                    identities = emptyList(),
                    snippets = emptyList(),
                    terminalProfiles = TerminalProfileDefaults.builtInProfiles,
                    defaultTerminalProfileId = TerminalProfileDefaults.DEFAULT_PROFILE_ID,
                    onConnect = { _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_HOST_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_PORT_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_USERNAME_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_PASSWORD_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
