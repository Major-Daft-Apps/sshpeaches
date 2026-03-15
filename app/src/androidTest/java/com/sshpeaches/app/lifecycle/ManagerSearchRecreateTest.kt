package com.majordaftapps.sshpeaches.app.lifecycle

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagerSearchRecreateTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun identitiesSearchFilter_survivesActivityRecreate() {
        AppStateSeeder.seedIdentityRecord(
            Identity(
                id = "identity-alpha",
                label = "Alpha Identity",
                fingerprint = "SHA256:alpha",
                username = "alpha-user",
                createdEpochMillis = System.currentTimeMillis()
            )
        )
        AppStateSeeder.seedIdentityRecord(
            Identity(
                id = "identity-beta",
                label = "Beta Identity",
                fingerprint = "SHA256:beta",
                username = "beta-user",
                createdEpochMillis = System.currentTimeMillis()
            )
        )

        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_SEARCH_INPUT).performTextReplacement("alpha-user")
        composeRule.onNodeWithText("Alpha Identity").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Identity").assertCountEquals(0)

        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_IDENTITIES).assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Identity").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Identity").assertCountEquals(0)
    }

    @Test
    fun forwardsSearchFilter_survivesActivityRecreate() {
        AppStateSeeder.seedPortForward(
            PortForward(
                id = "forward-alpha",
                label = "Alpha Forward",
                type = PortForwardType.LOCAL,
                sourceHost = "127.0.0.1",
                sourcePort = 8080,
                destinationHost = "alpha.internal",
                destinationPort = 443
            )
        )
        AppStateSeeder.seedPortForward(
            PortForward(
                id = "forward-beta",
                label = "Beta Forward",
                type = PortForwardType.LOCAL,
                sourceHost = "127.0.0.1",
                sourcePort = 8081,
                destinationHost = "beta.internal",
                destinationPort = 443
            )
        )

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithTag(UiTestTags.FORWARD_SEARCH_INPUT).performTextReplacement("alpha.internal")
        composeRule.onNodeWithText("Alpha Forward").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Forward").assertCountEquals(0)

        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FORWARDS).assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Forward").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Forward").assertCountEquals(0)
    }

    @Test
    fun snippetsSearchFilter_survivesActivityRecreate() {
        AppStateSeeder.seedSnippet(
            Snippet(
                id = "snippet-alpha",
                title = "Alpha Snippet",
                description = "first",
                command = "echo alpha"
            )
        )
        AppStateSeeder.seedSnippet(
            Snippet(
                id = "snippet-beta",
                title = "Beta Snippet",
                description = "second",
                command = "echo beta"
            )
        )

        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_SEARCH_INPUT).performTextReplacement("Alpha")
        composeRule.onNodeWithText("Alpha Snippet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Snippet").assertCountEquals(0)

        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Snippet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Snippet").assertCountEquals(0)
    }
}
