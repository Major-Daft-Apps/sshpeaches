package com.majordaftapps.sshpeaches.app.store

import android.graphics.Bitmap
import android.view.Gravity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.StoreScreenshotTest
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the Google Play phone screenshots: 1080x1920 (9:16) PNGs of the real app UI with
 * fictional demo data. Only the app's own window is resized (centred, clear of the system bars),
 * so no status bar appears and no device-global setting is changed. Session screenshots connect
 * to the live test SSH server (see LiveBackendConfig) whose sandbox holds the demo files.
 *
 * Output: <app files dir>/store-screenshots/<name>.png
 */
@RunWith(AndroidJUnit4::class)
@StoreScreenshotTest
class PlayStoreScreenshotsTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun useStoreWindowSize() {
        composeRule.runOnUiThread {
            composeRule.activity.window.setLayout(STORE_WIDTH_PX, STORE_HEIGHT_PX)
            composeRule.activity.window.setGravity(Gravity.CENTER)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun s1_terminal() {
        AppStateSeeder.configureSettings(hostKeyPrompt = false, autoTrustHostKey = true)
        val host = seedLiveHost("web-01")
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()
        waitForTag(UiTestTags.CONNECTING_TERMINAL_PANEL)
        withSessionService { service ->
            val sessionId = waitForActiveSessionId(service)
            // session.txt starts by clearing the screen (removing the test server's banner), then
            // shows a demo transcript; the test server's own prompt and cursor follow it.
            for (command in listOf("cd projects/api", "cat session.txt")) {
                service.sendShellInput(sessionId, "$command\r")
                Thread.sleep(700)
            }
        }
        capture("1-terminal")
    }

    @Test
    fun s2_hosts() {
        seedDemoHosts()
        composeRule.navigateDrawer(Routes.HOSTS)
        waitForTag(UiTestTags.SCREEN_HOSTS)
        capture("2-hosts")
    }

    @Test
    fun s3_file_browser() {
        AppStateSeeder.configureSettings(hostKeyPrompt = false, autoTrustHostKey = true)
        val host = seedLiveHost("web-01")
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "scp")).performClick()
        waitForTag(UiTestTags.CONNECTING_SCP_PANEL)
        Thread.sleep(3_000)
        capture("3-files")
    }

    @Test
    fun s4_identities() {
        seedDemoIdentities()
        composeRule.navigateDrawer(Routes.IDENTITIES)
        waitForTag(UiTestTags.SCREEN_IDENTITIES)
        capture("4-keys")
    }

    @Test
    fun s5_port_forwards() {
        seedDemoHosts()
        seedDemoForwards()
        composeRule.navigateDrawer(Routes.FORWARDS)
        waitForTag(UiTestTags.SCREEN_FORWARDS)
        capture("5-port-forwards")
    }

    @Test
    fun s6_snippets() {
        seedDemoSnippets()
        composeRule.navigateDrawer(Routes.SNIPPETS)
        waitForTag(UiTestTags.SCREEN_SNIPPETS)
        capture("6-snippets")
    }

    @Test
    fun s7_keyboard_editor() {
        composeRule.navigateDrawer(Routes.KEYBOARD)
        waitForTag(UiTestTags.SCREEN_KEYBOARD)
        capture("7-keyboard-editor")
    }

    @Test
    fun s8_theme_editor() {
        composeRule.navigateDrawer(Routes.THEME_EDITOR)
        waitForTag(UiTestTags.SCREEN_THEME_EDITOR)
        capture("8-theme-editor")
    }

    @Test
    fun z1_snippet_editor_light() = captureSnippetEditor(com.majordaftapps.sshpeaches.app.ui.state.ThemeMode.LIGHT, "z1-snippet-light")

    @Test
    fun z2_snippet_editor_dark() = captureSnippetEditor(com.majordaftapps.sshpeaches.app.ui.state.ThemeMode.DARK, "z2-snippet-dark")

    private fun captureSnippetEditor(mode: com.majordaftapps.sshpeaches.app.ui.state.ThemeMode, name: String) {
        AppStateSeeder.configureSettings(themeMode = mode)
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithContentDescription("Add snippet").performClick()
        waitForTag(UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT)
        Thread.sleep(2_000)
        composeRule.onNodeWithTag(UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT).performTextReplacement(
            """#!/usr/bin/env bash
# Rotate app logs and report disk use
set -euo pipefail
LOG_DIR="/var/log/app"
for f in "${"$"}LOG_DIR"/*.log; do
  if [ -s "${"$"}f" ]; then
    gzip -9 "${"$"}f" && echo "rotated ${"$"}{f##*/}"
  fi
done
df -h / | tail -n 1 | awk '{print ${"$"}5}'
"""
        )
        Thread.sleep(1_500)
        capture(name)
    }

    private fun seedLiveHost(name: String): HostConnection {
        val host = HostConnection(
            id = "store-${name}",
            name = name,
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD,
            osMetadata = OsMetadata.Known(OsFamily.UBUNTU, "24.04"),
            hasPassword = true
        )
        AppStateSeeder.seedHost(host, LiveBackendConfig.password)
        return host
    }

    private fun seedDemoHosts() {
        val now = System.currentTimeMillis()
        fun host(
            id: String, name: String, address: String, user: String, group: String?,
            os: OsMetadata, minutesAgo: Long, favorite: Boolean = false, mosh: Boolean = false
        ) = HostConnection(
            id = id, name = name, host = address, username = user, preferredAuth = AuthMethod.IDENTITY,
            group = group, osMetadata = os, favorite = favorite, useMosh = mosh,
            createdEpochMillis = now - 90L * DAY_MS, updatedEpochMillis = now - minutesAgo * 60_000,
            lastUsedEpochMillis = now - minutesAgo * 60_000
        )
        listOf(
            host("web-01", "web-01", "web-01.example.com", "deploy", "Cloud",
                OsMetadata.Known(OsFamily.UBUNTU, "24.04"), 4, favorite = true),
            host("web-02", "web-02", "web-02.example.com", "deploy", "Cloud",
                OsMetadata.Known(OsFamily.UBUNTU, "24.04"), 35),
            host("db-primary", "db-primary", "203.0.113.21", "postgres", "Cloud",
                OsMetadata.Known(OsFamily.DEBIAN, "12"), 180, favorite = true),
            host("build-box", "build-box", "198.51.100.14", "ci", "Home lab",
                OsMetadata.Known(OsFamily.FEDORA, "40"), 60 * 26),
            host("raspberry-pi", "raspberry-pi", "192.168.1.40", "pi", "Home lab",
                OsMetadata.Known(OsFamily.DEBIAN, "12"), 60 * 50, mosh = true),
            host("mac-mini", "mac-mini", "mac-mini.local", "alex", "Home lab",
                OsMetadata.Known(OsFamily.MAC, "15"), 60 * 72)
        ).forEach { AppStateSeeder.seedHost(it) }
    }

    private fun seedDemoIdentities() {
        val now = System.currentTimeMillis()
        listOf(
            Triple("Work laptop", "alex", true),
            Triple("Deploy key", "deploy", false),
            Triple("CI runner", "ci", false)
        ).forEachIndexed { index, (label, user, favorite) ->
            val generated = SshKeyGenerator.generate(
                IdentityKeyGenerationSpec(algorithm = IdentityKeyAlgorithm.ED25519, comment = "$user@example.com")
            )
            AppStateSeeder.seedIdentity(
                identity = Identity(
                    id = "store-identity-$index",
                    label = label,
                    fingerprint = generated.fingerprint,
                    username = user,
                    createdEpochMillis = now - (index + 1) * 40L * DAY_MS,
                    lastUsedEpochMillis = now - (index + 1) * 3L * 3_600_000,
                    favorite = favorite,
                    hasPrivateKey = true
                ),
                privateKey = generated.privateKey,
                publicKey = generated.publicKey
            )
        }
    }

    private fun seedDemoForwards() {
        val now = System.currentTimeMillis()
        listOf(
            PortForward(
                id = "fwd-postgres", label = "Postgres tunnel", group = "Databases",
                type = PortForwardType.LOCAL, sourcePort = 15432,
                destinationHost = "db-primary.internal", destinationPort = 5432,
                associatedHosts = listOf("web-01"), favorite = true, createdEpochMillis = now
            ),
            PortForward(
                id = "fwd-grafana", label = "Grafana dashboard", group = "Monitoring",
                type = PortForwardType.LOCAL, sourcePort = 3000,
                destinationHost = "127.0.0.1", destinationPort = 3000,
                associatedHosts = listOf("web-02"), createdEpochMillis = now
            ),
            PortForward(
                id = "fwd-home", label = "Home Assistant", group = "Home lab",
                type = PortForwardType.LOCAL, sourcePort = 8123,
                destinationHost = "192.168.1.10", destinationPort = 8123,
                associatedHosts = listOf("raspberry-pi"), createdEpochMillis = now
            )
        ).forEach { AppStateSeeder.seedPortForward(it) }
    }

    private fun seedDemoSnippets() {
        val now = System.currentTimeMillis()
        listOf(
            Snippet("snip-df", "Disk usage", "System", "Free space on every mount", "df -h", favorite = true),
            Snippet("snip-mem", "Memory", "System", "RAM and swap in use", "free -h"),
            Snippet("snip-log", "Follow system log", "System", "Live journal output", "journalctl -f"),
            Snippet("snip-nginx", "Reload nginx", "Web", "Test config, then reload", "sudo nginx -t && sudo systemctl reload nginx"),
            Snippet("snip-docker", "Running containers", "Docker", "Names and status", "docker ps --format '{{.Names}}: {{.Status}}'")
        ).forEach { AppStateSeeder.seedSnippet(it.copy(createdEpochMillis = now)) }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForActiveSessionId(service: SessionService): String {
        var sessionId: String? = null
        composeRule.waitUntil(30_000) {
            sessionId = service.sessionsFlow().value
                .firstOrNull { it.status == SessionService.SessionStatus.ACTIVE }?.hostId
            sessionId != null
        }
        Thread.sleep(1_000)
        return checkNotNull(sessionId)
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
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
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(10, TimeUnit.SECONDS)) { "Timed out binding SessionService" }
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }

    /** Screenshots the display and crops it to this activity's (resized) window. */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(1_200)
        val location = IntArray(2)
        var width = 0
        var height = 0
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            decor.getLocationOnScreen(location)
            width = decor.width
            height = decor.height
        }
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val cropped = Bitmap.createBitmap(screen, location[0], location[1], width, height)
        val dir = File(composeRule.activity.filesDir, "store-screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val STORE_WIDTH_PX = 1080
        const val STORE_HEIGHT_PX = 1920
        const val DAY_MS = 86_400_000L
    }
}
