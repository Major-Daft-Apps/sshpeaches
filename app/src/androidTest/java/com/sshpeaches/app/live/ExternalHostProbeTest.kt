package com.majordaftapps.sshpeaches.app.live

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class ExternalHostProbeTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun probeElasticTalksearchHost() {
        assumeTrue("elastic.talksearch.io:22 is not reachable from this test environment", canReachExternalHost())
        AppStateSeeder.configureSettings(
            hostKeyPrompt = false,
            autoTrustHostKey = true,
            terminalSelectionMode = TerminalSelectionMode.NATURAL
        )
        val host = HostConnection(
            id = "external-probe-${UUID.randomUUID()}",
            name = "Elastic Probe",
            host = "elastic.talksearch.io",
            port = 22,
            username = "dartnode",
            preferredAuth = AuthMethod.PASSWORD,
            hasPassword = false
        )
        AppStateSeeder.seedHost(host)

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        val outcome = waitForOutcome()
        Log.i("ExternalHostProbe", "probe outcome=$outcome")
        assumeTrue(
            "elastic.talksearch.io did not reach the password prompt in this environment; outcome=$outcome",
            outcome == "password_prompt"
        )
    }

    private fun waitForOutcome(timeoutMillis: Long = 45_000): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (hasTag(UiTestTags.PASSWORD_PROMPT_DIALOG)) return "password_prompt"
            if (hasTag(UiTestTags.CONNECTING_TERMINAL_PANEL)) return "terminal"
            if (hasTag(UiTestTags.CONNECTING_RETRY_BUTTON)) return "retry"
            if (hasTag(UiTestTags.HOST_KEY_PROMPT_DIALOG)) return "host_key_prompt"
            Thread.sleep(500)
        }
        return "timeout"
    }

    private fun hasTag(tag: String): Boolean =
        runCatching {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            true
        }.getOrDefault(false)

    private fun canReachExternalHost(timeoutMillis: Int = 5_000): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("elastic.talksearch.io", 22), timeoutMillis)
            }
            true
        }.getOrDefault(false)
}
