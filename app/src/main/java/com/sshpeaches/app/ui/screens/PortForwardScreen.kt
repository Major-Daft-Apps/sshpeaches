package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.ui.components.DeleteConfirmationDialog
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.GroupSectionHeader
import com.majordaftapps.sshpeaches.app.ui.components.generateForwardQr
import com.majordaftapps.sshpeaches.app.ui.components.PortForwardQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.buildGroupedSections
import com.majordaftapps.sshpeaches.app.ui.components.processPortForwardQrImport
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.rememberDialogBodyMaxHeight
import com.majordaftapps.sshpeaches.app.util.isValidHostAddress
import com.majordaftapps.sshpeaches.app.util.parsePort
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardScreen(
    items: List<PortForward>,
    hosts: List<HostConnection> = emptyList(),
    addRequestKey: Int = 0,
    importRequestKey: Int = 0,
    onAdd: (label: String, group: String?, type: PortForwardType, bind: String, srcPort: Int, dstHost: String, dstPort: Int, enabled: Boolean, associatedHosts: List<String>) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onUpdate: (id: String, label: String, group: String?, type: PortForwardType, bind: String, srcPort: Int, dstHost: String, dstPort: Int, enabled: Boolean, associatedHosts: List<String>) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onImportFromQr: () -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val search = rememberSaveable { mutableStateOf("") }
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
    val groupState = remember { mutableStateOf("") }
    val bindState = remember { mutableStateOf("127.0.0.1") }
    val srcPortState = remember { mutableStateOf("22") }
    val dstHostState = remember { mutableStateOf("") }
    val dstPortState = remember { mutableStateOf("0") }
    val enabledState = remember { mutableStateOf(true) }
    val selectedHostsState = remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedHostFilterId = remember { mutableStateOf<String?>(null) }
    val hostFilterExpanded = remember { mutableStateOf(false) }
    val dialogError = remember { mutableStateOf<String?>(null) }
    val shareForward = remember { mutableStateOf<PortForward?>(null) }
    val pendingDeleteForward = remember { mutableStateOf<PortForward?>(null) }
    val overflowForwardId = remember { mutableStateOf<String?>(null) }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    val dialogBodyMaxHeight = rememberDialogBodyMaxHeight()
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (val processed = processPortForwardQrImport(contents)) {
            is PortForwardQrImportResult.Error -> {
                Toast.makeText(context, processed.message, Toast.LENGTH_SHORT).show()
            }
            is PortForwardQrImportResult.Ready -> {
                val imported = processed.forward
                if (imported.type != PortForwardType.LOCAL) {
                    Toast.makeText(context, "Only Local forwarding is supported.", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                onAdd(
                    imported.label,
                    imported.group,
                    PortForwardType.LOCAL,
                    imported.sourceHost,
                    imported.sourcePort,
                    imported.destinationHost,
                    imported.destinationPort,
                    imported.enabled,
                    imported.associatedHosts
                )
                onImportFromQr()
                Toast.makeText(context, "Port forward imported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openDialog(forward: PortForward?) {
        editingId.value = forward?.id
        labelState.value = forward?.label ?: ""
        groupState.value = forward?.group ?: ""
        bindState.value = forward?.sourceHost ?: "127.0.0.1"
        srcPortState.value = forward?.sourcePort?.toString() ?: "8080"
        dstHostState.value = forward?.destinationHost ?: ""
        dstPortState.value = forward?.destinationPort?.toString() ?: "0"
        enabledState.value = forward?.enabled ?: true
        selectedHostsState.value = forward?.associatedHosts ?: emptyList()
        selectedHostFilterId.value = null
        hostFilterExpanded.value = false
        dialogError.value = null
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
    }

    LaunchedEffect(addRequestKey) {
        if (addRequestKey > 0) {
            openDialog(null)
        }
    }

    LaunchedEffect(importRequestKey) {
        if (importRequestKey > 0) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan port forward QR")
                setBeepEnabled(false)
                setCaptureActivity(com.majordaftapps.sshpeaches.app.ui.qr.PortraitCaptureActivity::class.java)
                setOrientationLocked(true)
            }
            scanLauncher.launch(options)
        }
    }

    val filteredItems = items.filter {
        val query = search.value.trim()
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            (it.group?.contains(query, ignoreCase = true) == true) ||
            it.sourceHost.contains(query, ignoreCase = true) ||
            it.destinationHost.contains(query, ignoreCase = true) ||
            it.type.name.contains(query, ignoreCase = true)
    }
    val groupedItems = buildGroupedSections(
        items = filteredItems,
        groupSelector = { it.group },
        itemComparator = compareBy<PortForward> { it.label.lowercase() }
    )
    val showEmptyState = items.isEmpty() || filteredItems.isEmpty()
    LaunchedEffect(showEmptyState) {
        onEmptyStateVisibleChanged(showEmptyState)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_FORWARDS)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.FORWARD_SEARCH_INPUT),
                    value = search.value,
                    onValueChange = { search.value = it },
                    placeholder = { Text("Search port forwards") },
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (items.isEmpty()) {
                    item { EmptyState(itemLabel = "port forward") }
                } else if (filteredItems.isEmpty()) {
                    item { EmptyState(itemLabel = "result") }
                } else {
                    groupedItems.forEach { section ->
                        item(key = "forward_header_${section.key}") {
                            GroupSectionHeader(
                                vertical = "forwards",
                                label = section.label,
                                count = section.items.size,
                                expanded = if (search.value.isNotBlank()) true else expandedSections[section.key] ?: true,
                                onToggle = {
                                    val current = expandedSections[section.key] ?: true
                                    expandedSections[section.key] = !current
                                }
                            )
                        }
                        if (search.value.isNotBlank() || (expandedSections[section.key] ?: true)) {
                            items(section.items, key = { it.id }) { forward ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(forward.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = localForwardSummary(forward),
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
                                                        forward.group,
                                                        PortForwardType.LOCAL,
                                                        forward.sourceHost,
                                                        forward.sourcePort,
                                                        forward.destinationHost,
                                                        forward.destinationPort,
                                                        it,
                                                        forward.associatedHosts
                                                    )
                                                }
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = if (forward.favorite) "Unfavorite" else "Favorite",
                                                tint = if (forward.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .testTag(UiTestTags.forwardFavorite(forward.id))
                                                    .clickable { onToggleFavorite(forward.id) }
                                                    .padding(4.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.QrCode,
                                                contentDescription = "Share",
                                                modifier = Modifier
                                                    .testTag(UiTestTags.forwardShare(forward.id))
                                                    .clickable { shareForward.value = forward }
                                                    .padding(4.dp)
                                            )
                                            IconButton(
                                                onClick = { overflowForwardId.value = forward.id },
                                                modifier = Modifier.testTag(UiTestTags.forwardOverflowButton(forward.id))
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                            }
                                            DropdownMenu(
                                                expanded = overflowForwardId.value == forward.id,
                                                onDismissRequest = { overflowForwardId.value = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit") },
                                                    onClick = {
                                                        overflowForwardId.value = null
                                                        openDialog(forward)
                                                    },
                                                    modifier = Modifier.testTag(UiTestTags.forwardOverflowAction(forward.id, "edit"))
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        overflowForwardId.value = null
                                                        pendingDeleteForward.value = forward
                                                    },
                                                    modifier = Modifier.testTag(UiTestTags.forwardOverflowAction(forward.id, "delete"))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteForward.value?.let { forward ->
        DeleteConfirmationDialog(
            title = "Delete port forward?",
            message = "Delete ${forward.label}?",
            onConfirm = {
                onDelete(forward.id)
                pendingDeleteForward.value = null
            },
            onDismiss = { pendingDeleteForward.value = null }
        )
    }

    if (showDialog.value) {
        val isEdit = editingId.value != null
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit port forward" else "Add port forward") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogBodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_DIALOG_LABEL_INPUT),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    OutlinedTextField(
                        value = groupState.value,
                        onValueChange = { groupState.value = it },
                        label = { Text("Group") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_GROUP_FIELD),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    Text("Type: Local (SSH -L)", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = bindState.value,
                        onValueChange = { bindState.value = it },
                        label = { Text("Local bind address") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_DIALOG_BIND_INPUT),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii
                        )
                    )
                    OutlinedTextField(
                        value = srcPortState.value,
                        onValueChange = { srcPortState.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Local port") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_DIALOG_SOURCE_PORT_INPUT),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number
                        )
                    )
                    OutlinedTextField(
                        value = dstHostState.value,
                        onValueChange = { dstHostState.value = it },
                        label = { Text("Destination host") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_DIALOG_DEST_HOST_INPUT),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii
                        )
                    )
                    OutlinedTextField(
                        value = dstPortState.value,
                        onValueChange = { dstPortState.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Destination port") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.FORWARD_DIALOG_DEST_PORT_INPUT),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = enabledState.value, onCheckedChange = { enabledState.value = it })
                        Text("Enable now")
                    }
                    ExposedDropdownMenuBox(
                        expanded = hostFilterExpanded.value,
                        onExpandedChange = { hostFilterExpanded.value = !hostFilterExpanded.value }
                    ) {
                        TextField(
                            value = selectedHostFilterId.value?.let { selectedId ->
                                hosts.firstOrNull { it.id == selectedId }?.name ?: "All hosts"
                            } ?: "All hosts",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filter hosts") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostFilterExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = hostFilterExpanded.value,
                            onDismissRequest = { hostFilterExpanded.value = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("All hosts") },
                                onClick = {
                                    selectedHostFilterId.value = null
                                    hostFilterExpanded.value = false
                                }
                            )
                            hosts.forEach { host ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(host.name.ifBlank { host.host }) },
                                    onClick = {
                                        selectedHostFilterId.value = host.id
                                        hostFilterExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val filtered = hosts.filter { host ->
                            selectedHostFilterId.value == null || host.id == selectedHostFilterId.value
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
                        if (hosts.isEmpty()) {
                            Text("No hosts available. Create a host first.", style = MaterialTheme.typography.bodySmall)
                        } else if (filtered.isEmpty()) {
                            Text("No hosts match selected filter", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    dialogError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val srcPort = parsePort(srcPortState.value) ?: run {
                        dialogError.value = "Enter a valid source port (1-65535)."
                        return@TextButton
                    }
                    val dstPort = parsePort(dstPortState.value) ?: run {
                        dialogError.value = "Enter a valid destination port."
                        return@TextButton
                    }
                    if (!isValidHostAddress(dstHostState.value)) {
                        dialogError.value = "Enter a valid destination host."
                        return@TextButton
                    }
                    val dstHost = dstHostState.value.trim()
                    dialogError.value = null
                    val label = labelState.value.ifBlank { "Forward ${UUID.randomUUID()}" }
                    val bind = bindState.value.ifBlank { "127.0.0.1" }.trim()
                    val associated = selectedHostsState.value
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            label,
                            groupState.value.trim().ifBlank { null },
                            PortForwardType.LOCAL,
                            bind,
                            srcPort,
                            dstHost,
                            dstPort,
                            enabledState.value,
                            associated
                        )
                    } else {
                        onAdd(
                            label,
                            groupState.value.trim().ifBlank { null },
                            PortForwardType.LOCAL,
                            bind,
                            srcPort,
                            dstHost,
                            dstPort,
                            enabledState.value,
                            associated
                        )
                    }
                    closeDialog()
                }) { Text(if (isEdit) "Save" else "Add") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { closeDialog() }) { Text("Cancel") }
                }
            }
        )
    }

    shareForward.value?.let { forward ->
        val qrBitmap = remember(forward) { generateForwardQr(forward) }
        AlertDialog(
            onDismissRequest = { shareForward.value = null },
            confirmButton = { TextButton(onClick = { shareForward.value = null }) { Text("Close") } },
            title = { Text("Share ${forward.label}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Port forward QR",
                            modifier = Modifier.size(220.dp)
                        )
                    } ?: Text("Unable to generate QR")
                    Text(
                        localForwardSummary(forward),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}

private fun localForwardSummary(forward: PortForward): String =
    "${forward.sourceHost}:${forward.sourcePort} \u2192 ${forward.destinationHost}:${forward.destinationPort}"
