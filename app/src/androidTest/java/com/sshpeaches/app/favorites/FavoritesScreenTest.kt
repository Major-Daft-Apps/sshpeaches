package com.majordaftapps.sshpeaches.app.home

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    private companion object {
        const val FAVORITES_TEST_PRIVATE_KEY = "favorites-test-private-key"
        const val FAVORITES_TEST_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDfavoritesplaceholder tester-key"
    }

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsSeededFavoritesAcrossSections() {
        AppStateSeeder.seedHost(
            HostConnection(
                id = "fav-host",
                name = "Favorite Host",
                host = "127.0.0.1",
                port = 22,
                username = "tester",
                preferredAuth = AuthMethod.PASSWORD,
                favorite = true
            )
        )
        AppStateSeeder.seedIdentity(
            identity = Identity(
                id = "fav-identity",
                label = "Favorite Identity",
                fingerprint = "SHA256:favoritesmokeidentity",
                username = "favuser",
                createdEpochMillis = System.currentTimeMillis(),
                favorite = true
            ),
            privateKey = FAVORITES_TEST_PRIVATE_KEY,
            publicKey = FAVORITES_TEST_PUBLIC_KEY
        )
        AppStateSeeder.seedPortForward(
            PortForward(
                id = "fav-forward",
                label = "Favorite Forward",
                type = PortForwardType.LOCAL,
                sourcePort = 9000,
                destinationHost = "example.com",
                destinationPort = 443,
                favorite = true
            )
        )
        AppStateSeeder.seedSnippet(
            Snippet(
                id = "fav-snippet",
                title = "Favorite Snippet",
                command = "echo favorites",
                favorite = true
            )
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        assertHomeItemVisible("Favorite Host")
        assertHomeItemVisible("Favorite Identity")
        assertHomeItemVisible("Favorite Forward")
        assertHomeItemVisible("Favorite Snippet")
    }

    @Test
    fun homeScreenShowsWelcomeActionsWhenNoResourcesExist() {
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOME_WELCOME).assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to SSHPeaches. To begin, create your first resource.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOME_WELCOME_ADD_HOST).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOME_WELCOME_ADD_IDENTITY).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOME_WELCOME_ADD_FORWARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOME_WELCOME_ADD_SNIPPET).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.HOME_RECENTS_SECTION).assertCountEquals(0)
    }

    @Test
    fun recentHostOverflowCanOpenEditDialog() {
        AppStateSeeder.seedHost(
            HostConnection(
                id = "recent-host",
                name = "Recent Host",
                host = "10.0.0.5",
                port = 22,
                username = "tester",
                preferredAuth = AuthMethod.PASSWORD,
                createdEpochMillis = System.currentTimeMillis()
            )
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        assertHomeItemVisible("Recent Host")

        val recentKey = "host_recent-host"
        composeRule.onNodeWithTag(UiTestTags.homeRecentOverflowButton(recentKey)).performClick()
        composeRule.onNodeWithTag(UiTestTags.homeRecentOverflowAction(recentKey, "edit")).performClick()

        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_NAME_INPUT).assertTextContains("Recent Host")
    }

    @Test
    fun favoriteIdentityOverflowCanOpenEditDialog() {
        AppStateSeeder.seedIdentity(
            identity = Identity(
                id = "favorite-identity",
                label = "Favorite Identity Overflow",
                fingerprint = "SHA256:favoriteidentityoverflow",
                username = "favuser",
                createdEpochMillis = System.currentTimeMillis(),
                favorite = true
            ),
            privateKey = FAVORITES_TEST_PRIVATE_KEY,
            publicKey = FAVORITES_TEST_PUBLIC_KEY
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        assertHomeItemVisible("Favorite Identity Overflow")

        val favoriteKey = "identity_favorite-identity"
        composeRule.onNodeWithTag(UiTestTags.homeFavoriteOverflowButton(favoriteKey)).performClick()
        composeRule.onNodeWithTag(UiTestTags.homeFavoriteOverflowAction(favoriteKey, "edit")).performClick()

        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_LABEL_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_LABEL_INPUT).assertTextContains("Favorite Identity Overflow")
    }

    private fun assertHomeItemVisible(label: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Expected to find $label somewhere on Home.",
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        )
    }
}
