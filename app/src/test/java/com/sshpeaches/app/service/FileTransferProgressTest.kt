package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferProgressTest {

    @Test
    fun `status message stays transferring until bytes move`() {
        val progress = FileTransferProgress(
            sessionId = "session-1",
            mode = ConnectionMode.SCP,
            direction = FileTransferDirection.DOWNLOAD,
            fileName = "archive.tar",
            sourceLabel = "/tmp/archive.tar",
            destinationLabel = "Downloads/archive.tar",
            totalBytes = 1_048_576L,
            hasStarted = false
        )

        assertEquals("Downloading archive.tar: transferring...", progress.statusMessage())
        assertEquals("Transferring...", progress.progressSummary())
    }

    @Test
    fun `progress summary shows percent and byte counts once transfer starts`() {
        val progress = FileTransferProgress(
            sessionId = "session-1",
            mode = ConnectionMode.SFTP,
            direction = FileTransferDirection.UPLOAD,
            fileName = "video.mp4",
            sourceLabel = "video.mp4",
            destinationLabel = "/remote/video.mp4",
            bytesTransferred = 524_288L,
            totalBytes = 1_048_576L,
            hasStarted = true
        )

        assertEquals("Uploading video.mp4: 50% | 512 KB / 1 MB", progress.statusMessage())
        assertEquals("50% | 512 KB / 1 MB", progress.progressSummary())
    }

    @Test
    fun `byte formatter scales units compactly`() {
        assertEquals("999 B", formatByteCount(999))
        assertEquals("1.5 KB", formatByteCount(1_536))
        assertEquals("5 MB", formatByteCount(5L * 1024L * 1024L))
        assertEquals("1 GB", formatByteCount(1024L * 1024L * 1024L))
    }

    @Test
    fun `byte formatter preserves trailing zeroes in whole values`() {
        assertEquals("600 KB", formatByteCount(600L * 1024L))
        assertEquals("100 MB", formatByteCount(100L * 1024L * 1024L))
    }

    @Test
    fun `progress throttle publishes first timed and final updates`() {
        var now = 1_000L
        val throttle = FileTransferProgressThrottle(
            updateIntervalNanos = 100L,
            nanoTime = { now }
        )

        assertEquals(true, throttle.shouldPublish(bytesTransferred = 32L, totalBytes = 1_024L))
        now += 99L
        assertEquals(false, throttle.shouldPublish(bytesTransferred = 64L, totalBytes = 1_024L))
        now += 1L
        assertEquals(true, throttle.shouldPublish(bytesTransferred = 96L, totalBytes = 1_024L))
        assertEquals(true, throttle.shouldPublish(bytesTransferred = 1_024L, totalBytes = 1_024L))
    }

    @Test
    fun `terminal transfer exposes stable identity and truthful success`() {
        val progress = FileTransferProgress(
            sessionId = "session-1",
            mode = ConnectionMode.SFTP,
            direction = FileTransferDirection.DOWNLOAD,
            fileName = "report.pdf",
            sourceLabel = "/reports/report.pdf",
            destinationLabel = "Downloads/report.pdf",
            operationId = "operation-42",
            status = FileTransferStatus.SUCCEEDED,
            completedAtEpochMillis = 1234L
        )

        assertEquals("operation-42", progress.operationId)
        assertFalse(progress.isActive)
        assertTrue(progress.isTerminal)
        assertEquals("Download completed: report.pdf", progress.terminalMessage)
        assertEquals(progress.terminalMessage, progress.statusMessage())
        assertNull(progress.errorMessage)
    }

    @Test
    fun `failed transfer includes its terminal error`() {
        val progress = FileTransferProgress(
            sessionId = "session-1",
            mode = ConnectionMode.SCP,
            direction = FileTransferDirection.UPLOAD,
            fileName = "backup.zip",
            sourceLabel = "backup.zip",
            destinationLabel = "/backup.zip",
            operationId = "operation-43",
            status = FileTransferStatus.FAILED,
            errorMessage = "permission denied"
        )

        assertFalse(progress.isActive)
        assertEquals(
            "Upload failed for backup.zip: permission denied",
            progress.statusMessage()
        )
    }
}
