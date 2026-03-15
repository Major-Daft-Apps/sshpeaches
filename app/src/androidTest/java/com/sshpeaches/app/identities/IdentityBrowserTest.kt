package com.majordaftapps.sshpeaches.app.identities

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityBrowserTest {

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
        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_IDENTITIES).assertIsDisplayed()
    }

    @Test
    fun searchFavoriteAndQrShareWorkForIdentity() {
        val target = Identity(
            id = "identity-favorite",
            label = "Favorite Identity",
            fingerprint = "SHA256:favoriteidentityfingerprint",
            username = "favorite-user",
            createdEpochMillis = System.currentTimeMillis()
        )
        val other = Identity(
            id = "identity-other",
            label = "Other Identity",
            fingerprint = "SHA256:otheridentityfingerprint",
            username = "other-user",
            createdEpochMillis = System.currentTimeMillis()
        )
        AppStateSeeder.seedIdentityRecord(target)
        AppStateSeeder.seedIdentityRecord(other)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.IDENTITIES)

        waitForIdentity("Favorite Identity")
        waitForIdentity("Other Identity")

        composeRule.onNodeWithTag(UiTestTags.IDENTITY_SEARCH_INPUT)
            .performTextReplacement("favorite-user")
        composeRule.onNodeWithText("Favorite Identity").assertIsDisplayed()
        composeRule.onAllNodesWithText("Other Identity").assertCountEquals(0)

        composeRule.onNodeWithTag(UiTestTags.identityFavorite(target.id)).performClick()

        composeRule.onNodeWithTag(UiTestTags.identityShare(target.id)).performClick()
        composeRule.onNodeWithText("Share Favorite Identity").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.navigateDrawer(Routes.FAVORITES)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_FAVORITES).assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Favorite Identity").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Favorite Identity").assertIsDisplayed()
        assertTrue(
            "Favorites screen should not surface the unfavorited identity.",
            composeRule.onAllNodesWithText("Other Identity").fetchSemanticsNodes().isEmpty()
        )
    }

    private fun waitForIdentity(label: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
