package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.service.FileTransferDirection
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.ui.screens.TransfersPane
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransfersPaneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: String, name: String, status: FileTransferStatus = FileTransferStatus.ACTIVE, block: FileTransferProgress.() -> FileTransferProgress = { this }) =
        FileTransferProgress(
            sessionId = "s", mode = ConnectionMode.SCP, direction = FileTransferDirection.DOWNLOAD, fileName = name,
            sourceLabel = "/srv/$name", destinationLabel = "Downloads/$name", operationId = id,
            totalBytes = 10L shl 20, status = status
        ).block()

    @Test
    fun listsTransfersLikeADownloadsPage() {
        val transfers = listOf(
            item("run", "running.bin") { copy(bytesTransferred = 5L shl 20, hasStarted = true, bytesPerSecond = 1L shl 20) },
            item("wait", "waiting.bin") { copy(waiting = true) },
            item("fail", "failed.bin", FileTransferStatus.FAILED) { copy(errorMessage = "permission denied", canRetry = true) },
            item("done", "done.bin", FileTransferStatus.SUCCEEDED) { copy(bytesTransferred = 10L shl 20, openUri = "content://x/done.bin") }
        )
        val cancelled = mutableListOf<String>()
        val retried = mutableListOf<String>()
        val opened = mutableListOf<String>()
        var cleared = 0
        composeRule.setContent {
            TransfersPane(
                transfers = transfers,
                onDismiss = {},
                onCancel = { cancelled += it },
                onRetry = { retried += it },
                onOpen = { opened += it.operationId },
                onClearFinished = { cleared++ }
            )
        }
        composeRule.onNodeWithText("In progress").assertIsDisplayed()
        composeRule.onNodeWithText("50% · 5 MB of 10 MB · 1 MB/s · 5 s left").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Finished").assertIsDisplayed()
        composeRule.onNodeWithText("Failed: permission denied").assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.transfersAction("wait", "cancel")).performClick()
        composeRule.onNodeWithTag(UiTestTags.transfersAction("fail", "retry")).performClick()
        composeRule.onNodeWithTag(UiTestTags.transfersAction("done", "open")).performClick()
        composeRule.onNodeWithTag(UiTestTags.TRANSFERS_CLEAR_FINISHED).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("wait"), cancelled)
            assertEquals(listOf("fail"), retried)
            assertEquals(listOf("done"), opened)
            assertEquals(1, cleared)
        }
        // Finished transfers can't be cancelled; running ones can't be retried.
        composeRule.onNodeWithTag(UiTestTags.transfersAction("done", "cancel")).assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.transfersAction("run", "retry")).assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysSo() {
        composeRule.setContent {
            TransfersPane(emptyList(), onDismiss = {}, onCancel = {}, onRetry = {}, onOpen = {}, onClearFinished = {})
        }
        composeRule.onNodeWithText("No transfers yet").assertIsDisplayed()
    }
}
