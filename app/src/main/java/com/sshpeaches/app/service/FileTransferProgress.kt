package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import java.util.Locale

enum class FileTransferDirection {
    DOWNLOAD,
    UPLOAD
}

enum class FileTransferStatus {
    ACTIVE,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class FileTransferProgress(
    val sessionId: String,
    val mode: ConnectionMode,
    val direction: FileTransferDirection,
    val fileName: String,
    val sourceLabel: String,
    val destinationLabel: String,
    val operationId: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long? = null,
    val hasStarted: Boolean = false,
    val status: FileTransferStatus = FileTransferStatus.ACTIVE,
    val errorMessage: String? = null,
    val completedAtEpochMillis: Long? = null,
    /** Transfers waiting behind this one in the session's queue. */
    val queuedTransfers: Int = 0,
    /** Why the transfer is waiting to retry (its connection dropped), or null while it moves. */
    val retryMessage: String? = null,
    /** Waiting for its turn: the session's earlier transfers run first. */
    val waiting: Boolean = false,
    /** Recent speed, smoothed; null until the transfer has moved for a moment. */
    val bytesPerSecond: Long? = null,
    /** A finished download's file the app can open (a content URI), when there is one. */
    val openUri: String? = null,
    /** A failed or cancelled transfer that can be started again. */
    val canRetry: Boolean = false
) {
    val isActive: Boolean
        get() = status == FileTransferStatus.ACTIVE

    val isTerminal: Boolean
        get() = !isActive

    val progressFraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { total ->
            (bytesTransferred.coerceIn(0L, total).toDouble() / total.toDouble()).toFloat()
        }

    val progressPercent: Int?
        get() = progressFraction?.let { fraction ->
            (fraction * 100f).toInt().coerceIn(0, 100)
        }

    val actionLabel: String
        get() = when (direction) {
            FileTransferDirection.DOWNLOAD -> "Downloading"
            FileTransferDirection.UPLOAD -> "Uploading"
        }

    val terminalMessage: String?
        get() {
            val name = fileName.ifBlank { "file" }
            val action = when (direction) {
                FileTransferDirection.DOWNLOAD -> "Download"
                FileTransferDirection.UPLOAD -> "Upload"
            }
            return when (status) {
                FileTransferStatus.ACTIVE -> null
                FileTransferStatus.SUCCEEDED -> "$action completed: $name"
                FileTransferStatus.FAILED -> {
                    val detail = errorMessage?.takeIf { it.isNotBlank() } ?: "unknown error"
                    "$action failed for $name: $detail"
                }
                FileTransferStatus.CANCELLED -> "$action cancelled: $name"
            }
        }

    fun statusMessage(): String {
        terminalMessage?.let { return it }
        val name = fileName.ifBlank { "file" }
        val status = when {
            retryMessage != null -> "$actionLabel $name: $retryMessage"
            !hasStarted -> "$actionLabel $name: transferring..."
            else -> "$actionLabel $name: ${progressSummary()}"
        }
        return status
    }

    /** Seconds left at the current speed, when both the size and the speed are known. */
    val secondsLeft: Long?
        get() {
            val total = totalBytes?.takeIf { it > 0L } ?: return null
            val rate = bytesPerSecond?.takeIf { it > 0L } ?: return null
            return ((total - bytesTransferred).coerceAtLeast(0L) + rate - 1) / rate
        }

    /** "4.1 MB/s · 28 s left", or what's known of it. */
    fun rateSummary(): String? {
        val rate = bytesPerSecond?.takeIf { it > 0L } ?: return null
        val left = secondsLeft?.let { " · ${formatDuration(it)} left" }.orEmpty()
        return "${formatByteCount(rate)}/s$left"
    }

    fun progressSummary(): String {
        if (!hasStarted) return "Transferring..."
        val transferred = formatByteCount(bytesTransferred)
        val total = totalBytes?.takeIf { it > 0L }?.let(::formatByteCount)
        val percent = progressPercent
        return when {
            total != null && percent != null -> "$percent% | $transferred / $total"
            total != null -> "$transferred / $total"
            else -> transferred
        }
    }
}

fun formatByteCount(bytes: Long): String {
    val clamped = bytes.coerceAtLeast(0L).toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = clamped
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val pattern = when {
        unitIndex == 0 -> "%.0f"
        value >= 100.0 -> "%.0f"
        value >= 10.0 -> "%.1f"
        else -> "%.2f"
    }
    val formatted = pattern.format(Locale.US, value).let { rendered ->
        if ('.' in rendered) {
            rendered.trimEnd('0').trimEnd('.')
        } else {
            rendered
        }
    }
    return "$formatted ${units[unitIndex]}"
}

internal class FileTransferProgressThrottle(
    private val updateIntervalNanos: Long,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var lastUpdateNanos: Long? = null

    fun shouldPublish(bytesTransferred: Long, totalBytes: Long?): Boolean {
        val complete = totalBytes != null &&
            totalBytes >= 0L &&
            bytesTransferred >= totalBytes
        val now = nanoTime()
        val previous = lastUpdateNanos
        if (previous == null || complete || now - previous >= updateIntervalNanos) {
            lastUpdateNanos = now
            return true
        }
        return false
    }
}

/** "45 s", "3 min 20 s", "1 h 5 min". */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60 -> "$s s"
        s < 3600 -> if (s % 60 == 0L) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        else -> if (s / 60 % 60 == 0L) "${s / 3600} h" else "${s / 3600} h ${s / 60 % 60} min"
    }
}
