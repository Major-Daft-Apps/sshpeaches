package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The connecting screen's log panel must show lines logged after it first drew (0.12.0 showed none of them). */
@RunWith(AndroidJUnit4::class)
class SessionLogsStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun entry(hostId: String, message: String) =
        SessionLogBus.Entry(hostId = hostId, level = SessionLogBus.LogLevel.INFO, message = message)

    @Test
    fun newEntriesForTheSessionShowUp() {
        val all = mutableStateListOf(entry("a", "first line"), entry("b", "other session"))
        composeRule.setContent {
            val logs = rememberSessionLogs(all, "a")
            Text("shown: " + logs.joinToString(" / ") { it.message })
        }
        composeRule.onNodeWithText("shown: first line").assertExists()

        composeRule.runOnIdle {
            all += entry("a", "second line")
            all += entry("b", "still other")
        }
        composeRule.onNodeWithText("shown: first line / second line").assertExists()

        // The app keeps the newest 400 entries: removing old ones must show too.
        composeRule.runOnIdle { all.removeAt(0) }
        composeRule.onNodeWithText("shown: second line").assertExists()
    }
}
