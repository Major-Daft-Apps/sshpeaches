package com.majordaftapps.sshpeaches.app.navigation

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInfoNavigationTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @Test
    fun startupRoute_canOpenLicenseNoticesScreen() {
        ActivityScenario.launch<MainActivity>(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.majordaftapps.sshpeaches.debug", MainActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_START_ROUTE, Routes.OPEN_SOURCE_LICENSES)
            }
        ).use {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag(UiTestTags.SCREEN_OPEN_SOURCE_LICENSES, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(UiTestTags.SCREEN_OPEN_SOURCE_LICENSES, useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }
}
