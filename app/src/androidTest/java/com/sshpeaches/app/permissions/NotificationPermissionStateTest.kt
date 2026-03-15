package com.majordaftapps.sshpeaches.app.permissions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.PermissionRequiredDialog
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionRemediation
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class NotificationPermissionStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun revokedNotifications_showPermissionsRequiredDialog() {
        composeRule.setContent {
            PermissionRequiredDialog(
                missingCorePermissions = listOf(notificationPermissionStatus()),
                onManagePermissions = {},
                onRequestNow = {}
            )
        }

        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Permissions Required").assertIsDisplayed()
        composeRule.onNodeWithText("- Notifications").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_REQUEST_BUTTON).assertIsDisplayed()
    }

    @Test
    fun managePermissionsButton_invokesCallback() {
        val manageClicked = AtomicBoolean(false)
        composeRule.setContent {
            PermissionRequiredDialog(
                missingCorePermissions = listOf(notificationPermissionStatus()),
                onManagePermissions = { manageClicked.set(true) },
                onRequestNow = {}
            )
        }

        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON).performClick()

        composeRule.runOnIdle {
            check(manageClicked.get()) { "Expected manage permissions callback to be invoked." }
        }
    }

    @Test
    fun requestNowButton_invokesCallback() {
        val requestClicked = AtomicBoolean(false)
        composeRule.setContent {
            PermissionRequiredDialog(
                missingCorePermissions = listOf(notificationPermissionStatus()),
                onManagePermissions = {},
                onRequestNow = { requestClicked.set(true) }
            )
        }

        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_REQUEST_BUTTON).performClick()

        composeRule.runOnIdle {
            check(requestClicked.get()) { "Expected request-now callback to be invoked." }
        }
    }

    @Test
    fun dialogListsAllMissingCorePermissions() {
        composeRule.setContent {
            PermissionRequiredDialog(
                missingCorePermissions = listOf(
                    notificationPermissionStatus(),
                    CorePermissionStatus(
                        id = "foreground_service",
                        title = "Foreground Service",
                        description = "Required to keep SSH sessions alive in background mode.",
                        granted = false,
                        remediation = CorePermissionRemediation.SETTINGS
                    )
                ),
                onManagePermissions = {},
                onRequestNow = {}
            )
        }

        composeRule.onNodeWithText("- Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("- Foreground Service (enable in Settings)").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun settingsOnlyPermissions_hideRequestNowAndExplainSettings() {
        composeRule.setContent {
            PermissionRequiredDialog(
                missingCorePermissions = listOf(
                    notificationPermissionStatus(remediation = CorePermissionRemediation.SETTINGS)
                ),
                onManagePermissions = {},
                onRequestNow = {}
            )
        }

        composeRule.onNodeWithText("enabled from system settings", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("- Notifications (enable in Settings)").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_REQUEST_BUTTON).assertDoesNotExist()
    }

    private fun notificationPermissionStatus(
        remediation: CorePermissionRemediation = CorePermissionRemediation.REQUEST
    ): CorePermissionStatus =
        CorePermissionStatus(
            id = "notifications",
            title = "Notifications",
            description = "Allow session notifications and reconnect actions.",
            granted = false,
            remediation = remediation
        )
}
