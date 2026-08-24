package com.majordaftapps.sshpeaches.app.session_ui

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import com.majordaftapps.sshpeaches.app.service.FileTransferDirection
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.screens.ConnectingScreen
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectPhase
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectRequest
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectUiState
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.termux.view.TerminalView
import java.nio.charset.StandardCharsets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun View.findEditTextDescendant(): EditText? {
    if (this is EditText) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        val match = getChildAt(index).findEditTextDescendant()
        if (match != null) return match
    }
    return null
}

@RunWith(AndroidJUnit4::class)
class ConnectingScreenTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun terminalTypeface_isRestoredWhenAppReturnsToForeground() {
        val profile =
            TerminalProfileDefaults.builtInProfiles.first().copy(font = TerminalFont.CASCADIA_CODE_MONO)
        composeRule.setContent {
            MaterialTheme {
                SshTerminalForInputTest(onSendShellBytes = {}, terminalProfile = profile)
            }
        }
        composeRule.waitForIdle()

        lateinit var expectedTypeface: Typeface
        composeRule.runOnIdle {
            val terminalView = terminalViewForTest()
            expectedTypeface = terminalView.rendererTypefaceForTest()
            check(expectedTypeface !== Typeface.MONOSPACE) {
                "Bundled terminal typeface was not applied before backgrounding"
            }
            terminalView.setTypeface(Typeface.MONOSPACE)
            check(terminalView.rendererTypefaceForTest() == Typeface.MONOSPACE) {
                "Test did not simulate the renderer typeface reset"
            }
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(terminalViewForTest().rendererTypefaceForTest() === expectedTypeface) {
                "Terminal typeface was not restored after returning to the foreground"
            }
        }
    }

    @Test
    fun terminalIme_fastCommittedTextBurstDoesNotLoseKeystrokes() {
        val sentPayloads = mutableListOf<ByteArray>()
        val chunks = List(128) { index ->
            when (index % 4) {
                0 -> "cmd-$index "
                1 -> "café "
                2 -> "中文 "
                else -> "line-$index\n"
            }
        }

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.runOnIdle {
            val root = composeRule.activity.findViewById<View>(android.R.id.content)
            val imeBridge = root.findEditTextDescendant()
                ?: error("Terminal IME bridge was not attached")
            val inputConnection = imeBridge.onCreateInputConnection(EditorInfo())
                ?: error("Terminal IME bridge did not create an InputConnection")

            chunks.forEach { chunk ->
                check(inputConnection.commitText(chunk, 1)) {
                    "IME rejected committed chunk: $chunk"
                }
            }
            check(inputConnection.deleteSurroundingText(3, 0)) {
                "IME rejected repeated backspace"
            }
        }

        composeRule.runOnIdle {
            val expected = chunks.joinToString(separator = "")
                .replace('\n', '\r') + "\u007F\u007F\u007F"
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload ->
                combined + payload
            }.toString(StandardCharsets.UTF_8)
            check(actual == expected) {
                "Terminal input lost or reordered bytes: expected ${expected.length} chars, " +
                    "received ${actual.length}"
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun terminalInput_actionMultipleRepeatsKnownHardwareKeys() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContent { sentPayloads += it.copyOf() }

        composeRule.runOnIdle {
            val root = composeRule.activity.findViewById<View>(android.R.id.content)
            val imeBridge = root.findEditTextDescendant()
                ?: error("Terminal IME bridge was not attached")
            val event = KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_MULTIPLE,
                KeyEvent.KEYCODE_A,
                4,
                0
            )
            check(imeBridge.dispatchKeyEvent(event))
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "aaaa") { "ACTION_MULTIPLE dropped repeats: $actual" }
        }
    }

    @Test
    fun terminalIme_composingTextIsSentOnceOnlyWhenCommitted() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContent { sentPayloads += it.copyOf() }

        composeRule.runOnIdle {
            val inputConnection = terminalInputConnection()
            check(inputConnection.setComposingText("e", 1))
            check(inputConnection.setComposingText("é", 1))
            check(inputConnection.commitText("é", 1))

            check(inputConnection.setComposingText("discard", 1))
            check(inputConnection.setComposingText("", 1))
            check(inputConnection.finishComposingText())

            check(inputConnection.setComposingText("中文", 1))
            check(inputConnection.finishComposingText())
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "é中文") {
                "IME composition leaked pre-edit or duplicate text: $actual"
            }
        }
    }

    @Test
    fun terminalIme_composingRegionReplacementHonorsCursorEdits() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContent { sentPayloads += it.copyOf() }

        composeRule.runOnIdle {
            val inputConnection = terminalInputConnection()
            check(inputConnection.setComposingText("abcd", 1))
            check(inputConnection.setComposingRegion(1, 3))
            check(inputConnection.setComposingText("X", 1))
            check(inputConnection.finishComposingText())
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "aXd") { "IME composing-region replacement produced: $actual" }
        }
    }

    @Test
    fun terminalIme_reportsShadowSelectionAndSupportsForwardDelete() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContent { sentPayloads += it.copyOf() }

        composeRule.runOnIdle {
            val inputConnection = terminalInputConnection()
            check(inputConnection.setComposingText("ab😀cd", 1))
            check(inputConnection.setSelection(2, 4))
            check(inputConnection.getSelectedText(0).toString() == "😀")
            check(inputConnection.setComposingRegion(2, 4))
            check(inputConnection.setComposingText("X", 1))
            check(inputConnection.finishComposingText())
            check(inputConnection.deleteSurroundingText(0, 2))
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "abXcd\u001B[3~\u001B[3~") {
                "IME selection/forward-delete path produced: ${actual.toByteArray().contentToString()}"
            }
        }
    }

    @Test
    fun terminalInput_usesLatestSendCallbackAfterSameSessionRecomposition() {
        val firstSink = mutableListOf<ByteArray>()
        val secondSink = mutableListOf<ByteArray>()
        var useSecondSink by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                SshTerminalForInputTest(
                    onSendShellBytes = if (useSecondSink) {
                        { payload -> secondSink += payload.copyOf() }
                    } else {
                        { payload -> firstSink += payload.copyOf() }
                    }
                )
            }
        }

        composeRule.runOnIdle {
            check(terminalInputConnection().commitText("before", 1))
            useSecondSink = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            check(terminalInputConnection().commitText("after", 1))
        }

        composeRule.runOnIdle {
            check(firstSink.single().toString(StandardCharsets.UTF_8) == "before")
            check(secondSink.single().toString(StandardCharsets.UTF_8) == "after") {
                "Remembered terminal input kept an obsolete send callback"
            }
        }
    }

    @Test
    fun terminalSession_supportsFindDialogAndSnippetPicker() {
        val sentPayloads = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ uname -a\nsshpeaches-live kernel build\nuser@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
                        this[12] = KeyboardLayoutDefaults.snippetPickerAction()
                    },
                    snippets = listOf(
                        Snippet(
                            id = "snippet-kernel",
                            title = "Kernel Check",
                            command = "uname -a"
                        )
                    ),
                    onSendShellBytes = { sentPayloads += String(it, StandardCharsets.UTF_8) },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 1
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_INPUT).assertIsDisplayed()
        composeRule.onNodeWithText("Enter search text").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_INPUT).performTextInput("sshpeaches-live")
        composeRule.onNodeWithText("1/1", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("sshpeaches-live", substring = true)[0].assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(12)).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SNIPPET_PICKER).assertIsDisplayed()
        composeRule.onNodeWithText("Kernel Check").performClick()

        composeRule.runOnIdle {
            check(sentPayloads.contains("uname -a\r")) {
                "Snippet picker did not send the expected shell payload"
            }
        }
    }

    @Test
    fun terminalKeyboardButtonAndDoubleTap_showSoftKeyboard() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
        hideTerminalKeyboardForTest(device)

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_TOGGLE).performClick()
        assertTerminalKeyboardRequested(device)

        hideTerminalKeyboardForTest(device)

        doubleTapTerminalPanel()
        assertTerminalKeyboardRequested(device)
    }

    @Test
    fun defaultKeyboard_allMainAndFnKeysDispatchAndControlsWork() {
        val sentPayloads = mutableListOf<ByteArray>()

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    useBuiltInKeyboard = true,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithText("Fn").assertIsDisplayed()

        listOf(0, 2, 3, 4, 5, 7, 9, 10, 11, 12).forEach { index ->
            composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(index))
                .assertIsDisplayed()
                .performClick()
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(6)).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            .performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(6)).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_TOGGLE)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(8)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(1)).performClick()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Shift").assertIsDisplayed().performClick()
        (2..13).forEach { index ->
            composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(index))
                .assertIsDisplayed()
                .performClick()
        }
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.onNodeWithText("Esc").assertIsDisplayed()

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            val expected = "\u001B\u001B[H\u001B[A\u001B[F\u001B[5~\t" +
                "\u001B[D\u001B[B\u001B[C\u001B[6~" +
                "\u001B[C" +
                "\u001B[1;6P\u001BOQ\u001BOR\u001BOS" +
                "\u001B[15~\u001B[17~\u001B[18~\u001B[19~" +
                "\u001B[20~\u001B[21~\u001B[23~\u001B[24~"
            check(actual == expected) {
                "Default main/Fn keyboard output mismatch. Expected=" +
                    expected.toByteArray().contentToString() +
                    " actual=" + actual.toByteArray().contentToString()
            }
        }
    }

    @Test
    fun compactKeyboard_dispatchesModifierSequenceAliasAndPasswordActions() {
        val sentPayloads = mutableListOf<ByteArray>()
        var openedSettings = 0
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.CTRL, "Ctrl")
            this[1] = KeyboardLayoutDefaults.textAction("c", "c")
            this[2] = KeyboardLayoutDefaults.sequenceAction("Echo", "echo custom-key\r")
            this[3] = KeyboardLayoutDefaults.passwordInjectAction()
            this[4] = KeyboardLayoutDefaults.textAction(label = "pwd+Enter", text = "").copy(iconId = "folder")
            this[5] = KeyboardLayoutDefaults.textAction(label = "Settings", text = "").copy(iconId = "build")
        }

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    resolveRuntimeSessionPassword = { "secret" },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = { openedSettings += 1 },
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(1)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(2)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(3)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(4)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(5)).performClick()

        composeRule.runOnIdle {
            check(sentPayloads.any { it.contentEquals(byteArrayOf(0x03)) }) {
                "Ctrl modifier did not transform the text key into Ctrl-C"
            }
            check(sentPayloads.any { String(it, StandardCharsets.UTF_8) == "echo custom-key\r" }) {
                "Sequence custom key did not send the expected payload"
            }
            check(sentPayloads.any { String(it, StandardCharsets.UTF_8) == "secret" }) {
                "Password inject custom key did not send the saved password"
            }
            check(sentPayloads.any { String(it, StandardCharsets.UTF_8) == "pwd\r" }) {
                "Folder alias custom key did not send the pwd command"
            }
            check(openedSettings == 1) {
                "Settings alias custom key did not invoke the settings callback"
            }
        }
    }

    @Test
    fun terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce() {
        val sentPayloads = mutableListOf<ByteArray>()
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.CTRL, "Ctrl")
        }

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "copy-paste-selection-target",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        lateinit var terminalView: TerminalView
        composeRule.runOnIdle {
            terminalView = composeRule.activity.findViewById<View>(android.R.id.content)
                .findTerminalViewDescendant()
                ?: error("Terminal view was not attached")
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            terminalView.performSelectionAction(copy = true)
            assertTerminalClipboard("copy-paste-selection-target")
            sentPayloads.clear()
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.runOnIdle {
            terminalView.dispatchUnmodifiedVirtualA()
            terminalView.dispatchUnmodifiedVirtualA()
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
            check(actual.contentEquals(byteArrayOf(0x01, 0x61))) {
                "Ctrl latch after terminal Copy produced ${actual.contentToString()}"
            }
        }
    }

    @Test
    fun terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce() {
        val sentPayloads = mutableListOf<ByteArray>()
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.CTRL, "Ctrl")
        }

        seedClipboardForTerminalPaste("seeded-paste")

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "copy-paste-selection-target",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.runOnIdle {
            val terminalView = composeRule.activity.findViewById<View>(android.R.id.content)
                .findTerminalViewDescendant()
                ?: error("Terminal view was not attached")
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            terminalView.performSelectionAction(copy = false)
            sentPayloads.clear()
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.runOnIdle {
            val terminalView = composeRule.activity.findViewById<View>(android.R.id.content)
                .findTerminalViewDescendant()
                ?: error("Terminal view was not attached")
            terminalView.dispatchUnmodifiedVirtualA()
            terminalView.dispatchUnmodifiedVirtualA()
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
            check(actual.contentEquals(byteArrayOf(0x01, 0x61))) {
                "Ctrl latch after terminal Paste produced ${actual.contentToString()}"
            }
        }
    }

    @Test
    fun terminalImeBridgeKeyListener_virtualImeKeyConsumesLatchedCtrlOnce() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey { sentPayloads += it.copyOf() }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.runOnIdle {
            val imeBridge = imeBridgeForTest()
            check(imeBridge.dispatchKeyEvent(keyDownEvent(KeyEvent.KEYCODE_A))) {
                "TerminalImeBridgeEditText did not consume latched Ctrl-A"
            }
            check(imeBridge.dispatchKeyEvent(keyDownEvent(KeyEvent.KEYCODE_A))) {
                "TerminalImeBridgeEditText did not consume plain A after Ctrl latch"
            }
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
            check(actual.contentEquals(byteArrayOf(0x01, 0x61))) {
                "IME bridge key listener produced ${actual.contentToString()}"
            }
        }
    }

    @Test
    fun terminalAndroidDispatcher_backAndSystemKeysDoNotClearLatchedCtrl() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey { sentPayloads += it.copyOf() }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.runOnIdle {
            val imeBridge = imeBridgeForTest()
            imeBridge.dispatchKeyEvent(keyDownEvent(KeyEvent.KEYCODE_BACK))
            imeBridge.dispatchKeyEvent(keyDownEvent(KeyEvent.KEYCODE_MENU))
            imeBridge.dispatchKeyEvent(
                KeyEvent(
                    0L,
                    0L,
                    KeyEvent.ACTION_MULTIPLE,
                    KeyEvent.KEYCODE_UNKNOWN,
                    1,
                    0
                )
            )
            check(sentPayloads.isEmpty()) {
                "Ignored Back/system/unknown keys wrote terminal bytes: ${sentPayloads.size}"
            }
            check(imeBridge.dispatchKeyEvent(keyDownEvent(KeyEvent.KEYCODE_A))) {
                "Latched Ctrl-A was not consumed after ignored Back/system/unknown keys"
            }
        }

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
            check(actual.contentEquals(byteArrayOf(0x01))) {
                "Ignored Back/system/unknown keys cleared Ctrl latch or wrote bytes: ${actual.contentToString()}"
            }
        }
    }

    @Test
    fun terminalActionModeCopy_restoresImeFocusAndCommittedCtrlA() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey(
            shellOutput = "copy-focus-selection-target",
            onSendShellBytes = { sentPayloads += it.copyOf() }
        )

        showTerminalKeyboardAndAssertBridgeFocus()
        val keyboardRequestedBefore = isTerminalKeyboardRequested()

        lateinit var terminalView: TerminalView
        composeRule.runOnIdle {
            terminalView = terminalViewForTest()
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            check(terminalView.hasFocus()) {
                "TerminalView should own focus while terminal selection is active"
            }
            terminalView.performSelectionAction(copy = true)
            sentPayloads.clear()
        }

        waitForSelectionInactive(terminalView)
        waitForImeBridgeFocus()
        check(isTerminalKeyboardRequested() == keyboardRequestedBefore) {
            "Terminal Copy changed keyboardVisibleRequested semantics"
        }
        assertCommittedCtrlAThenPlainA(sentPayloads)
    }

    @Test
    fun terminalActionModePaste_restoresImeFocusAndCommittedCtrlA() {
        val sentPayloads = mutableListOf<ByteArray>()
        seedClipboardForTerminalPaste("seeded-paste")
        setSshTerminalContentWithCtrlKey(
            shellOutput = "paste-focus-selection-target",
            onSendShellBytes = { sentPayloads += it.copyOf() }
        )

        showTerminalKeyboardAndAssertBridgeFocus()
        val keyboardRequestedBefore = isTerminalKeyboardRequested()

        lateinit var terminalView: TerminalView
        composeRule.runOnIdle {
            terminalView = terminalViewForTest()
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            check(terminalView.hasFocus()) {
                "TerminalView should own focus while terminal selection is active"
            }
            terminalView.performSelectionAction(copy = false)
            sentPayloads.clear()
        }

        waitForSelectionInactive(terminalView)
        waitForImeBridgeFocus()
        check(isTerminalKeyboardRequested() == keyboardRequestedBefore) {
            "Terminal Paste changed keyboardVisibleRequested semantics"
        }
        assertCommittedCtrlAThenPlainA(sentPayloads)
    }

    @Test
    fun terminalActionModeCopy_doesNotFocusImeBridgeWhenSystemKeyboardHidden() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey(
            shellOutput = "copy-hidden-keyboard-selection-target",
            onSendShellBytes = { sentPayloads += it.copyOf() }
        )

        check(!isTerminalKeyboardRequested()) {
            "Terminal keyboard should start hidden for this focus-restoration negative test"
        }

        lateinit var terminalView: TerminalView
        composeRule.runOnIdle {
            terminalView = terminalViewForTest()
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            check(terminalView.hasFocus()) {
                "TerminalView should own focus while terminal selection is active"
            }
            terminalView.performSelectionAction(copy = true)
        }

        waitForSelectionInactive(terminalView)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            check(!isTerminalKeyboardRequested()) {
                "Terminal Copy should not mark the system keyboard requested when it was hidden"
            }
            check(!imeBridgeForTest().hasFocus()) {
                "Terminal IME bridge should not regain focus when the system keyboard was hidden"
            }
        }
    }

    @Test
    fun terminalActionModeCopy_doesNotFocusImeBridgeInBuiltInKeyboardMode() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey(
            shellOutput = "copy-built-in-keyboard-selection-target",
            useBuiltInKeyboard = true,
            onSendShellBytes = { sentPayloads += it.copyOf() }
        )

        lateinit var terminalView: TerminalView
        composeRule.runOnIdle {
            terminalView = terminalViewForTest()
            terminalView.startTextSelectionAtViewportCenter()
            terminalView.selectAllText()
            check(terminalView.hasFocus()) {
                "TerminalView should own focus while terminal selection is active"
            }
            terminalView.performSelectionAction(copy = true)
        }

        waitForSelectionInactive(terminalView)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            check(!isTerminalKeyboardRequested()) {
                "Built-in keyboard mode should not mark the system keyboard requested"
            }
            check(!imeBridgeForTest().hasFocus()) {
                "Terminal IME bridge should not regain focus in built-in keyboard mode"
            }
        }
    }

    @Test
    fun terminalImeContextPaste_keepsCommittedCtrlAAfterPaste() {
        val sentPayloads = mutableListOf<ByteArray>()
        seedClipboardForTerminalPaste("ime-paste")
        setSshTerminalContentWithCtrlKey { sentPayloads += it.copyOf() }

        val inputConnection = focusedTerminalInputConnection()
        val handled = performImeContextMenuActionAndAssertKeyboardRequestUnchanged(
            inputConnection = inputConnection,
            actionId = android.R.id.paste,
            label = "paste"
        ) {
            sentPayloads.clear()
        }
        check(handled) { "IME paste context action was not handled" }

        assertCommittedCtrlAThenPlainA(sentPayloads)
    }

    @Test
    fun terminalImeContextPasteAsPlainText_keepsCommittedCtrlAAfterPaste() {
        val sentPayloads = mutableListOf<ByteArray>()
        seedClipboardForTerminalPaste("ime-plain-paste")
        setSshTerminalContentWithCtrlKey { sentPayloads += it.copyOf() }

        val inputConnection = focusedTerminalInputConnection()
        val handled = performImeContextMenuActionAndAssertKeyboardRequestUnchanged(
            inputConnection = inputConnection,
            actionId = android.R.id.pasteAsPlainText,
            label = "paste-as-plain-text"
        ) {
            sentPayloads.clear()
        }
        if (!handled) {
            composeRule.runOnIdle {
                check(sentPayloads.isEmpty()) {
                    "Unsupported IME paste-as-plain-text action should not send terminal bytes"
                }
            }
            return
        }

        assertCommittedCtrlAThenPlainA(sentPayloads)
    }

    @Test
    fun terminalImeContextCopy_keepsCommittedCtrlAAfterSelectedShadowTextCopy() {
        val sentPayloads = mutableListOf<ByteArray>()
        setSshTerminalContentWithCtrlKey { sentPayloads += it.copyOf() }

        val inputConnection = focusedTerminalInputConnection()
        composeRule.runOnIdle {
            check(inputConnection.setComposingText("shadow-copy", 1))
            check(inputConnection.setSelection(0, "shadow-copy".length))
        }
        val handled = performImeContextMenuActionAndAssertKeyboardRequestUnchanged(
            inputConnection = inputConnection,
            actionId = android.R.id.copy,
            label = "copy"
        ) {
            sentPayloads.clear()
        }
        check(handled) { "IME copy context action was not handled" }
        composeRule.runOnIdle {
            assertTerminalClipboard("shadow-copy")
        }

        assertCommittedCtrlAThenPlainA(sentPayloads)
    }

    @Test
    fun builtInKeyboard_keepsModifiersWorkingAcrossFnLayer() {
        val sentPayloads = mutableListOf<ByteArray>()

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    useBuiltInKeyboard = true,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(8)).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(1)).performClick()
        composeRule.onNodeWithText("Shift").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(2)).performClick()

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "\u001B[1;5P") {
                "Ctrl was lost while switching to the built-in Fn layer: $actual"
            }
        }
    }

    @Test
    fun customKeyboard_fnLayerIsFixedAndBackReturnsToRemappableMainRows() {
        val sentPayloads = mutableListOf<ByteArray>()
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[13] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it.copyOf() },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(1)).performClick()
        composeRule.onNodeWithText("F1").assertIsDisplayed()
        composeRule.onNodeWithText("Custom").assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(2)).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()

        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
                .toString(StandardCharsets.UTF_8)
            check(actual == "\u001BOP") { "Custom Fn layer did not emit F1: $actual" }
        }
        composeRule.onNodeWithText("F1").assertDoesNotExist()
        composeRule.onNodeWithText("Custom").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(13)).assertIsDisplayed()
    }

    @Test
    fun passwordInjectUsesSavedHostPasswordAfterVaultMigration() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        SecurityManager.init(appContext)
        SecurityManager.unlock()
        SecurityManager.storeHostPassword("saved-host", "vaulted-secret")
        SecurityManager.setPin("2468")
        SecurityManager.lock()
        check(SecurityManager.verifyPin("2468")) {
            "Expected PIN verification to unlock the migrated vault"
        }

        val sentPayloads = mutableListOf<ByteArray>()
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.passwordInjectAction()
        }

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH).copy(savedHostId = "saved-host"),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "user@host:~$ ",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = { sentPayloads += it },
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()

        composeRule.runOnIdle {
            check(sentPayloads.any { String(it, StandardCharsets.UTF_8) == "vaulted-secret" }) {
                "Password inject key did not send the saved host password from vault-backed storage"
            }
        }
    }

    @Test
    fun errorState_showsLogsPaneAndInvokesRetry() {
        var retryCount = 0

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.ERROR,
                        message = "Authentication failed"
                    ),
                    logs = listOf(
                        SessionLogBus.Entry(
                            hostId = "session-ssh",
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Connecting to localhost..."
                        ),
                        SessionLogBus.Entry(
                            hostId = "session-ssh",
                            level = SessionLogBus.LogLevel.ERROR,
                            message = "Permission denied (publickey,password)."
                        )
                    ),
                    shellOutput = "",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = { retryCount += 1 },
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithText("Connection failed").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_LOG_PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("Authentication failed").assertIsDisplayed()
        composeRule.onNodeWithText("Permission denied", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).performClick()
        composeRule.runOnIdle {
            check(retryCount == 1) { "Retry callback was not invoked exactly once" }
        }
    }

    @Test
    fun networkErrorState_identifiesNetworkFailure() {
        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.ERROR,
                        message = "Connection refused",
                        failureKind = ConnectionFailureKind.NETWORK
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithText("Network error").assertIsDisplayed()
        composeRule.onNodeWithText("Connection refused").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun connectingLogs_useTerminalStyleGrowUpwardAndFollowNewestLine() {
        val rawMessage = "ssh_packet_global_request: Received SSH_MSG_GLOBAL_REQUEST"
        var currentLogs by mutableStateOf(
            listOf(
                SessionLogBus.Entry(
                    hostId = "session-ssh",
                    level = SessionLogBus.LogLevel.DEBUG,
                    message = rawMessage,
                    timestamp = 1L
                )
            )
        )

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(500.dp)
            ) {
                MaterialTheme {
                    ConnectingLogStatus(logs = currentLogs)
                }
            }
        }

        composeRule.onNodeWithText(rawMessage).assertIsDisplayed()
        composeRule.onNodeWithText("[DEBUG] $rawMessage").assertDoesNotExist()
        val oneLineBounds = composeRule.onNodeWithTag(UiTestTags.CONNECTING_LOG_PANEL)
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle {
            currentLogs = (1..40).map { index ->
                SessionLogBus.Entry(
                    hostId = "session-ssh",
                    level = SessionLogBus.LogLevel.DEBUG,
                    message = "ssh_packet_line_$index",
                    timestamp = index.toLong()
                )
            }
        }
        waitUntilOrFail(
            timeoutMillis = 5_000,
            failureMessage = "The connecting log did not follow the newest line"
        ) {
            runCatching {
                composeRule.onNodeWithText("ssh_packet_line_40").assertIsDisplayed()
            }.isSuccess
        }
        val cappedBounds = composeRule.onNodeWithTag(UiTestTags.CONNECTING_LOG_PANEL)
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val tolerancePx = density * 2f
        val expectedMaxHeightPx = density * 120f
        check(cappedBounds.height > oneLineBounds.height) {
            "The log pane should grow upward as lines are added"
        }
        check(kotlin.math.abs(cappedBounds.bottom - oneLineBounds.bottom) <= tolerancePx) {
            "The growing log pane should stay anchored to the bottom"
        }
        check(cappedBounds.height <= expectedMaxHeightPx + tolerancePx) {
            "The log pane exceeded its 24% max height: ${cappedBounds.height}px"
        }

        composeRule.runOnIdle {
            currentLogs = (1..80).map { index ->
                SessionLogBus.Entry(
                    hostId = "session-ssh",
                    level = SessionLogBus.LogLevel.DEBUG,
                    message = "ssh_packet_line_$index",
                    timestamp = index.toLong()
                )
            }
        }
        waitUntilOrFail(
            timeoutMillis = 5_000,
            failureMessage = "The capped connecting log did not follow the newest line"
        ) {
            runCatching {
                composeRule.onNodeWithText("ssh_packet_line_80").assertIsDisplayed()
            }.isSuccess
        }
        val overflowBounds = composeRule.onNodeWithTag(UiTestTags.CONNECTING_LOG_PANEL)
            .fetchSemanticsNode()
            .boundsInRoot
        check(kotlin.math.abs(overflowBounds.height - cappedBounds.height) <= tolerancePx) {
            "The log pane should stop growing after reaching its max height"
        }
    }

    @Test
    fun sftpPanel_identicalListingWithNewTokenCompletesCommand() {
        var listedPath: String? = null
        var remoteDirectory by mutableStateOf(
            SessionService.RemoteDirectorySnapshot(
                path = "/docs",
                entries = listOf(
                    SessionService.RemoteDirectoryEntry(
                        name = "welcome.txt",
                        isDirectory = false,
                        sizeBytes = 12
                    )
                ),
                refreshToken = 10L
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SFTP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SFTP browser ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = remoteDirectory,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {
                        listedPath = it
                        remoteDirectory = remoteDirectory.copy(
                            refreshToken = remoteDirectory.refreshToken + 1L
                        )
                    },
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Commands: ls [path]", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("ls /docs")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON)
                    .assertIsEnabled()
            }.isSuccess
        }
        composeRule.runOnIdle {
            check(listedPath == "/docs") {
                "SFTP list callback did not receive the requested remote path"
            }
        }
        composeRule.onNodeWithText("Remote directory: /docs", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("- 12 welcome.txt", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun sftpGetWithoutLocalPath_downloadsImmediatePickerResult() {
        val destinationUri = Uri.parse("content://sshpeaches.test/sftp-download")
        val downloadRequests = mutableListOf<Pair<String, String?>>()

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(destinationUri)
                )
            )
            setSftpPickerContent(
                onSftpDownload = { remote, local -> downloadRequests += remote to local }
            )

            composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
                .performTextReplacement("get welcome.txt")
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                downloadRequests.isNotEmpty()
            }
            intended(hasAction(Intent.ACTION_CREATE_DOCUMENT), times(1))
            composeRule.runOnIdle {
                check(
                    downloadRequests == listOf(
                        "/docs/welcome.txt" to destinationUri.toString()
                    )
                ) {
                    "SFTP get should use the remote path captured before launching the picker: $downloadRequests"
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun sftpPutWithoutLocalPath_uploadsImmediatePickerResult() {
        val pickedUri = Uri.parse("content://sshpeaches.test/sftp-upload")
        val uploadRequests = mutableListOf<Pair<String, String>>()

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(pickedUri)
                )
            )
            setSftpPickerContent(
                onSftpUpload = { local, remote -> uploadRequests += local to remote }
            )

            composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
                .performTextReplacement("put")
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                uploadRequests.isNotEmpty()
            }
            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT), times(1))
            composeRule.runOnIdle {
                check(
                    uploadRequests == listOf(
                        pickedUri.toString() to "/docs/upload.bin"
                    )
                ) {
                    "SFTP put should use the remote base captured before launching the picker: $uploadRequests"
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun sftpPanel_invalidTypedPathShowsErrorToast() {
        var listedPath: String? = null
        var logs by mutableStateOf<List<SessionLogBus.Entry>>(emptyList())

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SFTP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SFTP browser ready"
                    ),
                    logs = logs,
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/docs",
                        entries = listOf(
                            SessionService.RemoteDirectoryEntry(
                                name = "welcome.txt",
                                isDirectory = false,
                                sizeBytes = 12
                            )
                        )
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = { listedPath = it },
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("ls /missing")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()

        composeRule.runOnIdle {
            check(listedPath == "/missing") {
                "SFTP list callback did not receive the invalid remote path."
            }
            logs = listOf(
                SessionLogBus.Entry(
                    hostId = "session-sftp",
                    level = SessionLogBus.LogLevel.ERROR,
                    message = "SFTP operation failed: No such file or directory"
                )
            )
        }

        composeRule.onNodeWithText("Remote: /docs", substring = true).assertIsDisplayed()
    }

    @Test
    fun sftpTransferCompletionShowsSuccessStatusAndSnackbarCallback() {
        val shownMessages = mutableListOf<String>()
        var activeTransfer by mutableStateOf<FileTransferProgress?>(
            FileTransferProgress(
                sessionId = "session-sftp",
                mode = ConnectionMode.SFTP,
                direction = FileTransferDirection.DOWNLOAD,
                fileName = "welcome.txt",
                sourceLabel = "/docs/welcome.txt",
                destinationLabel = "/storage/emulated/0/Download/welcome.txt",
                operationId = "download-1",
                bytesTransferred = 6L,
                totalBytes = 12L,
                hasStarted = true
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SFTP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SFTP browser ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/docs",
                        entries = listOf(
                            SessionService.RemoteDirectoryEntry(
                                name = "welcome.txt",
                                isDirectory = false,
                                sizeBytes = 12
                            )
                        )
                    ),
                    activeFileTransfer = activeTransfer,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    onShowMessage = { shownMessages += it },
                    findRequestToken = 0
                )
            }
        }

        composeRule.runOnIdle {
            activeTransfer = activeTransfer?.copy(
                bytesTransferred = 12L,
                status = FileTransferStatus.SUCCEEDED,
                completedAtEpochMillis = 1L
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Download completed: welcome.txt").assertIsDisplayed()

        composeRule.runOnIdle {
            check(shownMessages == listOf("Download completed: welcome.txt")) {
                "SFTP completion did not trigger the snackbar callback."
            }
        }
    }

    @Test
    fun scpPanel_usesSelectionModelAndOverflowActions() {
        var listedPath: String? = null
        var downloadRequest: Pair<String, String?>? = null

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SCP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SCP transfer ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/uploads",
                        entries = listOf(
                            SessionService.RemoteDirectoryEntry(
                                name = "subdir",
                                isDirectory = true,
                                sizeBytes = 0
                            ),
                            SessionService.RemoteDirectoryEntry(
                                name = "existing.txt",
                                isDirectory = false,
                                sizeBytes = 24
                            )
                        )
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = { listedPath = it },
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { remote, local -> downloadRequest = remote to local },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("new_folder")).assertIsEnabled()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("new_folder")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            check(downloadRequest == null) {
                "SCP download callback should not run before a file is selected."
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/existing.txt")).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/existing.txt"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON).assertIsEnabled()
        composeRule.runOnIdle {
            check(downloadRequest == null) {
                "SCP download callback should remain idle until a document picker result is delivered"
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/subdir")).performClick()
        composeRule.runOnIdle {
            check(listedPath == "/uploads/subdir") {
                "Tapping an SCP folder row should open that folder."
            }
        }
    }

    @Test
    fun scpUploadPanel_promotesUploadActionToToolbar() {
        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SCP).copy(
                        initialFileTransferEntryMode = FileTransferEntryMode.UPLOAD
                    ),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SCP transfer ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/uploads",
                        entries = emptyList()
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON)
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithText("Upload into this folder, or select a remote file to download.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON)
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("choose_local_file")).assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("upload_here")).assertDoesNotExist()
    }

    @Test
    fun scpUploadButton_reviewsDestinationBeforeEachUpload() {
        val pickedUri = Uri.parse("content://sshpeaches.test/picked-file")
        val uploadRequests = mutableListOf<Pair<String, String>>()

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(pickedUri)
                )
            )
            composeRule.setContent {
                MaterialTheme {
                    ConnectingScreen(
                        request = requestFor(ConnectionMode.SCP).copy(
                            initialFileTransferEntryMode = FileTransferEntryMode.UPLOAD
                        ),
                        state = QuickConnectUiState(
                            phase = QuickConnectPhase.SUCCESS,
                            message = "SCP transfer ready"
                        ),
                        logs = emptyList(),
                        shellOutput = "",
                        remoteDirectory = SessionService.RemoteDirectorySnapshot(
                            path = "/uploads",
                            entries = emptyList()
                        ),
                        terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                        terminalSelectionMode = TerminalSelectionMode.NATURAL,
                        keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                        snippets = emptyList(),
                        onSendShellBytes = {},
                        onTerminalResize = { _, _ -> },
                        onSftpListDirectory = {},
                        onSftpDownload = { _, _ -> },
                        onSftpUpload = { _, _ -> },
                        onScpDownload = { _, _ -> },
                        onScpUpload = { local, remote -> uploadRequests += local to remote },
                        onManageRemotePath = { _, _, _ -> },
                        onRetry = {},
                        onToggleConnectedHostBar = {},
                        onOpenSettings = {},
                        findRequestToken = 0
                    )
                }
            }

            repeat(2) { index ->
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON).performClick()
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_DIALOG)
                    .assertIsDisplayed()
                composeRule.runOnIdle {
                    check(uploadRequests.size == index) {
                        "Picking a file must not start an upload before destination review."
                    }
                }
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_DESTINATION_INPUT)
                    .assertIsDisplayed()
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_CONFIRM_BUTTON)
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    uploadRequests.size == index + 1
                }
            }

            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT), times(2))
            composeRule.runOnIdle {
                check(
                    uploadRequests == listOf(
                        pickedUri.toString() to "/uploads/upload.bin",
                        pickedUri.toString() to "/uploads/upload.bin"
                    )
                ) {
                    "Each confirmed upload should use the reviewed destination: $uploadRequests"
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun scpDownloadButton_downloadsPickerResultImmediatelyAndRepicksEveryTime() {
        val destinationUri = Uri.parse("content://sshpeaches.test/download-file")
        val downloadRequests = mutableListOf<Pair<String, String?>>()

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(destinationUri)
                )
            )
            composeRule.setContent {
                MaterialTheme {
                    ConnectingScreen(
                        request = requestFor(ConnectionMode.SCP),
                        state = QuickConnectUiState(
                            phase = QuickConnectPhase.SUCCESS,
                            message = "SCP transfer ready"
                        ),
                        logs = emptyList(),
                        shellOutput = "",
                        remoteDirectory = SessionService.RemoteDirectorySnapshot(
                            path = "/uploads",
                            entries = listOf(
                                SessionService.RemoteDirectoryEntry(
                                    name = "report.txt",
                                    isDirectory = false,
                                    sizeBytes = 24
                                )
                            )
                        ),
                        terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                        terminalSelectionMode = TerminalSelectionMode.NATURAL,
                        keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                        snippets = emptyList(),
                        onSendShellBytes = {},
                        onTerminalResize = { _, _ -> },
                        onSftpListDirectory = {},
                        onSftpDownload = { _, _ -> },
                        onSftpUpload = { _, _ -> },
                        onScpDownload = { remote, local -> downloadRequests += remote to local },
                        onScpUpload = { _, _ -> },
                        onManageRemotePath = { _, _, _ -> },
                        onRetry = {},
                        onToggleConnectedHostBar = {},
                        onOpenSettings = {},
                        findRequestToken = 0
                    )
                }
            }

            composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/report.txt"))
                .performClick()
            repeat(2) { index ->
                composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON).performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    downloadRequests.size == index + 1
                }
            }

            intended(hasAction(Intent.ACTION_CREATE_DOCUMENT), times(2))
            composeRule.runOnIdle {
                check(
                    downloadRequests == listOf(
                        "/uploads/report.txt" to destinationUri.toString(),
                        "/uploads/report.txt" to destinationUri.toString()
                    )
                ) {
                    "Each download button press should pick and immediately download one file: $downloadRequests"
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun scpUploadAction_isDisabledWhileDirectoryListingIsPending() {
        val listedPaths = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SCP).copy(
                        initialFileTransferEntryMode = FileTransferEntryMode.UPLOAD
                    ),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SCP transfer ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/uploads",
                        entries = emptyList(),
                        refreshToken = 1L
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = { listedPaths += it },
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON).assertIsEnabled()
        composeRule.onNodeWithContentDescription("Refresh").performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON).assertIsNotEnabled()
        composeRule.runOnIdle {
            check(listedPaths.lastOrNull() == "/uploads")
        }
    }

    @Test
    fun scpForwardNavigation_isReachableFromOverflowOnNarrowScreens() {
        val listedPaths = mutableListOf<String>()
        var remoteDirectory by mutableStateOf<SessionService.RemoteDirectorySnapshot?>(null)

        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    ConnectingScreen(
                        request = requestFor(ConnectionMode.SCP),
                        state = QuickConnectUiState(
                            phase = QuickConnectPhase.SUCCESS,
                            message = "SCP transfer ready"
                        ),
                        logs = emptyList(),
                        shellOutput = "",
                        remoteDirectory = remoteDirectory,
                        terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                        terminalSelectionMode = TerminalSelectionMode.NATURAL,
                        keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                        snippets = emptyList(),
                        onSendShellBytes = {},
                        onTerminalResize = { _, _ -> },
                        onSftpListDirectory = { listedPaths += it },
                        onSftpDownload = { _, _ -> },
                        onSftpUpload = { _, _ -> },
                        onScpDownload = { _, _ -> },
                        onScpUpload = { _, _ -> },
                        onManageRemotePath = { _, _, _ -> },
                        onRetry = {},
                        onToggleConnectedHostBar = {},
                        onOpenSettings = {},
                        findRequestToken = 0
                    )
                }
            }
        }

        composeRule.runOnIdle {
            remoteDirectory = SessionService.RemoteDirectorySnapshot(
                path = "/home/tester",
                entries = listOf(
                    SessionService.RemoteDirectoryEntry(
                        name = "docs",
                        isDirectory = true,
                        sizeBytes = 0
                    )
                ),
                refreshToken = 1L
            )
        }
        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/home/tester/docs")).performClick()
        composeRule.runOnIdle {
            remoteDirectory = SessionService.RemoteDirectorySnapshot(
                path = "/home/tester/docs",
                entries = emptyList(),
                refreshToken = 2L
            )
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle {
            remoteDirectory = SessionService.RemoteDirectorySnapshot(
                path = "/home/tester",
                entries = emptyList(),
                refreshToken = 3L
            )
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("forward"))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            check(listedPaths.lastOrNull() == "/home/tester/docs") {
                "Narrow SCP Forward did not revisit the next history entry: $listedPaths"
            }
        }
    }

    @Test
    fun scpPanel_remoteActionsInvokeManagementCallbacks() {
        val operations = mutableListOf<Triple<String, String, String?>>()

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SCP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SCP transfer ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/uploads",
                        entries = listOf(
                            SessionService.RemoteDirectoryEntry(
                                name = "existing.txt",
                                isDirectory = false,
                                sizeBytes = 24
                            )
                        )
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { operation, source, destination ->
                        operations += Triple(operation, source, destination)
                    },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/existing.txt")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("rename")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_RENAME_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_RENAME_INPUT).performTextReplacement("renamed.txt")
        composeRule.onAllNodesWithText("Rename")[1].performClick()

        composeRule.runOnIdle {
            check(
                operations.firstOrNull() == Triple(
                    "move",
                    "/uploads/existing.txt",
                    "/uploads/renamed.txt"
                )
            ) {
                "Rename action did not map to the expected move operation."
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("new_folder")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_INPUT).performTextInput("logs")
        composeRule.onNodeWithText("Create").performClick()

        composeRule.runOnIdle {
            check(
                operations.lastOrNull() == Triple(
                    "mkdir",
                    "/uploads/logs",
                    null
                )
            ) {
                "New folder action did not request mkdir for the current directory."
            }
        }
    }

    @Test
    fun scpPanel_homeButtonUsesCanonicalHomePath() {
        val listedPaths = mutableListOf<String>()
        var remoteDirectory by mutableStateOf<SessionService.RemoteDirectorySnapshot?>(null)

        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SCP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SCP transfer ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = remoteDirectory,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = { listedPaths += it },
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }

        composeRule.runOnIdle {
            check(listedPaths.firstOrNull() == ".") {
                "SCP screen did not request the initial home directory listing"
            }
            remoteDirectory = SessionService.RemoteDirectorySnapshot(
                path = "/home/tester",
                entries = listOf(
                    SessionService.RemoteDirectoryEntry(
                        name = "docs",
                        isDirectory = true,
                        sizeBytes = 0
                    )
                ),
                refreshToken = 1L
            )
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/home/tester/docs")).performClick()
        composeRule.runOnIdle {
            check(listedPaths.lastOrNull() == "/home/tester/docs") {
                "SCP browser did not navigate into the selected subdirectory"
            }
            remoteDirectory = SessionService.RemoteDirectorySnapshot(
                path = "/home/tester/docs",
                entries = emptyList(),
                refreshToken = 2L
            )
        }

        val homeActionTag = UiTestTags.connectingScpAction("home")
        if (composeRule.onAllNodesWithTag(homeActionTag).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        }
        composeRule.onNodeWithTag(homeActionTag).performClick()
        composeRule.runOnIdle {
            check(listedPaths.lastOrNull() == "/home/tester") {
                "SCP Home button should reuse the canonical home path instead of requesting '.'"
            }
        }
    }

    private fun setSshTerminalContent(onSendShellBytes: (ByteArray) -> Unit) {
        composeRule.setContent {
            MaterialTheme {
                SshTerminalForInputTest(onSendShellBytes)
            }
        }
    }

    private fun setSshTerminalContentWithCtrlKey(
        shellOutput: String = "",
        useBuiltInKeyboard: Boolean = false,
        onSendShellBytes: (ByteArray) -> Unit
    ) {
        val customSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.CTRL, "Ctrl")
        }
        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SSH),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "Interactive shell session ready"
                    ),
                    logs = emptyList(),
                    shellOutput = shellOutput,
                    remoteDirectory = null,
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    useBuiltInKeyboard = useBuiltInKeyboard,
                    keyboardSlots = customSlots,
                    snippets = emptyList(),
                    onSendShellBytes = onSendShellBytes,
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = { _, _ -> },
                    onSftpUpload = { _, _ -> },
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }
    }

    @Composable
    private fun ConnectingLogStatus(logs: List<SessionLogBus.Entry>) {
        ConnectingScreen(
            request = requestFor(ConnectionMode.SSH),
            state = QuickConnectUiState(
                phase = QuickConnectPhase.CONNECTING,
                message = "Opening SSH connection..."
            ),
            logs = logs,
            shellOutput = "",
            remoteDirectory = null,
            terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
            terminalSelectionMode = TerminalSelectionMode.NATURAL,
            keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
            snippets = emptyList(),
            onSendShellBytes = {},
            onTerminalResize = { _, _ -> },
            onSftpListDirectory = {},
            onSftpDownload = { _, _ -> },
            onSftpUpload = { _, _ -> },
            onScpDownload = { _, _ -> },
            onScpUpload = { _, _ -> },
            onManageRemotePath = { _, _, _ -> },
            onRetry = {},
            onToggleConnectedHostBar = {},
            onOpenSettings = {},
            findRequestToken = 0
        )
    }

    @Composable
    private fun SshTerminalForInputTest(
        onSendShellBytes: (ByteArray) -> Unit,
        terminalProfile: TerminalProfile = TerminalProfileDefaults.builtInProfiles.first()
    ) {
        ConnectingScreen(
            request = requestFor(ConnectionMode.SSH),
            state = QuickConnectUiState(
                phase = QuickConnectPhase.SUCCESS,
                message = "Interactive shell session ready"
            ),
            logs = emptyList(),
            shellOutput = "",
            remoteDirectory = null,
            terminalProfile = terminalProfile,
            terminalSelectionMode = TerminalSelectionMode.NATURAL,
            keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
            snippets = emptyList(),
            onSendShellBytes = onSendShellBytes,
            onTerminalResize = { _, _ -> },
            onSftpListDirectory = {},
            onSftpDownload = { _, _ -> },
            onSftpUpload = { _, _ -> },
            onScpDownload = { _, _ -> },
            onScpUpload = { _, _ -> },
            onManageRemotePath = { _, _, _ -> },
            onRetry = {},
            onToggleConnectedHostBar = {},
            onOpenSettings = {},
            findRequestToken = 0
        )
    }

    private fun terminalInputConnection(): InputConnection {
        val imeBridge = imeBridgeForTest()
        return imeBridge.onCreateInputConnection(EditorInfo())
            ?: error("Terminal IME bridge did not create an InputConnection")
    }

    private fun focusedTerminalInputConnection(): InputConnection {
        showTerminalKeyboardAndAssertBridgeFocus()
        return terminalInputConnection()
    }

    private fun imeBridgeForTest(): EditText {
        val root = composeRule.activity.findViewById<View>(android.R.id.content)
        return root.findEditTextDescendant()
            ?: error("Terminal IME bridge was not attached")
    }

    private fun terminalViewForTest(): TerminalView {
        return composeRule.activity.findViewById<View>(android.R.id.content)
            .findTerminalViewDescendant()
            ?: error("Terminal view was not attached")
    }

    private fun View.findTerminalViewDescendant(): TerminalView? {
        if (this is TerminalView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            val match = getChildAt(index).findTerminalViewDescendant()
            if (match != null) return match
        }
        return null
    }

    private fun TerminalView.rendererTypefaceForTest(): Typeface {
        val rendererField = TerminalView::class.java.getDeclaredField("mRenderer").apply {
            isAccessible = true
        }
        val renderer = rendererField.get(this)
            ?: error("Terminal renderer was not initialized")
        val typefaceField = renderer.javaClass.getDeclaredField("mTypeface").apply {
            isAccessible = true
        }
        return typefaceField.get(renderer) as Typeface
    }

    private fun TerminalView.performSelectionAction(copy: Boolean) {
        check(performTextSelectionActionForTest(copy)) {
            "Terminal ${if (copy) "Copy" else "Paste"} action was not handled"
        }
    }

    private fun TerminalView.dispatchUnmodifiedVirtualA() {
        val event = keyDownEvent(KeyEvent.KEYCODE_A)
        check(dispatchKeyEvent(event)) {
            "TerminalView did not consume unmodified virtual KEYCODE_A"
        }
    }

    private fun keyDownEvent(keyCode: Int): KeyEvent =
        KeyEvent(
            0L,
            0L,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0
        )

    private fun TerminalView.isTextSelectionActiveForTest(): Boolean {
        return isSelectingText
    }

    private fun waitForSelectionInactive(terminalView: TerminalView) {
        waitUntilOrFail(
            timeoutMillis = 5_000,
            failureMessage = "Timed out waiting for terminal selection to become inactive"
        ) {
            !terminalView.isTextSelectionActiveForTest()
        }
        check(!terminalView.isTextSelectionActiveForTest()) {
            "Terminal selection is still active after explicit Copy/Paste action"
        }
    }

    private fun waitForImeBridgeFocus() {
        waitUntilOrFail(
            timeoutMillis = 5_000,
            failureMessage = "Timed out waiting for TerminalImeBridgeEditText to regain focus"
        ) {
            imeBridgeForTest().hasFocus()
        }
        check(imeBridgeForTest().hasFocus()) {
            "TerminalImeBridgeEditText did not regain focus after terminal selection ended"
        }
    }

    private fun showTerminalKeyboardAndAssertBridgeFocus() {
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_TOGGLE).performClick()
        waitUntilOrFail(
            timeoutMillis = 5_000,
            failureMessage = "Timed out waiting for TerminalImeBridgeEditText focus after keyboard request"
        ) {
            imeBridgeForTest().hasFocus()
        }
        check(imeBridgeForTest().hasFocus()) {
            "TerminalImeBridgeEditText should own focus after showing the system keyboard"
        }
        check(isTerminalKeyboardRequested()) {
            "Terminal keyboard should be marked requested after showing the system keyboard"
        }
    }

    private fun waitUntilOrFail(
        timeoutMillis: Long,
        failureMessage: String,
        condition: () -> Boolean
    ) {
        runCatching {
            composeRule.waitUntil(timeoutMillis, condition)
        }.getOrElse { cause ->
            throw AssertionError(failureMessage, cause)
        }
    }

    private fun assertCommittedCtrlAThenPlainA(sentPayloads: MutableList<ByteArray>) {
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.runOnIdle {
            check(terminalInputConnection().commitText("a", 1)) {
                "IME rejected committed Ctrl-A input"
            }
            check(terminalInputConnection().commitText("a", 1)) {
                "IME rejected committed plain A input"
            }
        }
        composeRule.runOnIdle {
            val actual = sentPayloads.fold(ByteArray(0)) { combined, payload -> combined + payload }
            check(actual.contentEquals(byteArrayOf(0x01, 0x61))) {
                "Committed Ctrl-A/plain-A produced ${actual.contentToString()}"
            }
        }
    }

    private fun performImeContextMenuActionAndAssertKeyboardRequestUnchanged(
        inputConnection: InputConnection,
        actionId: Int,
        label: String,
        afterAction: () -> Unit
    ): Boolean {
        var handled = false
        composeRule.runOnIdle {
            check(imeBridgeForTest().hasFocus()) {
                "TerminalImeBridgeEditText should own focus before IME $label context action"
            }
            val keyboardRequestedBefore = isTerminalKeyboardRequested()
            handled = inputConnection.performContextMenuAction(actionId)
            check(imeBridgeForTest().hasFocus()) {
                "TerminalImeBridgeEditText lost focus after IME $label context action"
            }
            check(isTerminalKeyboardRequested() == keyboardRequestedBefore) {
                "IME $label context action changed keyboardVisibleRequested semantics"
            }
            afterAction()
        }
        return handled
    }

    private fun seedClipboardForTerminalPaste(text: String) {
        val clipboard = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal-selection", text))
    }

    private fun assertTerminalClipboard(expectedText: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
            ?: error("Terminal copy did not populate the clipboard")
        val copiedText = clipData
            .takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        check(copiedText?.contains(expectedText) == true) {
            "Terminal copy produced ${copiedText ?: "no text"}"
        }
        check(
            clipData.description.extras
                ?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        ) {
            "Terminal copy did not mark terminal text as sensitive"
        }
        check(
            clipData.description.extras
                ?.getBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY") == true
        ) {
            "Terminal copy did not suppress the emulator clipboard overlay"
        }
    }

    private fun setSftpPickerContent(
        onSftpDownload: (String, String?) -> Unit = { _, _ -> },
        onSftpUpload: (String, String) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            MaterialTheme {
                ConnectingScreen(
                    request = requestFor(ConnectionMode.SFTP),
                    state = QuickConnectUiState(
                        phase = QuickConnectPhase.SUCCESS,
                        message = "SFTP browser ready"
                    ),
                    logs = emptyList(),
                    shellOutput = "",
                    remoteDirectory = SessionService.RemoteDirectorySnapshot(
                        path = "/docs",
                        entries = listOf(
                            SessionService.RemoteDirectoryEntry(
                                name = "welcome.txt",
                                isDirectory = false,
                                sizeBytes = 12
                            )
                        )
                    ),
                    terminalProfile = TerminalProfileDefaults.builtInProfiles.first(),
                    terminalSelectionMode = TerminalSelectionMode.NATURAL,
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
                    snippets = emptyList(),
                    onSendShellBytes = {},
                    onTerminalResize = { _, _ -> },
                    onSftpListDirectory = {},
                    onSftpDownload = onSftpDownload,
                    onSftpUpload = onSftpUpload,
                    onScpDownload = { _, _ -> },
                    onScpUpload = { _, _ -> },
                    onManageRemotePath = { _, _, _ -> },
                    onRetry = {},
                    onToggleConnectedHostBar = {},
                    onOpenSettings = {},
                    findRequestToken = 0
                )
            }
        }
    }

    private fun requestFor(mode: ConnectionMode) = QuickConnectRequest(
        sessionId = "session-${mode.name.lowercase()}",
        name = "Sandbox ${mode.name}",
        host = "127.0.0.1",
        port = 2222,
        username = "tester",
        auth = AuthMethod.PASSWORD,
        mode = mode
    )

    private fun UiDevice.isSoftKeyboardShown(): Boolean {
        val dump = executeShellCommand("dumpsys input_method")
        return dump.lineSequence().any { line ->
            line.contains("mInputShown=true")
        }
    }

    private fun hideTerminalKeyboardForTest(device: UiDevice) {
        if (device.isSoftKeyboardShown()) {
            device.pressBack()
        }
        if (isTerminalKeyboardRequested()) {
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_TOGGLE).performClick()
        }
    }

    private fun assertTerminalKeyboardRequested(device: UiDevice) {
        composeRule.waitUntil(5_000) {
            device.isSoftKeyboardShown() || isTerminalKeyboardRequested()
        }
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_STATE).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                KEYBOARD_REQUESTED_STATE
            )
        )
    }

    private fun isTerminalKeyboardRequested(): Boolean {
        return runCatching {
            composeRule.onNodeWithTag(UiTestTags.CONNECTING_KEYBOARD_STATE).assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    KEYBOARD_REQUESTED_STATE
                )
            )
            true
        }.getOrDefault(false)
    }

    private fun doubleTapTerminalPanel() {
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            .performTouchInput { doubleClick() }
    }

    private companion object {
        const val KEYBOARD_REQUESTED_STATE = "keyboard_requested"
    }
}
