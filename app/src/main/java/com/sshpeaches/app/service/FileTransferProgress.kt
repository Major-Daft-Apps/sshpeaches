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
    val completedAtEpochMillis: Long? = null
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
        return if (!hasStarted) {
            "$actionLabel $name: transferring..."
        } else {
            "$actionLabel $name: ${progressSummary()}"
        }
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
