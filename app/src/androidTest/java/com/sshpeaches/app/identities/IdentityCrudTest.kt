package com.majordaftapps.sshpeaches.app.identities

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityCrudTest {

    private companion object {
        const val TEST_PRIVATE_KEY = "identity-test-private-key"
        const val TEST_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDidentityplaceholder tester-key"
    }

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addGeneratedIdentity_showsInList() {
        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_IDENTITIES).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.IDENTITIES)).performClick()
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_LABEL_INPUT).performTextInput("QA Identity")
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_GENERATE_BUTTON).performClick()
        composeRule.onNodeWithText("Generate").performClick()
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_FINGERPRINT_INPUT).assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("QA Identity").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun editAndDeleteSeededIdentity() {
        AppStateSeeder.seedIdentity(
            identity = Identity(
                id = "seed-identity",
                label = "Seed Identity",
                fingerprint = "SHA256:seedidentityfingerprint",
                username = "seeduser",
                createdEpochMillis = System.currentTimeMillis(),
                hasPrivateKey = true
            ),
            privateKey = TEST_PRIVATE_KEY,
            publicKey = TEST_PUBLIC_KEY
        )
        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithText("Seed Identity").assertIsDisplayed()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(
                UiTestTags.identityOverflowButton("seed-identity"),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(
            UiTestTags.identityOverflowButton("seed-identity"),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithTag(
            UiTestTags.identityOverflowAction("seed-identity", "edit"),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithTag(UiTestTags.IDENTITY_DIALOG_LABEL_INPUT)
            .performTextReplacement("QA Identity Updated")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("QA Identity Updated").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(UiTestTags.IDENTITY_SEARCH_INPUT).performTextInput("Updated")
        composeRule.onNodeWithText("QA Identity Updated").assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.identityOverflowButton("seed-identity"),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithTag(
            UiTestTags.identityOverflowAction("seed-identity", "delete"),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithTag(UiTestTags.DELETE_CONFIRM_BUTTON).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("QA Identity Updated").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("QA Identity Updated").assertCountEquals(0)
    }
}
