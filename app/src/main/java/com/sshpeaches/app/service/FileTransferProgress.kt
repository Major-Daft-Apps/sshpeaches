package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import java.util.Locale

enum class FileTransferDirection {
    DOWNLOAD,
    UPLOAD
}

data class FileTransferProgress(
    val sessionId: String,
    val mode: ConnectionMode,
    val direction: FileTransferDirection,
    val fileName: String,
    val sourceLabel: String,
    val destinationLabel: String,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long? = null,
    val hasStarted: Boolean = false
) {
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

    fun statusMessage(): String {
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
    val formatted = pattern.format(Locale.US, value)
        .trimEnd('0')
        .trimEnd('.')
    return "$formatted ${units[unitIndex]}"
}
