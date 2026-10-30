package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.majordaftapps.sshpeaches.app.service.FileTransferDirection
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.formatByteCount
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

/**
 * A session's transfers, like a browser's downloads page: the running one and those waiting their turn, then the
 * finished ones, each with its own cancel, retry or open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersPane(
    transfers: List<FileTransferProgress>,
    onDismiss: () -> Unit,
    onCancel: (operationId: String) -> Unit,
    onRetry: (operationId: String) -> Unit,
    onOpen: (FileTransferProgress) -> Unit,
    onClearFinished: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val inProgress = transfers.filter { it.isActive }
        val finished = transfers.filter { it.isTerminal }
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag(UiTestTags.TRANSFERS_PANE),
            topBar = {
                TopAppBar(
                    title = { Text("Transfers") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close transfers")
                        }
                    },
                    actions = {
                        if (finished.isNotEmpty()) {
                            TextButton(
                                onClick = onClearFinished,
                                modifier = Modifier.testTag(UiTestTags.TRANSFERS_CLEAR_FINISHED)
                            ) { Text("Clear finished") }
                        }
                    }
                )
            }
        ) { padding ->
            if (transfers.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No transfers yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (inProgress.isNotEmpty()) {
                    item { TransfersSectionHeader("In progress") }
                    items(inProgress, key = { it.operationId }) { transfer ->
                        TransferRow(transfer, onCancel = { onCancel(transfer.operationId) })
                    }
                }
                if (finished.isNotEmpty()) {
                    item { TransfersSectionHeader("Finished") }
                    items(finished, key = { it.operationId }) { transfer ->
                        TransferRow(
                            transfer,
                            onRetry = { onRetry(transfer.operationId) }.takeIf { transfer.canRetry },
                            onOpen = { onOpen(transfer) }.takeIf {
                                transfer.status == FileTransferStatus.SUCCEEDED && transfer.openUri != null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransfersSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** What a transfer row says under the file name. */
internal fun transferRowDetail(transfer: FileTransferProgress): String {
    val total = transfer.totalBytes?.takeIf { it > 0L }?.let(::formatByteCount)
    return when (transfer.status) {
        FileTransferStatus.ACTIVE -> when {
            transfer.waiting -> "Waiting"
            transfer.retryMessage != null -> transfer.retryMessage
            !transfer.hasStarted -> "Starting…"
            else -> listOfNotNull(
                transfer.progressPercent?.let { "$it%" },
                if (total != null) "${formatByteCount(transfer.bytesTransferred)} of $total" else formatByteCount(transfer.bytesTransferred),
                transfer.rateSummary()
            ).joinToString(" · ")
        }
        FileTransferStatus.SUCCEEDED -> listOfNotNull("Done", total).joinToString(" · ")
        FileTransferStatus.FAILED -> "Failed: ${transfer.errorMessage ?: "unknown error"}"
        FileTransferStatus.CANCELLED -> "Cancelled"
    }
}

@Composable
private fun TransferRow(
    transfer: FileTransferProgress,
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().testTag(UiTestTags.transfersRow(transfer.operationId))) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (icon, tint) = when {
                    transfer.status == FileTransferStatus.SUCCEEDED -> Icons.Filled.CheckCircle to colors.primary
                    transfer.status == FileTransferStatus.FAILED -> Icons.Filled.ErrorOutline to colors.error
                    transfer.waiting -> Icons.Filled.Schedule to colors.onSurfaceVariant
                    transfer.direction == FileTransferDirection.DOWNLOAD -> Icons.Filled.CloudDownload to colors.onSurface
                    else -> Icons.Filled.CloudUpload to colors.onSurface
                }
                Icon(
                    icon,
                    contentDescription = if (transfer.direction == FileTransferDirection.DOWNLOAD) "Download" else "Upload",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(transfer.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (transfer.isActive && !transfer.waiting) {
                        val fraction = transfer.progressFraction
                        if (fraction != null && transfer.hasStarted) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Text(
                        transferRowDetail(transfer),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (transfer.status == FileTransferStatus.FAILED) colors.error else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${transfer.sourceLabel} → ${transfer.destinationLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                onOpen?.let {
                    IconButton(onClick = it, modifier = Modifier.testTag(UiTestTags.transfersAction(transfer.operationId, "open"))) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${transfer.fileName}")
                    }
                }
                onRetry?.let {
                    IconButton(onClick = it, modifier = Modifier.testTag(UiTestTags.transfersAction(transfer.operationId, "retry"))) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Try ${transfer.fileName} again")
                    }
                }
                onCancel?.let {
                    IconButton(onClick = it, modifier = Modifier.testTag(UiTestTags.transfersAction(transfer.operationId, "cancel"))) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel ${transfer.fileName}")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
