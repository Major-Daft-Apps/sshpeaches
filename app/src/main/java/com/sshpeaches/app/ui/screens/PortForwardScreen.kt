package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.ui.components.EmptyState
import java.util.UUID

@Composable
fun PortForwardScreen(
    items: List<PortForward>,
    hosts: List<HostConnection> = emptyList(),
    editMode: Boolean = false,
    onAdd: (label: String, type: PortForwardType, bind: String, srcPort: Int, dstHost: String, dstPort: Int, enabled: Boolean, associatedHosts: List<String>) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onUpdate: (id: String, label: String, type: PortForwardType, bind: String, srcPort: Int, dstHost: String, dstPort: Int, enabled: Boolean, associatedHosts: List<String>) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onImportFromQr: () -> Unit = {}
) {
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
    val typeState = remember { mutableStateOf(PortForwardType.LOCAL) }
    val bindState = remember { mutableStateOf("127.0.0.1") }
    val srcPortState = remember { mutableStateOf("22") }
    val dstHostState = remember { mutableStateOf("") }
    val dstPortState = remember { mutableStateOf("0") }
    val enabledState = remember { mutableStateOf(true) }
    val selectedHostsState = remember { mutableStateOf<List<String>>(emptyList()) }
    val hostSearchState = remember { mutableStateOf(TextFieldValue("")) }

    fun openDialog(forward: PortForward?) {
        editingId.value = forward?.id
        labelState.value = forward?.label ?: ""
        typeState.value = forward?.type ?: PortForwardType.LOCAL
        bindState.value = forward?.sourceHost ?: "127.0.0.1"
        srcPortState.value = forward?.sourcePort?.toString() ?: "8080"
        dstHostState.value = forward?.destinationHost ?: ""
        dstPortState.value = forward?.destinationPort?.toString() ?: "0"
        enabledState.value = forward?.enabled ?: true
        selectedHostsState.value = forward?.associatedHosts ?: emptyList()
        hostSearchState.value = TextFieldValue("")
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = { openDialog(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Add port forward")
            }
            Button(onClick = onImportFromQr, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("Import QR")
            }
        }
        if (items.isEmpty()) {
            item { EmptyState(itemLabel = "port forward") }
        } else {
            items(items, key = { it.id }) { forward ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(forward.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (forward.type) {
                            PortForwardType.LOCAL -> "Local ${forward.sourceHost}:${forward.sourcePort} \u2192 ${forward.destinationHost}:${forward.destinationPort}"
                            PortForwardType.REMOTE -> "Remote ${forward.destinationHost}:${forward.destinationPort} \u2190 ${forward.sourceHost}:${forward.sourcePort}"
                            PortForwardType.DYNAMIC -> "Dynamic SOCKS on ${forward.sourceHost}:${forward.sourcePort}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (forward.associatedHosts.isNotEmpty()) {
                        val names = forward.associatedHosts.mapNotNull { id -> hosts.firstOrNull { it.id == id }?.name ?: id }
                        Text(
                            text = "Hosts: ${names.joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(
                            checked = forward.enabled,
                            onCheckedChange = {
                                onUpdate(
                                    forward.id,
                                    forward.label,
                                    forward.type,
                                    forward.sourceHost,
                                    forward.sourcePort,
                                    forward.destinationHost,
                                    forward.destinationPort,
                                    it,
                                    forward.associatedHosts
                                )
                            },
                            enabled = editMode
                        )
                        if (editMode) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier
                                    .clickable { openDialog(forward) }
                                    .padding(4.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .clickable { onDelete(forward.id) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showDialog.value) {
        val isEdit = editingId.value != null
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit port forward" else "Add port forward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        label = { Text("Label") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortForwardType.values().forEach { type ->
                            TextButton(onClick = { typeState.value = type }) {
                                Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = bindState.value,
                        onValueChange = { bindState.value = it },
                        label = { Text(if (typeState.value == PortForwardType.REMOTE) "Remote bind address" else "Local bind address") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = srcPortState.value,
                        onValueChange = { srcPortState.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (typeState.value == PortForwardType.REMOTE) "Remote listen port" else "Local port") },
                        singleLine = true
                    )
                    if (typeState.value != PortForwardType.DYNAMIC) {
                        OutlinedTextField(
                            value = dstHostState.value,
                            onValueChange = { dstHostState.value = it },
                            label = { Text("Destination host") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dstPortState.value,
                            onValueChange = { dstPortState.value = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Destination port") },
                            singleLine = true
                        )
                    } else {
                        Text("SOCKS proxy will be created on the local port above.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = enabledState.value, onCheckedChange = { enabledState.value = it })
                        Text("Enable now")
                    }
                    OutlinedTextField(
                        value = hostSearchState.value,
                        onValueChange = { hostSearchState.value = it },
                        label = { Text("Filter hosts") },
                        singleLine = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val filtered = hosts.filter {
                            it.name.contains(hostSearchState.value.text, ignoreCase = true) ||
                                    it.host.contains(hostSearchState.value.text, ignoreCase = true)
                        }
                        filtered.forEach { host ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val list = selectedHostsState.value.toMutableList()
                                        if (list.contains(host.id)) list.remove(host.id) else list.add(host.id)
                                        selectedHostsState.value = list
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Column {
                                    Text(host.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${host.host}:${host.port}", style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = selectedHostsState.value.contains(host.id),
                                    onCheckedChange = {
                                        val list = selectedHostsState.value.toMutableList()
                                        if (it == true && !list.contains(host.id)) list.add(host.id)
                                        if (it == false && list.contains(host.id)) list.remove(host.id)
                                        selectedHostsState.value = list
                                    }
                                )
                            }
                        }
                        if (filtered.isEmpty()) {
                            Text("No hosts match your search", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val srcPort = srcPortState.value.toIntOrNull() ?: 0
                    val dstPort = dstPortState.value.toIntOrNull() ?: 0
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            labelState.value.ifBlank { "Forward" },
                            typeState.value,
                            bindState.value.ifBlank { "127.0.0.1" },
                            srcPort,
                            dstHostState.value.ifBlank { "" },
                            dstPort,
                            enabledState.value,
                            selectedHostsState.value
                        )
                    } else {
                        onAdd(
                            labelState.value.ifBlank { "Forward ${UUID.randomUUID()}" },
                            typeState.value,
                            bindState.value.ifBlank { "127.0.0.1" },
                            srcPort,
                            dstHostState.value.ifBlank { "" },
                            dstPort,
                            enabledState.value,
                            selectedHostsState.value
                        )
                    }
                    closeDialog()
                }) { Text(if (isEdit) "Save" else "Add") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEdit) {
                        TextButton(onClick = {
                            onDelete(editingId.value!!)
                            closeDialog()
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { closeDialog() }) { Text("Cancel") }
                }
            }
        )
    }
}
