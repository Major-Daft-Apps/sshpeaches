package com.majordaftapps.sshpeaches.app.live

import android.Manifest
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalHostProbeTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun probeElasticTalksearchHost() {
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
        assertEquals("password_prompt", outcome)
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
}
