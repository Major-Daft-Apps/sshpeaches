package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.service.ConnectionLatency
import com.majordaftapps.sshpeaches.app.ui.screens.ConnectionLatencyBadge
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionLatencyBadgeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsTheRoundTripAndTheLastMinute() {
        val latency = ConnectionLatency(lastMs = 42, minMs = 38, avgMs = 45, maxMs = 61, jitterMs = 4, via = "SSH", samples = 12, lost = false)
        composeRule.setContent { ConnectionLatencyBadge(latency) }
        composeRule.onNodeWithText("42 ms").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CONNECTION_LATENCY_BADGE).performClick()
        composeRule.onNodeWithTag(UiTestTags.CONNECTION_LATENCY_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("fastest 38 ms, average 45 ms, slowest 61 ms", substring = true).assertIsDisplayed()
    }

    @Test
    fun sayswhenTheServerDoesntAnswer() {
        val lost = ConnectionLatency(lastMs = null, minMs = 40, avgMs = 40, maxMs = 40, jitterMs = 0, via = "Boost UDP", samples = 1, lost = true)
        composeRule.setContent { ConnectionLatencyBadge(lost) }
        composeRule.onNodeWithText("No answer").assertIsDisplayed()
    }

    @Test
    fun waitsForTheFirstCheck() {
        composeRule.setContent { ConnectionLatencyBadge(null) }
        composeRule.onNodeWithText("…").assertIsDisplayed()
    }
}
