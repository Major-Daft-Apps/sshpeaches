package com.majordaftapps.sshpeaches.app.testutil
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

typealias MainActivityComposeRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

fun MainActivityComposeRule.resetAppState() {
    AppStateResetter.reset(activity.applicationContext)
    activityRule.scenario.recreate()
    waitForIdle()
}

fun MainActivityComposeRule.openDrawer() {
    val drawerMatcher = hasTestTag(UiTestTags.DRAWER_SCROLL_CONTAINER)
    if (runCatching {
            onNode(drawerMatcher, useUnmergedTree = true).assertIsDisplayed()
        }.isSuccess
    ) {
        return
    }
    onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
    waitForIdle()
}

fun MainActivityComposeRule.navigateDrawer(route: String) {
    openDrawer()
    revealDrawerNode(UiTestTags.drawerItem(route))
    onNodeWithTag(UiTestTags.drawerItem(route), useUnmergedTree = true)
        .performClick()
    waitForIdle()
}

fun MainActivityComposeRule.openQuickConnect() {
    onNodeWithTag(UiTestTags.DRAWER_QUICK_CONNECT)
        .assertIsDisplayed()
        .performClick()
    waitUntil(15_000) {
        runCatching {
            onNodeWithTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON).assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
}

fun MainActivityComposeRule.openSettingsCategory(categoryTitle: String) {
    val categoryTag = UiTestTags.settingsCategory(categoryTitle)
    val categoryExists = onAllNodesWithTag(categoryTag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    if (!categoryExists) return

    onNodeWithTag(categoryTag, useUnmergedTree = true)
        .assertIsDisplayed()
        .performClick()
    waitForIdle()
}

fun MainActivityComposeRule.revealSettingsControl(
    tag: String,
    categoryTitle: String? = settingsCategoryForControl(tag)
) {
    categoryTitle?.let(::openSettingsCategory)
    waitForIdle()
    repeat(16) {
        val revealed = runCatching {
            onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            waitForIdle()
            true
        }.getOrDefault(false)
        val visible = runCatching {
            onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (revealed && visible) return
        if (visible) return
        runCatching {
            onNodeWithTag(UiTestTags.SETTINGS_SCROLL_CONTAINER, useUnmergedTree = true)
                .performTouchInput { swipeUp() }
        }.getOrElse {
            onNodeWithTag(UiTestTags.SCREEN_SETTINGS, useUnmergedTree = true)
                .performTouchInput { swipeUp() }
        }
        waitForIdle()
    }
    onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
}

private fun MainActivityComposeRule.revealDrawerNode(tag: String) {
    val target = onNodeWithTag(tag, useUnmergedTree = true)
    runCatching {
        target.performScrollTo()
        waitForIdle()
        target.assertIsDisplayed()
        return
    }
    repeat(5) {
        val shown = runCatching {
            target.assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (shown) {
            return
        }
        onNodeWithTag(UiTestTags.DRAWER_SCROLL_CONTAINER, useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        waitForIdle()
    }
    target.assertIsDisplayed()
}

private fun settingsCategoryForControl(tag: String): String? = when (tag) {
    UiTestTags.SETTINGS_THEME_MODE_FIELD -> "Appearance"
    UiTestTags.SETTINGS_BACKGROUND_SWITCH -> "Background"
    UiTestTags.SETTINGS_TERMINAL_EMULATION_FIELD,
    UiTestTags.SETTINGS_TERMINAL_BELL_FIELD,
    UiTestTags.SETTINGS_TERMINAL_VOLUME_BUTTONS_SWITCH,
    UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT,
    UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT -> "Terminal"
    UiTestTags.SETTINGS_BIOMETRIC_SWITCH,
    UiTestTags.SETTINGS_PIN_STATUS_TEXT,
    UiTestTags.SETTINGS_SET_PIN_BUTTON,
    UiTestTags.SETTINGS_DISABLE_PIN_BUTTON,
    UiTestTags.SETTINGS_HOST_KEY_PROMPT_SWITCH,
    UiTestTags.SETTINGS_AUTO_TRUST_HOST_KEY_SWITCH -> "Security"
    UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH -> "Diagnostics"
    UiTestTags.SETTINGS_EXPORT_QR_BUTTON -> "Transfer / QR"
    UiTestTags.SETTINGS_RESTORE_DEFAULTS_BUTTON -> "Advanced"
    else -> null
}
