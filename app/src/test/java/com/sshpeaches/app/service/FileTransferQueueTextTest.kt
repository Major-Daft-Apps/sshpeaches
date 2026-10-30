package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.ui.screens.transferRowDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FileTransferQueueTextTest {
    private fun transfer(
        bytes: Long = 0L,
        total: Long? = 100L shl 20,
        started: Boolean = true,
        rate: Long? = null,
        status: FileTransferStatus = FileTransferStatus.ACTIVE,
        waiting: Boolean = false,
        queued: Int = 0,
        error: String? = null
    ) = FileTransferProgress(
        sessionId = "s", mode = ConnectionMode.SCP, direction = FileTransferDirection.DOWNLOAD, fileName = "big.bin",
        sourceLabel = "/srv/big.bin", destinationLabel = "Downloads/big.bin", bytesTransferred = bytes,
        totalBytes = total, hasStarted = started, status = status, waiting = waiting, queuedTransfers = queued,
        bytesPerSecond = rate, errorMessage = error
    )

    @Test
    fun theCardNoLongerEndsWithAQueueCount() {
        val message = transfer(bytes = 50L shl 20, queued = 2).statusMessage()
        assertFalse(message, "queued" in message)
    }

    @Test
    fun speedAndTimeLeft() {
        val t = transfer(bytes = 60L shl 20, rate = 4L shl 20)
        assertEquals(10L, t.secondsLeft)
        assertEquals("4 MB/s · 10 s left", t.rateSummary())
        assertNull(transfer(rate = null).rateSummary())
        assertEquals("4 MB/s", transfer(total = null, rate = 4L shl 20).rateSummary())
    }

    @Test
    fun durations() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("3 min", formatDuration(180))
        assertEquals("3 min 20 s", formatDuration(200))
        assertEquals("1 h 5 min", formatDuration(3900))
        assertEquals("2 h", formatDuration(7200))
    }

    @Test
    fun rowDetails() {
        assertEquals("Waiting", transferRowDetail(transfer(waiting = true, started = false)))
        assertEquals("Starting…", transferRowDetail(transfer(started = false)))
        assertEquals(
            "50% · 50 MB of 100 MB · 5 MB/s · 10 s left",
            transferRowDetail(transfer(bytes = 50L shl 20, rate = 5L shl 20))
        )
        assertEquals("Done · 100 MB", transferRowDetail(transfer(status = FileTransferStatus.SUCCEEDED)))
        assertEquals("Failed: no space left", transferRowDetail(transfer(status = FileTransferStatus.FAILED, error = "no space left")))
        assertEquals("Cancelled", transferRowDetail(transfer(status = FileTransferStatus.CANCELLED)))
    }
}
