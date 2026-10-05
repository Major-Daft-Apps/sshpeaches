package com.majordaftapps.sshpeaches.app.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.transfer.LanTransfer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plays the other phone: receives the app's Wi-Fi export, then sends it back for the app to import. */
@RunWith(AndroidJUnit4::class)
class WifiTransferTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exportAndImportOverTheLocalNetwork() {
        AppStateSeeder.configureSettings(includeSecretsInQr = false)
        AppStateSeeder.seedHost(
            HostConnection(
                id = "wifi-host",
                name = "wifi-transfer-host",
                host = "192.0.2.10",
                username = "alice",
                preferredAuth = AuthMethod.PASSWORD
            )
        )
        composeRule.activityRule.scenario.recreate()
        composeRule.navigateDrawer(Routes.SETTINGS)

        // Export: Settings → Export → Wi-Fi → Start, then read the code and address off the screen.
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_EXPORT_BUTTON)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_WIFI_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_EXPORT_DIALOG)
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
        waitForTag(UiTestTags.SETTINGS_WIFI_SEND_DIALOG, timeoutMs = 20_000)
        val code = textOf(UiTestTags.SETTINGS_WIFI_SEND_CODE)
        val (host, port) = textOf(UiTestTags.SETTINGS_WIFI_SEND_ADDRESS).split(":").let { it[0] to it[1].toInt() }

        val payload = LanTransfer.receive(host, port, LanTransfer.normalizeCode(code)!!)
        assertTrue("received an export payload", payload.isNotBlank())
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_WIFI_SEND_DIALOG).fetchSemanticsNodes().isEmpty()
        }

        // Import: serve the same payload and let the app fetch it through Import → Wi-Fi.
        val address = checkNotNull(LanTransfer.localNetworkAddress()) { "emulator has no LAN address" }
        LanTransfer.Sender(payload, address).use { sender ->
            val pool = Executors.newSingleThreadExecutor()
            val served = pool.submit<java.net.InetAddress> { sender.awaitTransfer(60_000) }
            composeRule.revealSettingsControl(UiTestTags.SETTINGS_IMPORT_BUTTON)
            composeRule.waitUntil(15_000) {
                runCatching { composeRule.onNodeWithTag(UiTestTags.SETTINGS_IMPORT_BUTTON).performClick() }
                composeRule.waitForIdle()
                composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_TRANSFER_METHOD_DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_IMPORT_WIFI_BUTTON).performClick()
            waitForTag(UiTestTags.SETTINGS_WIFI_RECEIVE_DIALOG)
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_WIFI_RECEIVE_ADDRESS).performTextInput("${sender.host}:${sender.port}")
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_WIFI_RECEIVE_CODE).performTextInput(LanTransfer.formatCode(sender.code))
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_WIFI_RECEIVE_CONFIRM).performClick()
            served.get(60, TimeUnit.SECONDS)
            pool.shutdown()
        }
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText("Import complete", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The payload decoded and listed the seeded host, so the round trip carried real data.
        assertTrue(
            composeRule.onAllNodesWithText("1 hosts", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun textOf(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()

    private fun waitForTag(tag: String, timeoutMs: Long = 5_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
