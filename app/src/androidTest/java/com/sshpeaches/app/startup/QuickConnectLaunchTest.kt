package com.majordaftapps.sshpeaches.app.startup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.ui.QuickConnectSheet
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QuickConnectLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setQuickConnectContent(
        onConnect: (String, Int, String, AuthMethod, String, Boolean, Boolean, String?, String?, String, String?) -> Unit =
            { _, _, _, _, _, _, _, _, _, _, _ -> }
    ) {
        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.DARK) {
                QuickConnectSheet(
                    onDismiss = {},
                    portForwards = emptyList(),
                    identities = emptyList(),
                    snippets = emptyList(),
                    terminalProfiles = TerminalProfileDefaults.builtInProfiles,
                    defaultTerminalProfileId = TerminalProfileDefaults.DEFAULT_PROFILE_ID,
                    onConnect = onConnect
                )
            }
        }
    }

    @Test
    fun quickConnectRequiresHostPortAndUsername() {
        setQuickConnectContent()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON).performClick()
        composeRule.onNodeWithText("Host, port, and username required").assertIsDisplayed()
    }

    @Test
    fun quickConnectRejectsInvalidPort() {
        val attemptedPort = AtomicReference<Int?>(null)
        setQuickConnectContent { _, port, _, _, _, _, _, _, _, _, _ ->
            attemptedPort.set(port)
        }
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_HOST_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_HOST_INPUT).performTextReplacement("127.0.0.1")
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_PORT_INPUT).performScrollTo().performTextReplacement("70000")
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_USERNAME_INPUT).performScrollTo().performTextReplacement("tester")
        composeRule.onNodeWithTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON).performScrollTo().performClick()

        composeRule.runOnIdle {
            check(attemptedPort.get() == null) {
                "Quick connect should reject invalid ports before invoking onConnect, but called with ${attemptedPort.get()}."
            }
        }
    }
}
