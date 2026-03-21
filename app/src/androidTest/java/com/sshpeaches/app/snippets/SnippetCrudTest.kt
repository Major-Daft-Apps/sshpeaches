package com.majordaftapps.sshpeaches.app.snippets

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetCrudTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addEditSearchAndDeleteSnippet() {
        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.SNIPPETS)).performClick()
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPET_EDITOR).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_TITLE_INPUT).performTextInput("QA Snippet")
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_DESCRIPTION_INPUT).performTextInput("Smoke coverage")
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT).performTextInput("echo hello")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()
        composeRule.onNodeWithText("QA Snippet").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("More actions", useUnmergedTree = true)[0]
            .performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_TITLE_INPUT).performTextReplacement("QA Snippet Updated")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("QA Snippet Updated").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.SNIPPET_SEARCH_INPUT).performTextInput("Updated")
        composeRule.onNodeWithText("QA Snippet Updated").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("More actions", useUnmergedTree = true)[0]
            .performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithTag(UiTestTags.DELETE_CONFIRM_BUTTON).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("QA Snippet Updated").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("QA Snippet Updated").assertCountEquals(0)
    }
}
