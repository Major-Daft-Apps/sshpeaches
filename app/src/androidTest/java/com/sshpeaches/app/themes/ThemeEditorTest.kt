package com.majordaftapps.sshpeaches.app.themes

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeEditorTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createThemeProfile_returnsToThemeEditor() {
        val themeName = "Sunrise QA"

        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_EDITOR).assertIsDisplayed()
        openNewThemeProfileEditor()

        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_NAME_INPUT).performTextReplacement(themeName)
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_SAVE_BUTTON).performClick()

        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_EDITOR).assertIsDisplayed()
                composeRule.onNodeWithText(themeName).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun duplicateThemeCanBecomeDefaultAndDeleteFallsBackToBuiltin() {
        val duplicatedName = "Sunset QA"

        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        openDuplicateThemeProfileEditor(TerminalProfileDefaults.DEFAULT_PROFILE_ID)

        renameThemeAndSave(duplicatedName)

        composeRule.onNodeWithTag(UiTestTags.themeProfileRow(duplicatedName))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.THEME_DEFAULT_FIELD).performScrollTo().performClick()
        composeRule.onNodeWithTag(UiTestTags.themeDefaultOption(duplicatedName), useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_DEFAULT_FIELD).assertTextContains(duplicatedName)

        composeRule.onNodeWithTag(UiTestTags.themeProfileRow(duplicatedName)).performScrollTo()
        composeRule.onNodeWithTag(UiTestTags.themeDeleteByName(duplicatedName))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(UiTestTags.THEME_DELETE_CONFIRM, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.THEME_DELETE_CONFIRM, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onAllNodesWithText(duplicatedName).fetchSemanticsNodes().isEmpty() &&
                    !composeRule.onNodeWithTag(UiTestTags.THEME_DEFAULT_FIELD)
                        .fetchSemanticsNode().config.toString().contains(duplicatedName)
            }.getOrDefault(false)
        }
        check(composeRule.onAllNodesWithText(duplicatedName).fetchSemanticsNodes().isEmpty()) {
            "Expected deleted duplicated theme to be removed from the editor."
        }
        composeRule.onNodeWithTag(UiTestTags.THEME_DEFAULT_FIELD).assertTextContains("Termux")
    }

    @Test
    fun stockThemePickerIncludesAddedBuiltinThemes() {
        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        composeRule.onNodeWithTag(UiTestTags.THEME_DEFAULT_FIELD).performClick()

        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Dracula"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Tango"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("GitHub Light"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("GitHub Dark"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("One Dark"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Gruvbox"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Nord"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Monokai"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Solarized Dark"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("Solarized Light"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.themeDefaultOption("xterm"),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun duplicateThemeNameShowsValidationError() {
        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        openNewThemeProfileEditor()

        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_NAME_INPUT).performTextReplacement("Termux")
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_SAVE_BUTTON).performClick()

        composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_PROFILE_EDITOR).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_ERROR)
            .assertTextContains("Theme name already exists.")
    }

    @Test
    fun createThemeProfile_withCustomFontShowsFontInThemeList() {
        val themeName = "JetBrains QA"

        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        openNewThemeProfileEditor()

        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_FONT_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_FONT_FIELD).performClick()
        composeRule.onNodeWithTag(
            UiTestTags.themeProfileFontOption("JetBrains Mono"),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithText("Apply").performClick()

        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_NAME_INPUT).performTextReplacement(themeName)
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_SAVE_BUTTON).performClick()

        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_EDITOR).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(UiTestTags.themeProfileRow(themeName))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("JetBrains Mono", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun openNewThemeProfileEditor() {
        composeRule.onNodeWithTag(UiTestTags.THEME_CREATE_BUTTON)
            .performScrollTo()
            .performClick()
        waitForThemeProfileEditor()
    }

    private fun openDuplicateThemeProfileEditor(profileId: String) {
        composeRule.onNodeWithTag(UiTestTags.themeDuplicate(profileId))
            .performScrollTo()
            .performClick()
        waitForThemeProfileEditor()
    }

    private fun waitForThemeProfileEditor() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SCREEN_THEME_PROFILE_EDITOR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_PROFILE_EDITOR).assertIsDisplayed()
    }

    private fun renameThemeAndSave(themeName: String) {
        waitForThemeProfileEditor()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_NAME_INPUT).performTextReplacement(themeName)
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithTag(UiTestTags.THEME_PROFILE_SAVE_BUTTON).performClick()
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SCREEN_THEME_EDITOR).assertIsDisplayed()
                composeRule.onNodeWithText(themeName).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
