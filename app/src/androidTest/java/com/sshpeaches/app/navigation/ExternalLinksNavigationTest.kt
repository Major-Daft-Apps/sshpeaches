package com.majordaftapps.sshpeaches.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.ui.AboutDialog
import com.majordaftapps.sshpeaches.app.ui.components.AppDrawer
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.navigation.drawerDestinations
import com.majordaftapps.sshpeaches.app.ui.help.HelpDestination
import com.majordaftapps.sshpeaches.app.ui.help.helpTopics
import com.majordaftapps.sshpeaches.app.ui.screens.HelpScreen
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
                onDestinationSelected = { selectedRoute.set(it.route) }
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
                onOpenSourceLicenses = { lastAction.set("licenses") },
                onOpenSourceCode = { lastAction.set("source") }
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

        composeRule.onNodeWithTag(UiTestTags.ABOUT_SOURCE_LINK).performClick()
        composeRule.runOnIdle { check(lastAction.get() == "source") }
    }

    @Test
    fun helpScreen_moreHelpInvokesSupportCallback() {
        val lastAction = AtomicReference<String?>(null)
        composeRule.setContent {
            HelpScreen(onOpenSupport = { lastAction.set("support") })
        }

        composeRule.onNodeWithTag(UiTestTags.HELP_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.helpTopic("connect")).assertIsDisplayed()
        // Header, one card per topic, then the support card.
        composeRule.onNodeWithTag(UiTestTags.HELP_SCREEN).performScrollToIndex(helpTopics.size + 1)
        composeRule.onNodeWithTag(UiTestTags.HELP_MORE_HELP_BUTTON).performClick()

        composeRule.runOnIdle { check(lastAction.get() == "support") }
    }

    @Test
    fun helpScreen_searchExpandsTheOnlyMatchAndItsButtonOpensTheScreen() {
        val opened = AtomicReference<HelpDestination?>(null)
        composeRule.setContent {
            HelpScreen(onOpenSupport = {}, onOpenDestination = { opened.set(it) })
        }

        composeRule.onNodeWithTag(UiTestTags.HELP_SEARCH).performTextInput("authorized_keys")
        composeRule.onNodeWithTag(UiTestTags.helpTopic("key-login")).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.helpTopic("connect")).assertDoesNotExist()
        composeRule.onNodeWithText("Install Key To Host", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.helpTopicAction("key-login")).performClick()

        composeRule.runOnIdle { check(opened.get() == HelpDestination.IDENTITIES) { "Opened ${opened.get()}" } }
    }

    @Test
    fun helpScreen_tappingAQuestionShowsItsSteps() {
        composeRule.setContent {
            HelpScreen(onOpenSupport = {})
        }

        composeRule.onNodeWithText("Long-press the terminal", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("How do I copy and paste in the terminal?").performClick()
        composeRule.onNodeWithText("Long-press the terminal", substring = true).assertIsDisplayed()
    }
}
