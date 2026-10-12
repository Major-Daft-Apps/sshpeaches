package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import android.view.View
import android.view.ViewGroup
import io.github.rosemoe.sora.widget.CodeEditor
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

    /**
     * YAML, XML and Markdown grammars used to fail to load, so those files had no colors; a shell line tm4e can't parse
     * (an emoji right before a quote) stopped highlighting for the rest of the file.
     */
    @Test
    fun everyBundledGrammarHighlightsAndABadLineDoesNotStopTheRest() {
        val samples = listOf(
            Triple(CodeLanguage.YAML, "apiVersion: v1\nkind: Pod # comment\n", 1),
            Triple(CodeLanguage.XML, "<?xml version=\"1.0\"?>\n<a b=\"c\">t</a>\n", 1),
            Triple(CodeLanguage.MARKDOWN, "# Title\n\nSome *text* and `code`.\n", 2),
            Triple(CodeLanguage.SHELL, "\uD83C\uDF05\"\necho \"hi\" | grep h # done\n", 1)
        )
        var current by mutableStateOf(samples.first())
        composeRule.setContent {
            MaterialTheme {
                key(current) {
                    CodeEditorPane(
                        text = current.second,
                        onTextChange = {},
                        language = current.first,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        samples.forEach { sample ->
            composeRule.runOnIdle { current = sample }
            val (language, _, line) = sample
            runCatching { composeRule.waitUntil(20_000) { colorsOnLine(line) > 1 } }.onFailure {
                throw AssertionError("${language.label}: colors per line ${(0..2).map { colorsOnLine(it) }} spans ${(0..2).map { spansOnLine(it) }}", it)
            }
            composeRule.runOnIdle { check(colorsOnLine(line) > 1) { "${language.label} line $line has no highlighting" } }
        }
    }

    /** How many different foreground colors the editor's highlighting uses on [line]. */
    private fun colorsOnLine(line: Int): Int {
        var count = 0
        composeRule.runOnUiThread {
            val editor = findCodeEditor(composeRule.activity.window.decorView) ?: return@runOnUiThread
            val reader = editor.styles?.spans?.read() ?: return@runOnUiThread
            count = runCatching {
                reader.moveToLine(line)
                (0 until reader.spanCount).map { reader.getSpanAt(it).foregroundColorId }.toSet().size
            }.getOrDefault(0)
        }
        return count
    }

    private fun spansOnLine(line: Int): Int {
        var count = -1
        composeRule.runOnUiThread {
            val reader = findCodeEditor(composeRule.activity.window.decorView)?.styles?.spans?.read() ?: return@runOnUiThread
            count = runCatching { reader.moveToLine(line); reader.spanCount }.getOrDefault(-2)
        }
        return count
    }

    private fun findCodeEditor(view: View): CodeEditor? {
        if (view is CodeEditor) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findCodeEditor(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
