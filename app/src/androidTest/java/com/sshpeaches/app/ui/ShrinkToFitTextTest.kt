package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.components.ShrinkToFitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The session top bar's title: long host names shrink a little, then end in an ellipsis. */
@RunWith(AndroidJUnit4::class)
class ShrinkToFitTextTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun layout(text: String): TextLayoutResult {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(200.dp)) {
                    ShrinkToFitText(text, style = MaterialTheme.typography.titleLarge, testTag = "title")
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("title").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    @Test
    fun aShortNameKeepsTheTitleSize() {
        val result = layout("Pi")
        assertEquals(22f, result.layoutInput.style.fontSize.value)
        assertFalse(result.isLineEllipsized(0))
    }

    @Test
    fun aLongerNameShrinksToFit() {
        val result = layout("production-web-server")
        val size = result.layoutInput.style.fontSize.value
        assertTrue("shrunk to $size", size < 22f && size >= 15f)
        assertFalse(result.isLineEllipsized(0))
    }

    @Test
    fun aVeryLongNameStopsShrinkingAndEndsInAnEllipsis() {
        val result = layout("database-replica-eu-west-1.internal.example.com")
        assertEquals(15f, result.layoutInput.style.fontSize.value)
        assertTrue(result.isLineEllipsized(0))
    }
}
