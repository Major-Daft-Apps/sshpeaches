package com.majordaftapps.sshpeaches.app.session_ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.components.SessionOverflowMenu
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SessionOverflowMenuTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selected = AtomicReference<String?>(null)
    private val invoked = mutableListOf<String>()

    private fun setMenu() {
        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.DARK) {
                SessionOverflowMenu(
                    profiles = listOf(
                        TerminalProfile(id = "profile-alpha", name = "Alpha"),
                        TerminalProfile(id = "profile-beta", name = "Beta")
                    ),
                    selectedProfileId = "profile-alpha",
                    onSelectProfile = { selected.set(it) },
                    arrowKeysEnabled = false,
                    onToggleArrowKeys = { invoked += "arrows" },
                    onInsertPassword = { invoked += "password" },
                    onFind = { invoked += "find" },
                    onSnippets = { invoked += "snippets" },
                    onReset = { invoked += "reset" }
                )
            }
        }
    }

    @Test
    fun changeThemeOpensProfileListAndSelectingInvokesCallback() {
        setMenu()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_MENU_BUTTON).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_THEME_BUTTON, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(
            UiTestTags.connectingThemeItem("profile-beta"),
            useUnmergedTree = true
        ).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals("profile-beta", selected.get())
        }
    }

    @Test
    fun menuItemsInvokeTheirActions() {
        setMenu()

        listOf(
            UiTestTags.CONNECTING_ARROW_KEYS_BUTTON,
            UiTestTags.CONNECTING_INSERT_PASSWORD_BUTTON,
            UiTestTags.CONNECTING_FIND_BUTTON,
            UiTestTags.CONNECTING_SNIPPETS_BUTTON,
            UiTestTags.CONNECTING_RESET_BUTTON
        ).forEach { tag ->
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_MENU_BUTTON).performClick()
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().performClick()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("arrows", "password", "find", "snippets", "reset"), invoked)
        }
    }
}
