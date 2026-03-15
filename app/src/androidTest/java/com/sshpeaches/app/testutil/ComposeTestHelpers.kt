package com.majordaftapps.sshpeaches.app.testutil
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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
    val quickConnectMatcher = hasTestTag(UiTestTags.DRAWER_QUICK_CONNECT)
    if (runCatching {
            onNode(quickConnectMatcher, useUnmergedTree = true).assertIsDisplayed()
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
    openDrawer()
    revealDrawerNode(UiTestTags.DRAWER_QUICK_CONNECT)
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
