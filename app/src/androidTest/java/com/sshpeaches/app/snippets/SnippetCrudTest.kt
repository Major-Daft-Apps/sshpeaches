package com.majordaftapps.sshpeaches.app.snippets

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
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
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_SAVE_BUTTON)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.SCREEN_SNIPPETS).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.snippetRow("QA Snippet"))
            .performScrollTo()
            .assertIsDisplayed()

        openSnippetOverflow("QA Snippet")
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_TITLE_INPUT).performTextReplacement("QA Snippet Updated")
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_SAVE_BUTTON)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.snippetRow("QA Snippet Updated"))
                    .performScrollTo()
                    .assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.snippetRow("QA Snippet Updated")).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.SNIPPET_SEARCH_INPUT).performTextInput("Updated")
        composeRule.onNodeWithTag(UiTestTags.snippetRow("QA Snippet Updated")).assertIsDisplayed()

        openSnippetOverflow("QA Snippet Updated")
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithTag(UiTestTags.DELETE_CONFIRM_BUTTON).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("QA Snippet Updated").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("QA Snippet Updated").assertCountEquals(0)
    }

    private fun openSnippetOverflow(title: String) {
        composeRule.onNodeWithTag(UiTestTags.snippetRow(title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("More actions") and hasAnyAncestor(hasTestTag(UiTestTags.snippetRow(title))),
            useUnmergedTree = true
        ).performClick()
    }
}
