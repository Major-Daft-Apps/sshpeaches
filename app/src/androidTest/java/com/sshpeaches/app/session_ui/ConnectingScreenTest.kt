package com.majordaftapps.sshpeaches.app.session_ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.screens.ConnectingScreen
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectPhase
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectRequest
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectUiState
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.nio.charset.StandardCharsets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
                    keyboardSlots = KeyboardLayoutDefaults.DEFAULT_SLOTS,
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
    fun sftpPanel_runsCommandsAgainstUiCallbacks() {
        var listedPath: String? = null

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

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Commands: ls [path]", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("ls /uploads")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()

        composeRule.runOnIdle {
            check(listedPath == "/uploads") {
                "SFTP list callback did not receive the requested remote path"
            }
        }
    }

    @Test
    fun scpPanel_selectsRemoteFileAndValidatesDownloadAction() {
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
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON).performClick()
        composeRule.onNodeWithText("Select a file first.", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("📁 subdir", substring = true).performClick()
        composeRule.runOnIdle {
            check(listedPath == "/uploads/subdir") {
                "SCP browser did not request directory listing for selected folder"
            }
        }

        composeRule.onNodeWithText("📄 existing.txt", substring = true).performClick()
        composeRule.onNodeWithText("Selected file: /uploads/existing.txt", substring = true).assertIsDisplayed()

        composeRule.runOnIdle {
            check(downloadRequest == null) {
                "SCP download callback should not run before document picker returns"
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
        password = "secret",
        mode = mode
    )
}
