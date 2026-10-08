package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.code.CodeEditorPane
import com.majordaftapps.sshpeaches.app.ui.code.CodeLanguage
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeEditorPaneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun typingSymbolsAndUndoUpdateTheText() {
        var text by mutableStateOf("ls -la")
        composeRule.setContent {
            MaterialTheme {
                CodeEditorPane(
                    text = text,
                    onTextChange = { text = it },
                    language = CodeLanguage.SHELL,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Grammars load in the background on first use.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(UiTestTags.CODE_EDITOR).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(UiTestTags.CODE_EDITOR).performTextInput(" ")
        composeRule.onNodeWithTag(UiTestTags.codeEditorSymbol("|")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CODE_EDITOR).performTextInput(" grep ")
        composeRule.onNodeWithTag(UiTestTags.codeEditorSymbol("$")).performClick()
        // Like VS Code, the cursor starts at the beginning of the file.
        composeRule.runOnIdle { check(text == " | grep \$ls -la") { "text was \"$text\"" } }
        composeRule.onNodeWithTag(UiTestTags.CODE_EDITOR).assertTextEquals(text)

        composeRule.onNodeWithTag(UiTestTags.CODE_EDITOR_UNDO).assertIsEnabled().performClick()
        // Quick consecutive edits undo as one step, as in VS Code.
        composeRule.runOnIdle { check(text == "ls -la") { "undo left \"$text\"" } }
        composeRule.onNodeWithTag(UiTestTags.CODE_EDITOR_REDO).assertIsEnabled().performClick()
        composeRule.runOnIdle { check(text == " | grep \$ls -la") { "redo left \"$text\"" } }
    }
}
