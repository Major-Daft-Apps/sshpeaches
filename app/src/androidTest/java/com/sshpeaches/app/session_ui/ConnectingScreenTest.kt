package com.majordaftapps.sshpeaches.app.session_ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("download")).assertIsNotEnabled()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("new_folder")).assertIsEnabled()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("new_folder")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            check(downloadRequest == null) {
                "SCP download callback should not run before a file is selected."
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/subdir")).performClick()
        composeRule.runOnIdle {
            check(listedPath == null) {
                "Selecting a folder row should not navigate without the folder open affordance."
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/uploads/existing.txt")).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.connectingScpAction("download")).assertIsEnabled()
        composeRule.runOnIdle {
            check(downloadRequest == null) {
                "SCP download callback should remain idle until a document picker result is delivered"
            }
        }

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteOpen("/uploads/subdir")).performClick()
        composeRule.runOnIdle {
            check(listedPath == "/uploads/subdir") {
                "SCP browser did not request directory listing from the folder open affordance."
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

        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteOpen("/home/tester/docs")).performClick()
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

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.runOnIdle {
            check(listedPaths.lastOrNull() == "/home/tester") {
                "SCP Home button should reuse the canonical home path instead of requesting '.'"
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
