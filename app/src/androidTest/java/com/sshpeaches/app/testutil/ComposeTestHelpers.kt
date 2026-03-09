package com.majordaftapps.sshpeaches.app.testutil

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
    waitForIdle()
}

fun MainActivityComposeRule.navigateDrawer(route: String) {
    openDrawer()
    onNodeWithTag(UiTestTags.drawerItem(route), useUnmergedTree = true)
        .assertIsDisplayed()
        .performClick()
    waitForIdle()
}
