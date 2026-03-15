package com.majordaftapps.sshpeaches.app.release

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.AboutDialog
import com.majordaftapps.sshpeaches.app.ui.PermissionRequiredDialog
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionRemediation
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class RtlSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun permissionDialogRendersInRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PermissionRequiredDialog(
                    missingCorePermissions = listOf(
                        CorePermissionStatus(
                            id = "notifications",
                            title = "Notifications",
                            description = "Allow session notifications and reconnect actions.",
                            granted = false,
                            remediation = CorePermissionRemediation.SETTINGS
                        )
                    ),
                    onManagePermissions = {},
                    onRequestNow = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Permissions Required").assertIsDisplayed()
        composeRule.onNodeWithText("- Notifications (enable in Settings)").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun aboutDialogRendersLinksInRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboutDialog(
                    onDismiss = {},
                    onOpenWebsite = {},
                    onOpenSupport = {},
                    onOpenPrivacy = {},
                    onOpenSourceLicenses = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ABOUT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ABOUT_WEBSITE_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ABOUT_SUPPORT_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ABOUT_PRIVACY_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ABOUT_LICENSES_LINK).assertIsDisplayed()
    }
}
