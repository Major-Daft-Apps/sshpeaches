package com.majordaftapps.sshpeaches.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.AboutDialog
import com.majordaftapps.sshpeaches.app.ui.components.AppDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.navigation.drawerDestinations
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExternalLinksNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun helpDrawerEntry_selectsHelpDestination() {
        val selectedRoute = AtomicReference<String?>(null)
        composeRule.setContent {
            AppDrawer(
                destinations = drawerDestinations,
                currentRoute = Routes.HOME,
                onDestinationSelected = { selectedRoute.set(it.route) },
                onQuickConnect = {}
            )
        }

        composeRule.onNodeWithTag(UiTestTags.drawerItem(Routes.HELP)).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            check(selectedRoute.get() == Routes.HELP) {
                "Expected Help drawer item to select ${Routes.HELP}, got ${selectedRoute.get()}."
            }
        }
    }

    @Test
    fun aboutDialog_linksInvokeExpectedCallbacks() {
        val lastAction = AtomicReference<String?>(null)
        composeRule.setContent {
            AboutDialog(
                onDismiss = {},
                onOpenWebsite = { lastAction.set("website") },
                onOpenSupport = { lastAction.set("support") },
                onOpenPrivacy = { lastAction.set("privacy") },
                onOpenSourceLicenses = { lastAction.set("licenses") }
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ABOUT_DIALOG).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.ABOUT_WEBSITE_LINK).performClick()
        composeRule.runOnIdle { check(lastAction.get() == "website") }

        composeRule.onNodeWithTag(UiTestTags.ABOUT_SUPPORT_LINK).performClick()
        composeRule.runOnIdle { check(lastAction.get() == "support") }

        composeRule.onNodeWithTag(UiTestTags.ABOUT_PRIVACY_LINK).performClick()
        composeRule.runOnIdle { check(lastAction.get() == "privacy") }

        composeRule.onNodeWithTag(UiTestTags.ABOUT_LICENSES_LINK).performClick()
        composeRule.runOnIdle { check(lastAction.get() == "licenses") }
    }
}
