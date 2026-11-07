package com.majordaftapps.sshpeaches.app.session_ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalClipboard
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalClipboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clipboard
        get() = composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before
    fun shortWait() {
        TerminalClipboard.clearAfterMsForTests = 1_500
        // Android 10+ shows the clipboard only to the app with window focus.
        composeRule.waitUntil(5_000) { composeRule.activity.hasWindowFocus() }
    }

    @After
    fun normalWait() {
        TerminalClipboard.clearAfterMsForTests = null
    }

    // Read straight from the test thread: runOnIdle would first wait out TerminalClipboard's pending clear.
    private fun clipText(): String? =
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun aTerminalCopyIsClearedAfterTheWait() {
        onMain { TerminalClipboard.copy(composeRule.activity, "token=abc123") }
        check(clipText() == "token=abc123") { "Copied: ${clipText()}" }
        check(clipboard.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) {
            "The copy isn't marked sensitive"
        }
        Thread.sleep(2_500)
        check(clipText().isNullOrEmpty()) { "Still on the clipboard: ${clipText()}" }
    }

    @Test
    fun somethingCopiedSinceIsLeftAlone() {
        onMain { TerminalClipboard.copy(composeRule.activity, "token=abc123") }
        Thread.sleep(200)
        onMain { clipboard.setPrimaryClip(ClipData.newPlainText("elsewhere", "copied later")) }
        Thread.sleep(2_500)
        check(clipText() == "copied later") { "The later copy became: ${clipText()}" }
    }
}
