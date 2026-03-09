package com.majordaftapps.sshpeaches.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeNavigationTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.waitForIdle()
    }

    @Test
    fun drawerNavigation_showsAllCoreScreens() {
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FAVORITES).assertIsDisplayed()

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()

        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_IDENTITIES).assertIsDisplayed()

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FORWARDS).assertIsDisplayed()

        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()

        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SETTINGS).assertIsDisplayed()
    }
}
