package com.majordaftapps.sshpeaches.app.keyboard

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.recreateActivity
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardEditorTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun keyboardEditor_hasTwoEditableRowsAndMainFnSlotOpensTheEditor() {
        composeRule.navigateDrawer(Routes.KEYBOARD)

        composeRule.onNodeWithText("Fn").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(13)).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(14)).assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(1)).assertContentDescriptionEquals("Alt")
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(6))
            .assertContentDescriptionEquals("Fn")
            .performClick()

        composeRule.onNodeWithText("Edit Key Action").assertIsDisplayed()
        composeRule.onNodeWithText("Current: Fn").assertIsDisplayed()
    }

    @Test
    fun keyboardEditor_canAssignFnToAnotherMainRowSlot() {
        composeRule.navigateDrawer(Routes.KEYBOARD)

        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).performClick()
        composeRule.onNodeWithText("Modifiers").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.KEYBOARD_EDITOR_FN_BUTTON).assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0))
            .assertContentDescriptionEquals("Fn")
    }

    @Test
    fun keyboardEditor_offersTheCuratedGroupsAndAssignsShortcutsAndCustomText() {
        composeRule.navigateDrawer(Routes.KEYBOARD)

        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(2)).performClick()
        listOf("Keys", "Shortcuts", "Symbols", "Actions", "Custom text").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        // Letters, the numpad and lock keys are no longer offered.
        composeRule.onNodeWithText("Letters").assertDoesNotExist()
        composeRule.onNodeWithText("Numpad").assertDoesNotExist()
        composeRule.onNodeWithText("CapsLk").assertDoesNotExist()
        composeRule.onNodeWithText("Ctrl-R").performScrollTo().performClick()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(2)).assertContentDescriptionEquals("Ctrl-R")

        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(4)).performClick()
        composeRule.onNodeWithTag(UiTestTags.KEYBOARD_EDITOR_TEXT_INPUT).performScrollTo().performTextInput("uptime")
        composeRule.onNodeWithTag(UiTestTags.KEYBOARD_EDITOR_PRESS_ENTER).performScrollTo().performClick()
        composeRule.onNodeWithTag(UiTestTags.KEYBOARD_EDITOR_USE_TEXT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(4)).assertContentDescriptionEquals("uptime")
        composeRule.waitUntil(5_000) {
            AppStateSeeder.keyboardLayout().getOrNull(4)?.text == "uptime\r"
        }
    }

    @Test
    fun keyboardSlotPersistsAcrossRecreate() {
        val customLayout = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.textAction("ls -la")
        }
        AppStateSeeder.seedKeyboardLayout(customLayout)
        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.KEYBOARD)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_KEYBOARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).assertContentDescriptionEquals("ls -la")

        composeRule.recreateActivity()
        composeRule.navigateDrawer(Routes.KEYBOARD)
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).assertContentDescriptionEquals("ls -la")
    }
}
