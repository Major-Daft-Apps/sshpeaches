package com.majordaftapps.sshpeaches.app.uptime

import android.Manifest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.local.asModel
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UptimeScreenTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openUptimeScreen() {
        composeRule.navigateDrawer(Routes.UPTIME)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_UPTIME).assertIsDisplayed()
    }

    @Test
    fun uptimeShowsSavedHostsEmptyStateWhenNoHostsExist() {
        openUptimeScreen()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_EMPTY_SAVED_HOSTS).assertIsDisplayed()
    }

    @Test
    fun addAndRemoveTrackedHostRequiresExplicitOptIn() {
        val host = host(name = "Tracked Host", address = "10.0.2.55")
        AppStateSeeder.seedHost(host)
        composeRule.activityRule.scenario.recreate()

        openUptimeScreen()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_EMPTY_MONITORS).assertIsDisplayed()

        AppStateSeeder.seedUptimeConfig(host.id)
        composeRule.activityRule.scenario.recreate()
        openUptimeScreen()

        waitForTag(UiTestTags.uptimeCard(host.id))
        composeRule.onNodeWithTag(UiTestTags.uptimeCard(host.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.uptimeRemove(host.id)).performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(UiTestTags.uptimeCard(host.id))
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(UiTestTags.UPTIME_EMPTY_MONITORS).assertIsDisplayed()
    }

    @Test
    fun addPickerOnlyShowsUntrackedHosts() {
        val tracked = host(name = "Already Tracked", address = "10.0.2.60")
        val available = host(name = "Available Host", address = "10.0.2.61")
        AppStateSeeder.seedHost(tracked)
        AppStateSeeder.seedHost(available)
        AppStateSeeder.seedUptimeConfig(tracked.id)
        composeRule.activityRule.scenario.recreate()

        openUptimeScreen()
        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.UPTIME)).performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_HOST_PICKER).performClick()

        composeRule.onNodeWithTag(UiTestTags.uptimeHostOption(available.id)).assertIsDisplayed()
    }

    @Test
    fun addButtonOpensDialogFromEmptyMonitorsState() {
        val host = host(name = "Available Host", address = "10.0.2.61")
        AppStateSeeder.seedHost(host)
        composeRule.activityRule.scenario.recreate()

        openUptimeScreen()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_EMPTY_MONITORS).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.UPTIME)).performClick()

        composeRule.onNodeWithTag(UiTestTags.UPTIME_HOST_PICKER).assertIsDisplayed()
    }

    @Test
    fun addTrackedHostThroughDialogPersistsMonitor() {
        val host = host(name = "Dialog Host", address = "10.0.2.62")
        AppStateSeeder.seedHost(host)
        composeRule.activityRule.scenario.recreate()

        openUptimeScreen()
        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.UPTIME)).performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_HOST_PICKER).performClick()
        composeRule.onNodeWithTag(UiTestTags.uptimeHostOption(host.id)).performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_INTERVAL_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_INTERVAL_INPUT).performTextInput("30")
        composeRule.onNodeWithTag(UiTestTags.UPTIME_SAVE_BUTTON).performClick()

        waitForTag(UiTestTags.uptimeCard(host.id))
        composeRule.onNodeWithTag(UiTestTags.uptimeCard(host.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.uptimeEnabled(host.id)).assertIsOn()

        val config = uptimeConfig(host.id)
        assertEquals(UptimeCheckMethod.TCP, config?.method)
        assertEquals(22, config?.port)
        assertEquals(30, config?.intervalMinutes)
        assertEquals(true, config?.enabled)
    }

    @Test
    fun editTrackedHostPersistsUpdatedConfig() {
        val host = host(name = "Editable Host", address = "10.0.2.63")
        AppStateSeeder.seedHost(host)
        AppStateSeeder.seedUptimeConfig(host.id)
        composeRule.activityRule.scenario.recreate()

        openUptimeScreen()
        waitForTag(UiTestTags.uptimeCard(host.id))
        composeRule.onNodeWithTag(UiTestTags.uptimeEdit(host.id)).performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_METHOD_FIELD).performClick()
        composeRule.onNodeWithTag(UiTestTags.uptimeMethodOption("ICMP"), useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_INTERVAL_INPUT).performTextClearance()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_INTERVAL_INPUT).performTextInput("45")
        composeRule.onNodeWithTag(UiTestTags.UPTIME_ENABLED_SWITCH).performClick()
        composeRule.onNodeWithTag(UiTestTags.UPTIME_SAVE_BUTTON).performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.uptimeEnabled(host.id)).assertIsOff()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(UiTestTags.uptimeCard(host.id)).assertIsDisplayed()

        val config = uptimeConfig(host.id)
        assertEquals(UptimeCheckMethod.ICMP, config?.method)
        assertEquals(22, config?.port)
        assertEquals(45, config?.intervalMinutes)
        assertFalse(config?.enabled ?: true)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun uptimeConfig(hostId: String) = runBlocking {
        SshPeachesDatabase.get(composeRule.activity).hostUptimeConfigDao().getByHostId(hostId)?.asModel()
    }

    private fun host(name: String, address: String) = HostConnection(
        id = "uptime-${UUID.randomUUID()}",
        name = name,
        host = address,
        port = 22,
        username = "tester",
        preferredAuth = AuthMethod.PASSWORD
    )
}
