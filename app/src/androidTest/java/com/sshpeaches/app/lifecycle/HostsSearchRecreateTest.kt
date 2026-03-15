package com.majordaftapps.sshpeaches.app.lifecycle

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostsSearchRecreateTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun hostsSearchFilter_survivesActivityRecreate() {
        AppStateSeeder.seedHost(seedHost("Alpha Lab"))
        AppStateSeeder.seedHost(seedHost("Beta Rack"))

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.HOST_SEARCH_INPUT).performTextInput("Alpha")
        composeRule.onNodeWithText("Alpha Lab").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Rack").assertCountEquals(0)

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Lab").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Rack").assertCountEquals(0)
    }

    private fun seedHost(name: String): HostConnection =
        HostConnection(
            id = "host-${UUID.randomUUID()}",
            name = name,
            host = "127.0.0.1",
            port = 22,
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
}
