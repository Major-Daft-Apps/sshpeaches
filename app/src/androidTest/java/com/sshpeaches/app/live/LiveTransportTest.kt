package com.majordaftapps.sshpeaches.app.live

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.KnownHostsSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.openQuickConnect
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.service.SessionService
import java.io.File
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveTransportSuiteTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun passwordQuickConnect_opensLiveTerminalSession() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Quick Connect Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun manualHostKeyPrompt_canBeAccepted() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = true,
            autoTrustHostKey = false,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        SshClientProvider.clearKnownHostEntry(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port
        )
        val host = seedPasswordHost("Live Manual Host Key")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.HOST_KEY_PROMPT_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.HOST_KEY_PROMPT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOST_KEY_PROMPT_ACCEPT).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun acceptAlwaysStoresHostTrustForLaterConnections() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = true,
            autoTrustHostKey = false,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        SshClientProvider.clearKnownHostEntry(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port
        )
        val host = seedPasswordHost("Live Trusted Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.HOST_KEY_PROMPT_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.HOST_KEY_PROMPT_ACCEPT_ALWAYS).performClick()
        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_CLOSE_BUTTON).performClick()

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()
        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)

        check(
            composeRule.onAllNodesWithTag(
                UiTestTags.HOST_KEY_PROMPT_DIALOG,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isEmpty()
        ) {
            "Trusted host should not prompt for host-key confirmation on the second connection"
        }
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun identityAuth_andTransferModesReachLivePanels() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val identityFixture = AppStateSeeder.generateIdentityFixture(username = LiveBackendConfig.keyUsername)
        AppStateSeeder.seedIdentity(
            identity = identityFixture.identity,
            privateKey = identityFixture.privateKey,
            publicKey = identityFixture.publicKey
        )
        val host = HostConnection(
            id = "live-${UUID.randomUUID()}",
            name = "Live Sandbox",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.keyUsername,
            preferredAuth = AuthMethod.IDENTITY,
            preferredIdentityId = identityFixture.identity.id,
            defaultMode = ConnectionMode.SSH
        )
        AppStateSeeder.seedHost(host)

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()
        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_CLOSE_BUTTON).performClick()

        val passwordHost = host.copy(
            id = "live-transfer-${UUID.randomUUID()}",
            name = "Live Transfer",
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            preferredIdentityId = null,
            hasPassword = true
        )
        AppStateSeeder.seedHost(passwordHost, LiveBackendConfig.password)

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(passwordHost.id, "sftp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SFTP_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_CLOSE_BUTTON).performClick()

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(passwordHost.id, "scp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SCP_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_PANEL).assertIsDisplayed()
    }

    @Test
    fun wrongPasswordShowsPasswordPromptForRetry() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val host = HostConnection(
            id = "live-wrong-password-${UUID.randomUUID()}",
            name = "Live Wrong Password",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true
        )
        AppStateSeeder.seedHost(host, "wrong-password")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.PASSWORD_PROMPT_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_DIALOG).assertIsDisplayed()
        composeRule.onAllNodesWithText("Authentication failed. Enter password and try again.")[0].assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_INPUT).assertIsDisplayed()
    }

    @Test
    fun rejectingHostKeyShowsRetryState() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = true,
            autoTrustHostKey = false
        )
        SshClientProvider.clearKnownHostEntry(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port
        )
        val host = seedPasswordHost("Live Reject Host Key")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.HOST_KEY_PROMPT_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.HOST_KEY_PROMPT_REJECT).performClick()
        waitForTag(UiTestTags.CONNECTING_RETRY_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun changedHostKeyShowsWarningPromptAndCanBeAccepted() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = true,
            autoTrustHostKey = false
        )
        SshClientProvider.clearKnownHostEntry(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port
        )
        KnownHostsSeeder.seedMismatchedHostKey(LiveBackendConfig.host, LiveBackendConfig.port)
        val host = seedPasswordHost("Live Changed Host Key")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.HOST_KEY_PROMPT_DIALOG)
        composeRule.onNodeWithText("Host Key Changed").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOST_KEY_PROMPT_ACCEPT).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun snippetCommandOutputIsSearchableInTerminalTranscript() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Snippet Host")
        AppStateSeeder.seedSnippet(
            Snippet(
                id = "live-snippet",
                title = "Kernel Check",
                command = "uname -a"
            )
        )

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(12)).performClick()
        waitForTag(UiTestTags.CONNECTING_SNIPPET_PICKER)
        composeRule.onNodeWithText("Kernel Check").performClick()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_INPUT).performTextInput("sshpeaches-live")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(UiTestTags.CONNECTING_FIND_STATUS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.config.toString().contains("sshpeaches-live") }
        }
        composeRule.onAllNodesWithText("sshpeaches-live", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun customKeyboardSequenceKeyWritesToLiveShellTranscript() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        AppStateSeeder.seedKeyboardLayout(
            KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
                this[0] = KeyboardLayoutDefaults.sequenceAction("Echo", "echo CUSTOM-KEY-LIVE\r")
            }
        )
        composeRule.activityRule.scenario.recreate()
        val host = seedPasswordHost("Live Custom Key Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.connectingCompactKey(0)).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_FIND_INPUT).performTextInput("CUSTOM-KEY-LIVE")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(UiTestTags.CONNECTING_FIND_STATUS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.config.toString().contains("CUSTOM-KEY-LIVE") }
        }
        composeRule.onAllNodesWithText("CUSTOM-KEY-LIVE", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun backgroundAndForegroundWhileTerminalSessionIsOpen_keepsConnectingRoute() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Background Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun recreateWhileTerminalSessionIsOpen_restoresConnectingRoute() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Recreate Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.activityRule.scenario.recreate()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL).assertIsDisplayed()
    }

    @Test
    fun sftpUploadAndDownloadStayInsideSandbox() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val host = seedPasswordHost("Live SFTP Host")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uploadFile = File(context.filesDir, "live-sftp-upload.txt").apply {
            writeText("sftp-live-upload")
        }
        val downloadFile = File(context.filesDir, "live-sftp-download.txt").apply {
            delete()
        }

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "sftp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SFTP_PANEL)

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("put ${uploadFile.absolutePath} /uploads/live-sftp.txt")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("ls /uploads")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        composeRule.onNodeWithText("live-sftp.txt", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("get /uploads/live-sftp.txt ${downloadFile.absolutePath}")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        composeRule.waitUntil(15_000) {
            downloadFile.exists() && downloadFile.readText() == "sftp-live-upload"
        }
        check(downloadFile.readText() == "sftp-live-upload") {
            "Unexpected SFTP download contents"
        }
    }

    @Test
    fun sftpRenameAndDeleteStayInsideSandbox() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val host = seedPasswordHost("Live SFTP Rename Host")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "rename-source-${System.currentTimeMillis()}.txt"
        val targetName = "rename-target-${System.currentTimeMillis()}.txt"
        val uploadFile = File(context.filesDir, sourceName).apply {
            writeText("rename-delete-live")
        }
        val renamedDownload = File(context.filesDir, "renamed-$targetName").apply {
            delete()
        }
        val deletedDownload = File(context.filesDir, "deleted-$targetName").apply {
            delete()
        }

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "sftp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SFTP_PANEL)

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("put ${uploadFile.absolutePath} /uploads/$sourceName")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        waitForEnabledTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("mv /uploads/$sourceName /uploads/$targetName")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        waitForEnabledTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("get /uploads/$targetName ${renamedDownload.absolutePath}")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        composeRule.waitUntil(15_000) {
            renamedDownload.exists() && renamedDownload.readText() == "rename-delete-live"
        }
        check(renamedDownload.readText() == "rename-delete-live") {
            "Renamed SFTP file could not be downloaded"
        }

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("rm /uploads/$targetName")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        Thread.sleep(2_000)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_CLOSE_BUTTON).performClick()
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "sftp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SFTP_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("get /uploads/$targetName ${deletedDownload.absolutePath}")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        Thread.sleep(2_000)
        check(!deletedDownload.exists()) {
            "Deleted SFTP file was still downloadable"
        }
    }

    @Test
    fun scpBrowserCanNavigateAndSelectFiles() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val host = seedPasswordHost("Live SCP Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "scp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SCP_PANEL)

        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_REMOTE_DIR_INPUT)
            .performTextReplacement("/uploads")
        composeRule.onAllNodesWithContentDescription("Go")[0].performClick()

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("📄", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onAllNodesWithText("📄", substring = true)[0].performClick()
        composeRule.onNodeWithText("Selected file:", substring = true).assertIsDisplayed()
    }


    @Test
    fun identityAuthWithWrongUsernameShowsRetryState() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val identityFixture = AppStateSeeder.generateIdentityFixture(
            label = "Wrong Identity",
            username = LiveBackendConfig.keyUsername
        )
        AppStateSeeder.seedIdentity(
            identity = identityFixture.identity,
            privateKey = identityFixture.privateKey,
            publicKey = identityFixture.publicKey
        )
        val host = HostConnection(
            id = "live-wrong-key-${UUID.randomUUID()}",
            name = "Live Wrong Identity",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = "${LiveBackendConfig.keyUsername}-wrong",
            preferredAuth = AuthMethod.IDENTITY,
            preferredIdentityId = identityFixture.identity.id,
            defaultMode = ConnectionMode.SSH
        )
        AppStateSeeder.seedHost(host)

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_RETRY_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun identityAuthWithInvalidPrivateKeyShowsRetryState() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        val identityFixture = AppStateSeeder.generateIdentityFixture(
            label = "Invalid Identity",
            username = LiveBackendConfig.keyUsername
        )
        AppStateSeeder.seedIdentity(
            identity = identityFixture.identity,
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ninvalid\n-----END OPENSSH PRIVATE KEY-----",
            publicKey = identityFixture.publicKey
        )
        val host = HostConnection(
            id = "live-invalid-key-${UUID.randomUUID()}",
            name = "Live Invalid Identity",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.keyUsername,
            preferredAuth = AuthMethod.IDENTITY,
            preferredIdentityId = identityFixture.identity.id,
            defaultMode = ConnectionMode.SSH
        )
        AppStateSeeder.seedHost(host)

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_RETRY_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_RETRY_BUTTON).assertIsDisplayed()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForEnabledTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun seedPasswordHost(name: String): HostConnection {
        val host = HostConnection(
            id = "live-${UUID.randomUUID()}",
            name = name,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true
        )
        AppStateSeeder.seedHost(host, LiveBackendConfig.password)
        return host
    }

}
