package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.service.ConnectionLatency
import com.majordaftapps.sshpeaches.app.ui.screens.ConnectionLatencyBadge
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithContentDescription("Connection latency no answer").assertIsDisplayed()
        composeRule.onNodeWithText("No answer").assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.CONNECTION_LATENCY_BADGE).performClick()
        composeRule.onNodeWithText("The last check got no answer within 5 seconds.").assertIsDisplayed()
    }

    @Test
    fun waitsForTheFirstCheck() {
        composeRule.setContent { ConnectionLatencyBadge(null) }
        composeRule.onNodeWithContentDescription("Connection latency not measured yet").assertIsDisplayed()
    }

    @Test
    fun keepsItsWidthWithOrWithoutAReading() {
        var latency by mutableStateOf<ConnectionLatency?>(null)
        composeRule.setContent { ConnectionLatencyBadge(latency) }
        val badge = composeRule.onNodeWithTag(UiTestTags.CONNECTION_LATENCY_BADGE)
        val blank = badge.fetchSemanticsNode().size.width
        val reading = ConnectionLatency(lastMs = 7, minMs = 7, avgMs = 7, maxMs = 7, jitterMs = 0, via = "SSH", samples = 1, lost = false)
        for (ms in listOf(7L, 42L, 288L, null)) {
            latency = reading.copy(lastMs = ms, lost = ms == null)
            composeRule.waitForIdle()
            assertEquals("width at $ms ms", blank, badge.fetchSemanticsNode().size.width)
        }
    }
}
