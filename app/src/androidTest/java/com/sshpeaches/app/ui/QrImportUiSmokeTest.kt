package com.majordaftapps.sshpeaches.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.components.HostQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.SnippetQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.processHostQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processSnippetQrImport
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrImportUiSmokeTest {

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
    }

    @Test
    fun invalidHostQrPayload_returnsError_andUiRemainsUsable() {
        val result = processHostQrImport(
            contents = "not-a-valid-qr-payload",
            existingHosts = emptyList()
        )
        assertTrue(result is HostQrImportResult.Error)
        assertEquals("Invalid host QR", (result as HostQrImportResult.Error).message)

        composeRule.onNodeWithTag(UiTestTags.SCREEN_HOSTS).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HOST_ADD_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CANCEL_BUTTON).performClick()
    }

    @Test
    fun invalidSnippetQrPayload_returnsError() {
        val result = processSnippetQrImport(contents = "{}")
        assertTrue(result is SnippetQrImportResult.Error)
        assertEquals("Invalid snippet QR", (result as SnippetQrImportResult.Error).message)
    }
}
