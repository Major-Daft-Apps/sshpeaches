package com.majordaftapps.sshpeaches.app.hosts

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.UUID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostsBrowserTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.waitForIdle()
        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()
    }

    @Test
    fun hostSearchFiltersByName() {
        AppStateSeeder.seedHost(host(name = "Alpha Sandbox", host = "10.0.2.10"))
        AppStateSeeder.seedHost(host(name = "Beta Sandbox", host = "10.0.2.11"))

        waitForHost("Alpha Sandbox")
        waitForHost("Beta Sandbox")

        composeRule.onNodeWithTag(UiTestTags.HOST_SEARCH_INPUT)
            .performTextReplacement("Alpha")

        composeRule.onNodeWithText("Alpha Sandbox").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Sandbox").assertCountEquals(0)
    }

    @Test
    fun hostSortSwitchesFromLastUsedToAlphabetical() {
        AppStateSeeder.seedHost(
            host(
                name = "Zulu Node",
                host = "10.0.2.20",
                lastUsedEpochMillis = 5_000L
            )
        )
        AppStateSeeder.seedHost(
            host(
                name = "Alpha Node",
                host = "10.0.2.21",
                lastUsedEpochMillis = 1_000L
            )
        )

        waitForHost("Zulu Node")
        waitForHost("Alpha Node")

        composeRule.onNodeWithTag(UiTestTags.HOST_SORT_FIELD).performClick()
        composeRule.onNodeWithText("Last Used").performClick()
        composeRule.waitForIdle()
        val zuluTopBefore = topOf("Zulu Node")
        val alphaTopBefore = topOf("Alpha Node")
        check(zuluTopBefore < alphaTopBefore) {
            "Expected last-used order to place Zulu Node before Alpha Node."
        }

        composeRule.onNodeWithTag(UiTestTags.HOST_SORT_FIELD).performClick()
        composeRule.onNodeWithText("Alphabetical").performClick()
        composeRule.waitForIdle()
        val zuluTopAfter = topOf("Zulu Node")
        val alphaTopAfter = topOf("Alpha Node")
        check(alphaTopAfter < zuluTopAfter) {
            "Expected alphabetical order to place Alpha Node before Zulu Node."
        }
    }

    @Test
    fun favoriteToggleAndHostActionsAreVisible() {
        val host = host(name = "Favorite Host", host = "10.0.2.30")
        AppStateSeeder.seedHost(host)

        waitForHost(host.name)

        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "sftp")).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "scp")).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "qr")).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "qr")).performClick()
        composeRule.onNodeWithText("Share ${host.name}").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithTag(UiTestTags.hostFavorite(host.id)).performClick()

        composeRule.navigateDrawer(Routes.HOME)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOME).assertIsDisplayed()
        composeRule.onNodeWithText(host.name).assertIsDisplayed()
    }

    @Test
    fun passwordProtectedQrRequiresPassphraseAndThenShowsQrDialog() {
        val host = host(name = "Secret Host", host = "10.0.2.40").copy(hasPassword = true)
        AppStateSeeder.seedHost(host, password = "secret-password")

        waitForHost(host.name)

        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "qr")).performClick()
        waitForTag(UiTestTags.HOST_EXPORT_PASSWORD_INPUT)

        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_INPUT).performTextInput("short")
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT).performTextInput("short")
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_BUTTON).performClick()
        composeRule.onNodeWithText(
            "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_INPUT).performTextInput("peaches-pass")
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT).performTextInput("mismatch-pass")
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_BUTTON).performClick()
        composeRule.onNodeWithText("Passphrases do not match.").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT).performTextInput("peaches-pass")
        composeRule.onNodeWithTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_BUTTON).performClick()

        waitForTag(UiTestTags.HOST_QR_DIALOG)
        composeRule.onNodeWithText("Share ${host.name}").assertIsDisplayed()
    }

    @Test
    fun sshActionNavigatesToConnectingScreenWithoutCrashing() {
        val host = host(name = "SSH Connect Host", host = "10.0.2.50").copy(hasPassword = true)
        AppStateSeeder.seedHost(host, password = "secret-password")

        waitForHost(host.name)

        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "ssh")).performClick()

        waitForTag(UiTestTags.SCREEN_CONNECTING)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
    }

    @Test
    fun scpActionNavigatesToConnectingScreenWithoutCrashing() {
        val host = host(name = "SCP Connect Host", host = "10.0.2.60").copy(hasPassword = true)
        AppStateSeeder.seedHost(host, password = "secret-password")

        waitForHost(host.name)

        composeRule.onNodeWithTag(UiTestTags.hostAction(host.id, "scp")).performClick()

        waitForTag(UiTestTags.SCREEN_CONNECTING)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_CONNECTING).assertIsDisplayed()
    }

    private fun waitForHost(name: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun topOf(text: String): Float =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top

    private fun host(
        name: String,
        host: String,
        lastUsedEpochMillis: Long? = null
    ) = HostConnection(
        id = "host-${UUID.randomUUID()}",
        name = name,
        host = host,
        port = 22,
        username = "tester",
        preferredAuth = AuthMethod.PASSWORD,
        lastUsedEpochMillis = lastUsedEpochMillis
    )
}
