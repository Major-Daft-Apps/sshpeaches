package com.majordaftapps.sshpeaches.app.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.revealSettingsControl
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareExportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val resetRules: TestRule = RuleChain.outerRule(AppStateResetRule())
        .around(NotificationPermissionHelper.grantRule())

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun openingAReceivedExportFileAsksAndThenImportsIt() {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "received-export.json")
        file.writeText(
            """{"v":2,"hosts":[{"id":"shared-host","name":"shared over bluetooth","host":"192.0.2.44","port":22,"username":"bob","auth":"PASSWORD"}]}"""
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/json")
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            waitForTag(UiTestTags.SETTINGS_INCOMING_IMPORT_DIALOG, 20_000)
            composeRule.onAllNodesWithText("received-export.json", substring = true).fetchSemanticsNodes().let {
                assertTrue("dialog names the file", it.isNotEmpty())
            }
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_INCOMING_IMPORT_CONFIRM).performClick()
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodesWithText("Import complete", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val imported = runBlocking { SshPeachesDatabase.get(context).hostDao().getById("shared-host") }
        assertNotNull(imported)
        assertEquals("192.0.2.44", imported!!.host)
    }

    private fun waitForTag(tag: String, timeoutMs: Long) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ShareExportSheetTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shareHandsTheExportFileToTheShareSheet() {
        AppStateSeeder.configureSettings(includeSecretsInQr = false)
        composeRule.navigateDrawer(Routes.SETTINGS)
        composeRule.revealSettingsControl(UiTestTags.SETTINGS_EXPORT_BUTTON)
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_BUTTON).performClick()
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_SHARE_BUTTON).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(UiTestTags.SETTINGS_EXPORT_DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON).performClick()
            composeRule.waitUntil(20_000) {
                Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER }
            }
            intended(hasAction(Intent.ACTION_CHOOSER))
            val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("application/json", send.type)
            @Suppress("DEPRECATION")
            val stream = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val contents = composeRule.activity.contentResolver.openInputStream(stream)!!.use { it.readBytes() }
            assertTrue("shared file has the export", contents.isNotEmpty())
        } finally {
            Intents.release()
        }
    }
}
