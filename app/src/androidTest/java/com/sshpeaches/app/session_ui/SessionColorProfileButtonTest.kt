package com.majordaftapps.sshpeaches.app.session_ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.components.SessionColorProfileButton
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SessionColorProfileButtonTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingProfileFromMenuInvokesCallback() {
        val profiles = listOf(
            TerminalProfile(id = "profile-alpha", name = "Alpha"),
            TerminalProfile(id = "profile-beta", name = "Beta")
        )
        val selected = AtomicReference<String?>(null)

        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.DARK) {
                SessionColorProfileButton(
                    profiles = profiles,
                    selectedProfileId = "profile-alpha",
                    onSelectProfile = { selected.set(it) }
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_THEME_BUTTON).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(
            UiTestTags.connectingThemeItem("profile-beta"),
            useUnmergedTree = true
        ).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals("profile-beta", selected.get())
        }
    }
}
