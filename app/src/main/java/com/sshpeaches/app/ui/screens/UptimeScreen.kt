package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.UptimeBarBucketStatus
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UptimeScreen(
    hosts: List<HostConnection>,
    summaries: List<HostUptimeSummary>,
    addRequestKey: Int = 0,
    onAddHost: (String) -> Unit = {},
    onUpdateConfig: (String, UptimeCheckMethod, Int, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onSetEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveHost: (String) -> Unit = {},
    onRefreshHost: (String?) -> Unit = {},
) {
    val trackedHostIds = summaries.map { it.host.id }.toSet()
    val availableHosts = hosts.filterNot { trackedHostIds.contains(it.id) }.sortedBy { it.name.lowercase() }
    val editingSummary = remember { mutableStateOf<HostUptimeSummary?>(null) }
    val addingHostId = remember { mutableStateOf<String?>(null) }
    val dialogHostId = rememberSaveable { mutableStateOf("") }
    val dialogMethod = rememberSaveable { mutableStateOf(UptimeCheckMethod.TCP) }
    val dialogPort = rememberSaveable { mutableStateOf("22") }
    val dialogInterval = rememberSaveable { mutableStateOf("15") }
    val dialogEnabled = rememberSaveable { mutableStateOf(true) }
    val dialogError = rememberSaveable { mutableStateOf<String?>(null) }
    val showDialog = remember { mutableStateOf(false) }
    val hostMenuExpanded = remember { mutableStateOf(false) }
    val methodMenuExpanded = remember { mutableStateOf(false) }
    val handledAddRequestKey = rememberSaveable { mutableIntStateOf(0) }

    fun openAddDialog() {
        if (availableHosts.isEmpty()) return
        val host = availableHosts.first()
        addingHostId.value = host.id
        editingSummary.value = null
        dialogHostId.value = host.id
        dialogMethod.value = UptimeCheckMethod.TCP
        dialogPort.value = "22"
        dialogInterval.value = "15"
        dialogEnabled.value = true
        dialogError.value = null
        showDialog.value = true
    }

    fun openEditDialog(summary: HostUptimeSummary) {
        addingHostId.value = null
        editingSummary.value = summary
        dialogHostId.value = summary.host.id
        dialogMethod.value = summary.config.method
        dialogPort.value = summary.config.port.toString()
        dialogInterval.value = summary.config.intervalMinutes.toString()
        dialogEnabled.value = summary.config.enabled
        dialogError.value = null
        showDialog.value = true
    }

    LaunchedEffect(addRequestKey, availableHosts) {
        if (addRequestKey > handledAddRequestKey.intValue) {
            handledAddRequestKey.intValue = addRequestKey
            openAddDialog()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_UPTIME)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tracked hosts", style = MaterialTheme.typography.titleMedium)
                Text("${summaries.size} monitored", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (hosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.UPTIME_EMPTY_SAVED_HOSTS),
                contentAlignment = Alignment.Center
            ) {
                Text("Add a host in Hosts before tracking uptime.")
            }
            return
        }

        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.UPTIME_EMPTY_MONITORS),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No uptime monitors yet. Add a saved host to start tracking.",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            return
        }

        SummaryRow(summaries = summaries)
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(summaries, key = { it.host.id }) { summary ->
                UptimeCard(
                    summary = summary,
                    onRefresh = { onRefreshHost(summary.host.id) },
                    onEdit = { openEditDialog(summary) },
                    onRemove = { onRemoveHost(summary.host.id) },
                    onSetEnabled = { enabled -> onSetEnabled(summary.host.id, enabled) }
                )
            }
        }
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(if (editingSummary.value == null) "Add uptime host" else "Edit uptime host") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = hostMenuExpanded.value,
                        onExpandedChange = {
                            if (editingSummary.value == null) {
                                hostMenuExpanded.value = !hostMenuExpanded.value
                            }
                        }
                    ) {
                        val selectedHost = hosts.firstOrNull { it.id == dialogHostId.value }
                        TextField(
                            value = selectedHost?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Saved host") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag(UiTestTags.UPTIME_HOST_PICKER),
                            trailingIcon = {
                                if (editingSummary.value == null) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostMenuExpanded.value)
                                }
                            }
                        )
                        if (editingSummary.value == null) {
                            ExposedDropdownMenu(
                                expanded = hostMenuExpanded.value,
                                onDismissRequest = { hostMenuExpanded.value = false }
                            ) {
                                availableHosts.forEach { host ->
                                    DropdownMenuItem(
                                        text = { Text(host.name) },
                                        modifier = Modifier.testTag(UiTestTags.uptimeHostOption(host.id)),
                                        onClick = {
                                            dialogHostId.value = host.id
                                            hostMenuExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = methodMenuExpanded.value,
                        onExpandedChange = { methodMenuExpanded.value = !methodMenuExpanded.value }
                    ) {
                        TextField(
                            value = dialogMethod.value.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Method") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag(UiTestTags.UPTIME_METHOD_FIELD),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodMenuExpanded.value)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = methodMenuExpanded.value,
                            onDismissRequest = { methodMenuExpanded.value = false }
                        ) {
                            UptimeCheckMethod.values().forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.name) },
                                    onClick = {
                                        dialogMethod.value = method
                                        methodMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    if (dialogMethod.value == UptimeCheckMethod.TCP) {
                        OutlinedTextField(
                            value = dialogPort.value,
                            onValueChange = { dialogPort.value = it.filter(Char::isDigit) },
                            label = { Text("Port") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.UPTIME_PORT_INPUT)
                        )
                    }
                    OutlinedTextField(
                        value = dialogInterval.value,
                        onValueChange = { dialogInterval.value = it.filter(Char::isDigit) },
                        label = { Text("Interval (minutes)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.UPTIME_INTERVAL_INPUT)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled")
                        Switch(
                            checked = dialogEnabled.value,
                            onCheckedChange = { dialogEnabled.value = it },
                            modifier = Modifier.testTag(UiTestTags.UPTIME_ENABLED_SWITCH)
                        )
                    }
                    dialogError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hostId = dialogHostId.value
                        val port = dialogPort.value.toIntOrNull() ?: 22
                        val interval = dialogInterval.value.toIntOrNull()
                        when {
                            hostId.isBlank() -> dialogError.value = "Pick a saved host."
                            dialogMethod.value == UptimeCheckMethod.TCP && port !in 1..65_535 ->
                                dialogError.value = "Enter a valid TCP port."
                            interval == null || interval !in 1..60 ->
                                dialogError.value = "Interval must be between 1 and 60 minutes."
                            else -> {
                                if (editingSummary.value == null) {
                                    onAddHost(hostId)
                                }
                                onUpdateConfig(
                                    hostId,
                                    dialogMethod.value,
                                    port,
                                    interval,
                                    dialogEnabled.value
                                )
                                showDialog.value = false
                            }
                        }
                    },
                    modifier = Modifier.testTag(UiTestTags.UPTIME_SAVE_BUTTON)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog.value = false },
                    modifier = Modifier.testTag(UiTestTags.UPTIME_CANCEL_BUTTON)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(summaries: List<HostUptimeSummary>) {
    val upCount = summaries.count { it.currentStatus == UptimeStatus.UP }
    val downCount = summaries.count { it.currentStatus == UptimeStatus.DOWN }
    val greyCount = summaries.count { it.currentStatus == UptimeStatus.UNVERIFIED }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip("Up", upCount, Color(0xFF2E7D32), Modifier.weight(1f))
        SummaryChip("Down", downCount, Color(0xFFC62828), Modifier.weight(1f))
        SummaryChip("Grey", greyCount, Color(0xFF757575), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun UptimeCard(
    summary: HostUptimeSummary,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onSetEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(UiTestTags.uptimeCard(summary.host.id)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary.host.name, style = MaterialTheme.typography.titleMedium)
                    Text(summary.host.host, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${summary.config.method.name}${if (summary.config.method == UptimeCheckMethod.TCP) " • ${summary.config.port}" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${statusLabel(summary)} • Last checked ${summary.lastCheckedAt?.let(::formatTime).orEmpty().ifBlank { "never" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = summary.config.enabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag(UiTestTags.uptimeEnabled(summary.host.id))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("24h ${formatPercent(summary.uptime24hPercent)}")
                Text("7d ${formatPercent(summary.uptime7dPercent)}")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                summary.statusBars24h.forEach { bucket ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(bucketColor(bucket))
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag(UiTestTags.uptimeRefresh(summary.host.id))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag(UiTestTags.uptimeEdit(summary.host.id))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(UiTestTags.uptimeRemove(summary.host.id))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

private fun statusLabel(summary: HostUptimeSummary): String = when (summary.currentStatus) {
    UptimeStatus.UP -> "Up"
    UptimeStatus.DOWN -> "Down"
    UptimeStatus.UNVERIFIED -> when (summary.currentReason?.name) {
        "NO_INTERNET" -> "Grey • No internet"
        else -> "Grey • Checks inactive"
    }
}

private fun bucketColor(status: UptimeBarBucketStatus): Color = when (status) {
    UptimeBarBucketStatus.UP -> Color(0xFF4CAF50)
    UptimeBarBucketStatus.DOWN -> Color(0xFFE53935)
    UptimeBarBucketStatus.UNVERIFIED -> Color(0xFF9E9E9E)
    UptimeBarBucketStatus.NO_DATA -> Color(0xFFE0E0E0)
}

private fun formatPercent(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMillis))
