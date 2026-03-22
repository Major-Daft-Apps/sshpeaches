package com.majordaftapps.sshpeaches.app.release

import android.Manifest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.testutil.openDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class AccessibilitySmokeTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawerEntryPointsExposeAccessibleActions() {
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed().assertHasClickAction()
        composeRule.openDrawer()

        composeRule.onNodeWithTag(UiTestTags.DRAWER_QUICK_CONNECT, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HOSTS), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.SETTINGS), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HELP), useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun settingsPermissionEntryPointIsClickable() {
        composeRule.openDrawer()
        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.SETTINGS), useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithText("Manage permissions").assertIsDisplayed().assertHasClickAction()
    }
}
