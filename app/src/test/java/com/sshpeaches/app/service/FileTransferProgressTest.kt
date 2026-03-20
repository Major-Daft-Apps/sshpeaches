package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import org.junit.Assert.assertEquals
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
}
