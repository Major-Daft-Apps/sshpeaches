package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
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
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionService
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveTransportSuiteTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

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
    fun terminalAndSftpRemainUsableAcrossRotation() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val baseHost = seedPasswordHost("Live Rotation Host")
        val terminalSessionId = "device-terminal-rotation-${UUID.randomUUID()}"
        startLiveSession(terminalSessionId, baseHost, ConnectionMode.SSH)
        openLiveSession(terminalSessionId)
        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        rotateAndAssert(UiTestTags.CONNECTING_TERMINAL_PANEL, terminalSessionId)
        assertSessionActive(terminalSessionId)
        withBoundSessionService { it.stopSession(terminalSessionId) }

        val sftpHost = baseHost.copy(
            id = "live-sftp-rotation-${UUID.randomUUID()}",
            name = "Live Rotation SFTP",
            defaultMode = ConnectionMode.SFTP
        )
        val sftpSessionId = "device-sftp-rotation-${UUID.randomUUID()}"
        startLiveSession(sftpSessionId, sftpHost, ConnectionMode.SFTP)
        openLiveSession(sftpSessionId)
        waitForTag(UiTestTags.CONNECTING_SFTP_PANEL)
        assertLiveSftpListing()
        rotateAndAssert(UiTestTags.CONNECTING_SFTP_PANEL, sftpSessionId)
        assertLiveSftpListing()
        assertSessionActive(sftpSessionId)
    }

    @Test
    fun terminalAndSftpSurviveConcurrentTrafficResizeLifecycleAndConnectionChurn() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val baseHost = seedPasswordHost("Live Combined Stress Host")
        val terminalSessionId = "device-terminal-stress-${UUID.randomUUID()}"
        val sftpSessionIds = (0 until 4).map { "device-sftp-stress-$it-${UUID.randomUUID()}" }
        val churnSessionIds = (0 until 8).map { "device-ssh-churn-$it-${UUID.randomUUID()}" }
        val ptySizes = listOf(80 to 24, 200 to 60, 40 to 12, 132 to 43, 320 to 100, 20 to 8)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stressRoot = File(context.cacheDir, "live-transport-stress-${UUID.randomUUID()}").apply { mkdirs() }
        val remoteNames = listOf(
            " leading-space.txt",
            "multiple   internal   spaces.txt",
            "unicode-雪-🍑.txt",
            "punctuation-[]#%+=,;'().bin"
        )
        val payloadSizes = listOf(0, 65_537, 2 * 1024 * 1024, 4 * 1024 * 1024)
        val sourceFiles = payloadSizes.mapIndexed { index, size ->
            File(stressRoot, "source-$index.bin").apply {
                outputStream().buffered().use { output ->
                    val block = ByteArray(8192) { offset -> ((offset + index * 37) and 0xff).toByte() }
                    var remaining = size
                    while (remaining > 0) {
                        val count = minOf(remaining, block.size)
                        output.write(block, 0, count)
                        remaining -= count
                    }
                }
            }
        }

        try {
            startLiveSession(terminalSessionId, baseHost, ConnectionMode.SSH)
            openLiveSession(terminalSessionId)
            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            sftpSessionIds.forEachIndexed { index, sessionId ->
                startLiveSession(
                    sessionId,
                    baseHost.copy(
                        id = "live-sftp-stress-host-$index-${UUID.randomUUID()}",
                        name = "Live SFTP Stress $index",
                        defaultMode = ConnectionMode.SFTP
                    ),
                    ConnectionMode.SFTP
                )
            }
            withBoundSessionService { service ->
                checkNotNull(service.resolveTerminalEmulator(terminalSessionId)) {
                    "Terminal emulator was unavailable before stress started"
                }
                repeat(60) { round ->
                    service.sendShellInput(terminalSessionId, "echo PRE-STRESS-$round\r")
                    val (columns, rows) = ptySizes[round % ptySizes.size]
                    service.resizeShell(terminalSessionId, columns, rows)
                }
                waitForShellMarker(service, terminalSessionId, "PRE-STRESS-59")
                sourceFiles.indices.forEach { index ->
                    service.sftpUploadFile(
                        sftpSessionIds[index],
                        sourceFiles[index].absolutePath,
                        "/uploads/${remoteNames[index]}"
                    )
                }
                repeat(40) { round ->
                    service.sendShellInput(terminalSessionId, "echo DURING-UPLOAD-$round\r")
                    val (columns, rows) = ptySizes[(round + 1) % ptySizes.size]
                    service.resizeShell(terminalSessionId, columns, rows)
                }
            }

            rotateAndAssert(UiTestTags.CONNECTING_TERMINAL_PANEL, terminalSessionId)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
            composeRule.activityRule.scenario.recreate()
            waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)

            withBoundSessionService { service ->
                waitForTransfers(service, sftpSessionIds, "upload")
                churnSessionIds.forEachIndexed { index, sessionId ->
                    service.startSession(
                        requestedSessionId = sessionId,
                        host = baseHost.copy(
                            id = "live-ssh-churn-host-$index-${UUID.randomUUID()}",
                            name = "Live SSH Churn $index"
                        ),
                        mode = ConnectionMode.SSH,
                        passwordOverride = LiveBackendConfig.password,
                        autoTrustUnknownHostKey = true,
                        hostKeyPromptEnabled = false
                    )
                }
                composeRule.waitUntil(45_000) {
                    val activeIds = service.sessionsFlow().value
                        .filter { it.status == SessionService.SessionStatus.ACTIVE }
                        .map { it.hostId }
                        .toSet()
                    churnSessionIds.all(activeIds::contains)
                }
                churnSessionIds.forEach(service::stopSession)
                sourceFiles.indices.forEach { index ->
                    service.sftpDownloadFile(
                        sftpSessionIds[index],
                        "/uploads/${remoteNames[index]}",
                        File(stressRoot, "download-$index.bin").absolutePath
                    )
                }
                repeat(60) { round ->
                    service.sendShellInput(terminalSessionId, "echo DURING-DOWNLOAD-$round\r")
                    val (columns, rows) = ptySizes[(round + 2) % ptySizes.size]
                    service.resizeShell(terminalSessionId, columns, rows)
                }
                waitForTransfers(service, sftpSessionIds, "download")
                service.sendShellInput(terminalSessionId, "echo TERMINAL-STILL-ALIVE\r")
                waitForShellMarker(service, terminalSessionId, "TERMINAL-STILL-ALIVE")
                checkNotNull(service.resolveTerminalEmulator(terminalSessionId)) {
                    "Terminal emulator disappeared during combined stress"
                }
            }
            sourceFiles.indices.forEach { index ->
                val downloaded = File(stressRoot, "download-$index.bin")
                check(downloaded.exists()) { "Missing SFTP download for ${remoteNames[index]}" }
                check(sourceFiles[index].readBytes().contentEquals(downloaded.readBytes())) {
                    "SFTP round-trip mismatch for ${remoteNames[index]}"
                }
            }
            assertSessionActive(terminalSessionId)
            sftpSessionIds.forEach(::assertSessionActive)
        } finally {
            withBoundSessionService { service ->
                (listOf(terminalSessionId) + sftpSessionIds + churnSessionIds).forEach(service::stopSession)
            }
            stressRoot.deleteRecursively()
        }
    }
    @Test
    fun sftpAndScpCancellationRestoreSessionsAndAllowImmediateRetry() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = true
        )
        val baseHost = seedPasswordHost("Live Cancellation Host")
        val sftpSessionId = "device-sftp-cancel-${UUID.randomUUID()}"
        val scpSessionId = "device-scp-cancel-${UUID.randomUUID()}"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "live-cancel-${UUID.randomUUID()}").apply { mkdirs() }
        val largeSource = File(root, "large-sparse.bin").also { file ->
            RandomAccessFile(file, "rw").use { it.setLength(192L * 1024L * 1024L) }
        }
        val retrySource = File(root, "retry-雪-🍑.txt").apply {
            writeText("transfer session recovered\n")
        }
        val sftpRemote = "/uploads/sftp-retry-${UUID.randomUUID()}.txt"
        val scpRemote = "/uploads/scp-retry-${UUID.randomUUID()}.txt"

        try {
            startLiveSession(
                sftpSessionId,
                baseHost.copy(
                    id = "live-sftp-cancel-host-${UUID.randomUUID()}",
                    name = "Live SFTP Cancellation",
                    defaultMode = ConnectionMode.SFTP
                ),
                ConnectionMode.SFTP
            )
            startLiveSession(
                scpSessionId,
                baseHost.copy(
                    id = "live-scp-cancel-host-${UUID.randomUUID()}",
                    name = "Live SCP Cancellation",
                    defaultMode = ConnectionMode.SCP
                ),
                ConnectionMode.SCP
            )

            withBoundSessionService { service ->
                service.sftpUploadFile(
                    sftpSessionId,
                    largeSource.absolutePath,
                    "/uploads/cancel-sftp-${UUID.randomUUID()}.bin"
                )
                service.scpUploadFile(
                    scpSessionId,
                    largeSource.absolutePath,
                    "/uploads/cancel-scp-${UUID.randomUUID()}.bin"
                )
                composeRule.waitUntil(30_000) {
                    listOf(sftpSessionId, scpSessionId).all { sessionId ->
                        service.fileTransferProgressFlow().value[sessionId]?.let {
                            it.isActive && it.hasStarted && it.bytesTransferred > 0L
                        } == true
                    }
                }
                check(service.cancelFileTransfer(sftpSessionId)) {
                    "SFTP cancellation was rejected after transfer started"
                }
                check(service.cancelFileTransfer(scpSessionId)) {
                    "SCP cancellation was rejected after transfer started"
                }
                waitForTransferStatus(service, sftpSessionId, FileTransferStatus.CANCELLED)
                waitForTransferStatus(service, scpSessionId, FileTransferStatus.CANCELLED)
                assertServiceSessionActive(service, sftpSessionId, "SFTP cancellation")
                assertServiceSessionActive(service, scpSessionId, "SCP cancellation")

                service.listSftpDirectory(sftpSessionId, "/docs")
                composeRule.waitUntil(30_000) {
                    service.remoteDirectoryFlow().value[sftpSessionId]?.let { snapshot ->
                        snapshot.path == "/docs" && snapshot.entries.any { it.name == "welcome.txt" }
                    } == true
                }

                service.sftpUploadFile(sftpSessionId, retrySource.absolutePath, sftpRemote)
                service.scpUploadFile(scpSessionId, retrySource.absolutePath, scpRemote)
                waitForTransfers(service, listOf(sftpSessionId, scpSessionId), "retry upload")

                service.sftpDownloadFile(
                    sftpSessionId,
                    sftpRemote,
                    File(root, "sftp-retry-download.txt").absolutePath
                )
                service.scpDownloadFile(
                    scpSessionId,
                    scpRemote,
                    File(root, "scp-retry-download.txt").absolutePath
                )
                waitForTransfers(service, listOf(sftpSessionId, scpSessionId), "retry download")
            }

            check(
                retrySource.readBytes().contentEquals(
                    File(root, "sftp-retry-download.txt").readBytes()
                )
            ) { "SFTP retry after cancellation corrupted the file" }
            check(
                retrySource.readBytes().contentEquals(
                    File(root, "scp-retry-download.txt").readBytes()
                )
            ) { "SCP retry after cancellation corrupted the file" }
            assertSessionActive(sftpSessionId)
            assertSessionActive(scpSessionId)
        } finally {
            withBoundSessionService { service ->
                service.stopSession(sftpSessionId)
                service.stopSession(scpSessionId)
            }
            root.deleteRecursively()
        }
    }

    @Test
    fun terminalSurvivesAnsiUnicodeAlternateScreenAndDeviceSleepWake() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Terminal Rendering Stress")
        val sessionId = "device-terminal-render-${UUID.randomUUID()}"
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")

        try {
            startLiveSession(sessionId, host, ConnectionMode.SSH)

            withBoundSessionService { service ->
                val emulator = checkNotNull(service.resolveTerminalEmulator(sessionId))
                service.sendShellInput(
                    sessionId,
                    "echo \u001B[?1049h\u001B[2J\u001B[1;1H\u001B[38;5;208mALT-SCREEN-雪-🍑\u001B[0m\r"
                )
                composeRule.waitUntil(20_000) {
                    emulator.isAlternateBufferActive &&
                        emulator.screen.transcriptText.contains("ALT-SCREEN-雪-🍑")
                }
                repeat(300) { line ->
                    service.sendShellInput(
                        sessionId,
                        "echo \u001B[3${line % 8}mANSI-$line-界-é-\u001B[0m\r"
                    )
                    val columns = listOf(20, 40, 80, 132, 200, 320)[line % 6]
                    val rows = listOf(8, 12, 24, 43, 60, 100)[line % 6]
                    service.resizeShell(sessionId, columns, rows)
                }
                service.sendShellInput(
                    sessionId,
                    "echo \u001B[38;2;255;128;0mALT-FINAL-雪-🍑\u001B[0m\r"
                )
                composeRule.waitUntil(30_000) {
                    emulator.screen.transcriptText.contains("ALT-FINAL-雪-🍑")
                }
                service.sendShellInput(sessionId, "echo \u001B[?1049lMAIN-BUFFER-RESTORED\r")
                composeRule.waitUntil(20_000) {
                    !emulator.isAlternateBufferActive &&
                        emulator.screen.transcriptText.contains("MAIN-BUFFER-RESTORED")
                }
                assertServiceSessionActive(service, sessionId, "alternate-screen stress")
            }

            check(device.pressHome()) { "Could not background the app before sleep/wake stress" }
            SystemClock.sleep(1_000)
            device.sleep()
            SystemClock.sleep(4_000)
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.pressMenu()


            withBoundSessionService { service ->
                service.sendShellInput(sessionId, "echo POST-SLEEP-TERMINAL-ALIVE\r")
                waitForShellMarker(service, sessionId, "POST-SLEEP-TERMINAL-ALIVE")
                check(
                    service.resolveTerminalEmulator(sessionId)
                        ?.screen
                        ?.transcriptText
                        ?.contains("POST-SLEEP-TERMINAL-ALIVE") == true
                ) { "Terminal emulator did not render output after device sleep/wake" }
            }
            assertSessionActive(sessionId)
        } finally {
            runCatching {
                device.wakeUp()
                device.executeShellCommand("wm dismiss-keyguard")
            }
            withBoundSessionService { it.stopSession(sessionId) }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
            SystemClock.sleep(500)
        }
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
        waitForTag(UiTestTags.CONNECTING_SCP_PANEL)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_PANEL).assertIsDisplayed()
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
        check(
            composeRule.onAllNodesWithText("Authentication failed. Enter password and try again.", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) { "Retry explanation was missing from the password dialog semantics." }
        composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_INPUT).assertIsDisplayed()
    }

    @Test
    fun wrongPasswordShowsPasswordPromptForRetryWhenDhKeyPairGeneratorUnavailable() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true
        )
        SshClientProvider.withTestingUnavailableKeyExchangeAlgorithms(setOf("DH")) {
            val host = HostConnection(
                id = "live-wrong-password-dh-${UUID.randomUUID()}",
                name = "Live Wrong Password with DH fallback",
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
            check(
            composeRule.onAllNodesWithText("Authentication failed. Enter password and try again.", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) { "Retry explanation was missing from the password dialog semantics." }
            composeRule.onNodeWithTag(UiTestTags.PASSWORD_PROMPT_INPUT).assertIsDisplayed()
        }
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

        AppStateSeeder.seedKeyboardLayout(
            KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
                this[12] = KeyboardLayoutDefaults.snippetPickerAction()
            }
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
    fun foregroundAfterBackgroundClosesTerminalSession_returnsHomeWithoutStaleTerminal() {
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            allowBackgroundSessions = false,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = seedPasswordHost("Live Closed In Background Host")

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SCREEN_HOME, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(UiTestTags.SCREEN_HOSTS, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        check(
            composeRule.onAllNodesWithTag(UiTestTags.SCREEN_CONNECTING, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        ) { "Stale connecting screen was still composed after the backing session closed." }
        check(
            composeRule.onAllNodesWithTag(UiTestTags.CONNECTING_TERMINAL_PANEL, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        ) { "Stale terminal panel was still composed after the backing session closed." }
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
    fun localPortForward_canFetchHttpResponseTwice() {
        assumeTrue(
            "This live forward test requires localhost SSH routing via adb reverse.",
            LiveBackendConfig.host == "127.0.0.1"
        )
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val forwardedPort = 18080
        val host = HostConnection(
            id = "live-forward-${UUID.randomUUID()}",
            name = "Live Forward Host",
            host = "127.0.0.1",
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = true
        )
        AppStateSeeder.seedHost(host, LiveBackendConfig.password)
        AppStateSeeder.seedPortForward(
            PortForward(
                id = "live-forward-config-${UUID.randomUUID()}",
                label = "Live HTTP Forward",
                type = PortForwardType.LOCAL,
                sourceHost = "127.0.0.1",
                sourcePort = forwardedPort,
                destinationHost = host.host,
                destinationPort = LiveBackendConfig.forwardHttpPort,
                associatedHosts = listOf(host.id),
                enabled = true
            )
        )

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        val response = waitForForwardedHttpResponse(forwardedPort)
        check(response.contains("SSHPEACHES_FORWARD_OK")) {
            "Expected forwarded HTTP response but got: $response"
        }
        val secondResponse = waitForForwardedHttpResponse(forwardedPort)
        check(secondResponse.contains("SSHPEACHES_FORWARD_OK")) {
            "Expected forwarded HTTP response on second request but got: $secondResponse"
        }
    }

    @Ignore("SFTP host action was replaced by SCP upload/download.")
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

    @Ignore("SFTP host action was replaced by SCP upload/download.")
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

        repeat(3) {
            composeRule.onNodeWithContentDescription("Up").performClick()
            Thread.sleep(750)
        }

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(UiTestTags.connectingScpRemoteRow("/docs"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/docs")).performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(UiTestTags.connectingScpRemoteRow("/docs/welcome.txt"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SCP_REMOTE_DIR_INPUT)
            .assertTextContains("/docs")
        composeRule.onNodeWithTag(UiTestTags.connectingScpRemoteRow("/docs/welcome.txt"))
            .performClick()
        check(
            composeRule.onAllNodesWithText(
                "Selected: /docs/welcome.txt",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        ) { "SCP file selection state was missing after selecting /docs/welcome.txt." }    }

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

    private fun startLiveSession(sessionId: String, host: HostConnection, mode: ConnectionMode) {
        withBoundSessionService { service ->
            service.startSession(
                requestedSessionId = sessionId,
                host = host,
                mode = mode,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false
            )
            composeRule.waitUntil(30_000) {
                service.sessionsFlow().value.any {
                    it.hostId == sessionId && it.status == SessionService.SessionStatus.ACTIVE
                }
            }
        }
    }


    private fun openLiveSession(sessionId: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    action = SessionService.ACTION_OPEN_SESSION
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(SessionService.EXTRA_HOST_ID, sessionId)
                }
            )
        }
    }

    private fun assertSessionActive(sessionId: String) {
        withBoundSessionService { service ->
            check(
                service.sessionsFlow().value.any {
                    it.hostId == sessionId && it.status == SessionService.SessionStatus.ACTIVE
                }
            ) { "Session $sessionId was not active after rotation" }
        }
    }

    private fun rotateAndAssert(tag: String, sessionId: String) {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        waitForTag(tag)
        assertSessionActive(sessionId)
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        waitForTag(tag)
        assertSessionActive(sessionId)
    }

    private fun assertLiveSftpListing() {
        waitForEnabledTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
            .performTextReplacement("ls /docs")
        composeRule.onNodeWithTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON).performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("welcome.txt", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithText("welcome.txt", substring = true)[0].assertIsDisplayed()
    }

    private fun waitForTransferStatus(
        service: SessionService,
        sessionId: String,
        status: FileTransferStatus,
        timeoutMillis: Long = 45_000
    ) {
        composeRule.waitUntil(timeoutMillis) {
            service.fileTransferProgressFlow().value[sessionId]?.status == status
        }
        val progress = checkNotNull(service.fileTransferProgressFlow().value[sessionId])
        check(progress.status == status) {
            "Expected $status for $sessionId, got ${progress.status}: ${progress.errorMessage}"
        }
    }

    private fun assertServiceSessionActive(
        service: SessionService,
        sessionId: String,
        operation: String
    ) {
        check(
            service.sessionsFlow().value.any {
                it.hostId == sessionId && it.status == SessionService.SessionStatus.ACTIVE
            }
        ) { "Session $sessionId died during $operation" }
    }
    private fun waitForShellMarker(
        service: SessionService,
        sessionId: String,
        marker: String,
        timeoutMillis: Long = 30_000
    ) {
        composeRule.waitUntil(timeoutMillis) {
            service.shellOutputFlow().value[sessionId]?.contains(marker) == true
        }
        check(
            service.sessionsFlow().value.any {
                it.hostId == sessionId && it.status == SessionService.SessionStatus.ACTIVE
            }
        ) { "Terminal session $sessionId died before marker $marker" }
    }

    private fun waitForTransfers(
        service: SessionService,
        sessionIds: List<String>,
        operation: String,
        timeoutMillis: Long = 90_000
    ) {
        composeRule.waitUntil(timeoutMillis) {
            sessionIds.all { service.fileTransferProgressFlow().value[it]?.isTerminal == true }
        }
        sessionIds.forEach { sessionId ->
            val progress = checkNotNull(service.fileTransferProgressFlow().value[sessionId]) {
                "Missing $operation progress for $sessionId"
            }
            check(progress.status == FileTransferStatus.SUCCEEDED) {
                "$operation failed for ${progress.fileName}: ${progress.errorMessage ?: progress.status}"
            }
            check(
                service.sessionsFlow().value.any {
                    it.hostId == sessionId && it.status == SessionService.SessionStatus.ACTIVE
                }
            ) { "SFTP session $sessionId died after $operation" }
        }
    }
    private fun withBoundSessionService(block: (SessionService) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService()
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            "Could not bind SessionService"
        }
        try {
            check(connected.await(10, TimeUnit.SECONDS)) { "Timed out binding SessionService" }
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
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

    private fun waitForForwardedHttpResponse(forwardedPort: Int, timeoutMillis: Long = 20_000): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", forwardedPort), 2_000)
                    socket.soTimeout = 2_000
                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.apply {
                        writer.write("GET /health HTTP/1.1\r\n")
                        writer.write("Host: 127.0.0.1\r\n")
                        writer.write("Connection: close\r\n")
                        writer.write("\r\n")
                        writer.flush()
                    }
                    val buffer = ByteArray(1_024)
                    val response = StringBuilder()
                    val input = socket.getInputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        response.append(String(buffer, 0, read))
                        if (response.contains("SSHPEACHES_FORWARD_OK")) {
                            return response.toString()
                        }
                    }
                    return response.toString()
                }
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(500)
            }
        }
        throw AssertionError("Timed out waiting for forwarded HTTP response", lastError)
    }

}
