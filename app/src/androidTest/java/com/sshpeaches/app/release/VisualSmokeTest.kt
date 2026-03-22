package com.majordaftapps.sshpeaches.app.release

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.AboutDialog
import com.majordaftapps.sshpeaches.app.ui.PermissionRequiredDialog
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionRemediation
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.testing.ReleaseTestHostActivity
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class VisualSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ReleaseTestHostActivity>()

    @Test
    fun permissionDialogRendersNonBlankLightTheme() {
        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.LIGHT) {
                PermissionRequiredDialog(
                    missingCorePermissions = listOf(
                        CorePermissionStatus(
                            id = "notifications",
                            title = "Notifications",
                            description = "Allow session notifications and reconnect actions.",
                            granted = false,
                            remediation = CorePermissionRemediation.REQUEST
                        )
                    ),
                    onManagePermissions = {},
                    onRequestNow = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.PERMISSION_REQUIRED_DIALOG).assertIsDisplayed()
        assertImageHasVisualContent(UiTestTags.PERMISSION_REQUIRED_DIALOG)
    }

    @Test
    fun aboutDialogRendersNonBlankDarkTheme() {
        composeRule.setContent {
            SSHPeachesTheme(themeMode = ThemeMode.DARK) {
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
        assertImageHasVisualContent(UiTestTags.ABOUT_DIALOG)
    }

    private fun assertImageHasVisualContent(tag: String) {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).captureToImage()
                true
            }.getOrDefault(false)
        }
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        check(image.width > 0 && image.height > 0) {
            "Captured image was empty."
        }
        val bitmap = image.asAndroidBitmap()
        val firstPixel = bitmap.getPixel(0, 0)
        var foundDifferentPixel = false
        val stepX = (bitmap.width / 12).coerceAtLeast(1)
        val stepY = (bitmap.height / 12).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height && !foundDifferentPixel) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    foundDifferentPixel = true
                    break
                }
                x += stepX
            }
            y += stepY
        }
        check(foundDifferentPixel) {
            "Captured image appears visually blank or monochrome."
        }
    }
}
