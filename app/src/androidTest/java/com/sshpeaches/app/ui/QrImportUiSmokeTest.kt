package com.majordaftapps.sshpeaches.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.ui.components.HostQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.PortForwardQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.SnippetQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.encodeHostPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeIdentityPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodePortForwardPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeSnippetPayload
import com.majordaftapps.sshpeaches.app.ui.components.processHostQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processIdentityQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processPortForwardQrImport
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
        composeRule.onNodeWithTag(UiTestTags.topBarAdd(Routes.HOSTS)).performClick()
        composeRule.onNodeWithTag(UiTestTags.HOST_DIALOG_CANCEL_BUTTON).performClick()
    }

    @Test
    fun invalidSnippetQrPayload_returnsError() {
        val result = processSnippetQrImport(contents = "{}")
        assertTrue(result is SnippetQrImportResult.Error)
        assertEquals("Invalid snippet QR", (result as SnippetQrImportResult.Error).message)
    }

    @Test
    fun validQrImportsBecomeVisibleAcrossManagersAfterRecreate() {
        val hostResult = processHostQrImport(
            contents = encodeHostPayload(
                HostConnection(
                    id = "qr-host",
                    name = "QR Imported Host",
                    host = "10.0.2.50",
                    port = 2222,
                    username = "qr-user",
                    preferredAuth = AuthMethod.PASSWORD,
                    defaultMode = ConnectionMode.SSH
                ),
                encryptedPasswordPayload = null
            ),
            existingHosts = emptyList()
        )
        assertTrue(hostResult is HostQrImportResult.Ready)
        AppStateSeeder.seedHost((hostResult as HostQrImportResult.Ready).data.host)

        val identityResult = processIdentityQrImport(
            contents = encodeIdentityPayload(
                Identity(
                    id = "qr-identity",
                    label = "QR Imported Identity",
                    fingerprint = "SHA256:qr-imported-identity",
                    username = "qr-identity-user",
                    createdEpochMillis = System.currentTimeMillis()
                ),
                encryptedKeyPayload = null
            ),
            existingIdentities = emptyList()
        )
        assertTrue(identityResult is IdentityQrImportResult.Ready)
        AppStateSeeder.seedIdentityRecord((identityResult as IdentityQrImportResult.Ready).data.identity)

        val forwardResult = processPortForwardQrImport(
            encodePortForwardPayload(
                PortForward(
                    id = "qr-forward",
                    label = "QR Imported Forward",
                    type = PortForwardType.LOCAL,
                    sourceHost = "127.0.0.1",
                    sourcePort = 8081,
                    destinationHost = "qr.internal",
                    destinationPort = 443
                )
            )
        )
        assertTrue(forwardResult is PortForwardQrImportResult.Ready)
        AppStateSeeder.seedPortForward((forwardResult as PortForwardQrImportResult.Ready).forward)

        val snippetResult = processSnippetQrImport(
            encodeSnippetPayload(
                Snippet(
                    id = "qr-snippet",
                    title = "QR Imported Snippet",
                    description = "Imported from QR",
                    command = "echo qr"
                )
            )
        )
        assertTrue(snippetResult is SnippetQrImportResult.Ready)
        val snippetData = (snippetResult as SnippetQrImportResult.Ready).data
        AppStateSeeder.seedSnippet(
            Snippet(
                id = "qr-snippet-seeded",
                title = snippetData.title,
                description = snippetData.description,
                command = snippetData.command
            )
        )

        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.HOSTS)
        composeRule.onNodeWithText("QR Imported Host").assertIsDisplayed()

        composeRule.navigateDrawer(Routes.IDENTITIES)
        composeRule.onNodeWithText("QR Imported Identity").assertIsDisplayed()

        composeRule.navigateDrawer(Routes.FORWARDS)
        composeRule.onNodeWithText("QR Imported Forward").assertIsDisplayed()

        composeRule.navigateDrawer(Routes.SNIPPETS)
        composeRule.onNodeWithText("QR Imported Snippet").assertIsDisplayed()
    }
}
