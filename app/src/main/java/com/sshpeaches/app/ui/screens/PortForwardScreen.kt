package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
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
import com.majordaftapps.sshpeaches.app.util.inferredDestinationHost
import com.majordaftapps.sshpeaches.app.util.isValidHostAddress
import com.majordaftapps.sshpeaches.app.util.parsePort
import com.majordaftapps.sshpeaches.app.util.selectedHostId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardScreen(
    items: List<PortForward>,
    hosts: List<HostConnection> = emptyList(),
    allowBackgroundSessions: Boolean = true,
    addRequestKey: Int = 0,
    editRequestKey: Int = 0,
    editRequestId: String? = null,
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
    val dstPortState = remember { mutableStateOf("0") }
    val enabledState = remember { mutableStateOf(true) }
    val selectedHostIdState = remember { mutableStateOf<String?>(null) }
    val hostPickerExpanded = remember { mutableStateOf(false) }
    val dialogError = remember { mutableStateOf<String?>(null) }
    val shareForward = remember { mutableStateOf<PortForward?>(null) }
    val pendingDeleteForward = remember { mutableStateOf<PortForward?>(null) }
    val overflowForwardId = remember { mutableStateOf<String?>(null) }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    val dialogBodyMaxHeight = rememberDialogBodyMaxHeight()
    val handledAddRequestKey = rememberSaveable { mutableIntStateOf(0) }
    val handledEditRequestKey = rememberSaveable { mutableIntStateOf(0) }
    val handledImportRequestKey = rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    fun openDialog(forward: PortForward?, asEdit: Boolean = forward != null) {
        editingId.value = if (asEdit) forward?.id else null
        labelState.value = forward?.label ?: ""
        groupState.value = forward?.group ?: ""
        bindState.value = forward?.sourceHost ?: "127.0.0.1"
        srcPortState.value = forward?.sourcePort?.toString() ?: "8080"
        dstPortState.value = forward?.destinationPort?.toString() ?: "0"
        enabledState.value = forward?.enabled ?: true
        selectedHostIdState.value = forward?.selectedHostId() ?: hosts.singleOrNull()?.id
        hostPickerExpanded.value = false
        dialogError.value = null
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
    }

    fun hostHasBackgroundSessionsDisabled(host: HostConnection): Boolean =
        host.backgroundBehavior == BackgroundBehavior.ALWAYS_STOP

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
                openDialog(imported, asEdit = false)
                onImportFromQr()
                Toast.makeText(context, "Select a host to finish importing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(addRequestKey) {
        if (addRequestKey > handledAddRequestKey.intValue) {
            handledAddRequestKey.intValue = addRequestKey
            openDialog(null)
        }
    }

    LaunchedEffect(editRequestKey, editRequestId, items) {
        if (editRequestKey > handledEditRequestKey.intValue) {
            handledEditRequestKey.intValue = editRequestKey
            items.firstOrNull { it.id == editRequestId }?.let { forward ->
                openDialog(forward)
            }
        }
    }

    LaunchedEffect(importRequestKey) {
        if (importRequestKey > handledImportRequestKey.intValue) {
            handledImportRequestKey.intValue = importRequestKey
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

    val filteredItems = items.filter { forward ->
        val query = search.value.trim()
        val selectedHost = hosts.firstOrNull { it.id == forward.selectedHostId() }
        val resolvedDestinationHost = forward.inferredDestinationHost(selectedHost)
        val selectedHostName = selectedHost?.name.orEmpty()
        query.isBlank() ||
            forward.label.contains(query, ignoreCase = true) ||
            (forward.group?.contains(query, ignoreCase = true) == true) ||
            forward.sourceHost.contains(query, ignoreCase = true) ||
            resolvedDestinationHost.contains(query, ignoreCase = true) ||
            selectedHostName.contains(query, ignoreCase = true) ||
            forward.type.name.contains(query, ignoreCase = true)
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
                                val selectedHost = hosts.firstOrNull { it.id == forward.selectedHostId() }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(forward.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = localForwardSummary(forward, selectedHost),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (selectedHost != null) {
                                            Text(
                                                text = "Host: ${selectedHost.name.ifBlank { selectedHost.host }}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
                                            IconButton(
                                                onClick = { onToggleFavorite(forward.id) },
                                                modifier = Modifier
                                                    .testTag(UiTestTags.forwardFavorite(forward.id))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = if (forward.favorite) "Unfavorite" else "Favorite",
                                                    tint = if (forward.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            IconButton(
                                                onClick = { shareForward.value = forward },
                                                modifier = Modifier.testTag(UiTestTags.forwardShare(forward.id))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCode,
                                                    contentDescription = "Share"
                                                )
                                            }
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
        val selectedHost = hosts.firstOrNull { it.id == selectedHostIdState.value }
        val selectedHostBackgroundError = selectedHost
            ?.takeIf(::hostHasBackgroundSessionsDisabled)
            ?.let { "Selected host has background sessions disabled." }
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
                    ExposedDropdownMenuBox(
                        expanded = hostPickerExpanded.value,
                        onExpandedChange = {
                            if (hosts.isNotEmpty()) {
                                hostPickerExpanded.value = !hostPickerExpanded.value
                            }
                        }
                    ) {
                        TextField(
                            value = selectedHost?.name?.ifBlank { selectedHost.host } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Host") },
                            placeholder = { Text("Select host") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostPickerExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag(UiTestTags.FORWARD_DIALOG_HOST_FIELD)
                        )
                        ExposedDropdownMenu(
                            expanded = hostPickerExpanded.value,
                            onDismissRequest = { hostPickerExpanded.value = false }
                        ) {
                            hosts.forEach { host ->
                                DropdownMenuItem(
                                    text = { Text(host.name.ifBlank { host.host }) },
                                    modifier = Modifier.testTag(UiTestTags.forwardHostOption(host.id)),
                                    onClick = {
                                        selectedHostIdState.value = host.id
                                        hostPickerExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    selectedHostBackgroundError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(UiTestTags.FORWARD_HOST_BACKGROUND_ERROR)
                        )
                    }
                    Text(
                        text = if (selectedHost == null) {
                            "Destination host is inferred from the selected host."
                        } else {
                            "Destination host: ${selectedHost.host}"
                        },
                        style = MaterialTheme.typography.bodySmall
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
                    if (hosts.isEmpty()) {
                        Text("No hosts available. Create a host first.", style = MaterialTheme.typography.bodySmall)
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
                    val chosenHost = hosts.firstOrNull { it.id == selectedHostIdState.value } ?: run {
                        dialogError.value = "Select a host."
                        return@TextButton
                    }
                    if (!allowBackgroundSessions) {
                        dialogError.value = "Enable background sessions in Settings before saving a port forward."
                        return@TextButton
                    }
                    if (hostHasBackgroundSessionsDisabled(chosenHost)) {
                        dialogError.value = "Selected host has background sessions disabled."
                        return@TextButton
                    }
                    if (!isValidHostAddress(chosenHost.host)) {
                        dialogError.value = "Selected host has an invalid address."
                        return@TextButton
                    }
                    dialogError.value = null
                    val label = labelState.value.ifBlank { "Forward ${UUID.randomUUID()}" }
                    val bind = bindState.value.ifBlank { "127.0.0.1" }.trim()
                    val associated = listOf(chosenHost.id)
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            label,
                            groupState.value.trim().ifBlank { null },
                            PortForwardType.LOCAL,
                            bind,
                            srcPort,
                            chosenHost.host,
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
                            chosenHost.host,
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
                        localForwardSummary(forward, hosts.firstOrNull { it.id == forward.selectedHostId() }),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}

private fun localForwardSummary(forward: PortForward, selectedHost: HostConnection? = null): String =
    "${forward.sourceHost}:${forward.sourcePort} \u2192 ${forward.inferredDestinationHost(selectedHost)}:${forward.destinationPort}"
