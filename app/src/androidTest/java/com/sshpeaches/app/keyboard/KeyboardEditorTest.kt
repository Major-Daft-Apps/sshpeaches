package com.majordaftapps.sshpeaches.app.keyboard

import android.Manifest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.navigateDrawer
import com.majordaftapps.sshpeaches.app.testutil.recreateActivity
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardEditorTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun keyboardSlotPersistsAcrossRecreate() {
        val customLayout = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[0] = KeyboardLayoutDefaults.textAction("ls -la")
        }
        AppStateSeeder.seedKeyboardLayout(customLayout)
        composeRule.activityRule.scenario.recreate()

        composeRule.navigateDrawer(Routes.KEYBOARD)
        composeRule.onNodeWithTag(UiTestTags.SCREEN_KEYBOARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).assertContentDescriptionEquals("ls -la")

        composeRule.recreateActivity()
        composeRule.navigateDrawer(Routes.KEYBOARD)
        composeRule.onNodeWithTag(UiTestTags.keyboardSlot(0)).assertContentDescriptionEquals("ls -la")
    }
}
