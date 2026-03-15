package com.majordaftapps.sshpeaches.app.favorites

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {

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
    fun favoritesScreenShowsSeededFavoritesAcrossSections() {
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
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FAVORITES).assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Host").assertTextContains("Favorite Host")
        composeRule.onNodeWithText("Favorite Identity").assertTextContains("Favorite Identity")
        composeRule.onNodeWithText("Favorite Forward").assertTextContains("Favorite Forward")
        composeRule.onNodeWithText("Favorite Snippet").assertTextContains("Favorite Snippet")
    }
}
