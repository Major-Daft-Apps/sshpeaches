package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.state.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    hosts: List<HostConnection>,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    editMode: Boolean = false,
    onAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onImportFromQr: () -> Unit = {},
    onDeleteHost: (String) -> Unit = {},
    onUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onStartSession: (HostConnection, ConnectionMode) -> Unit = { _, _ -> },
    onStopSession: (String) -> Unit = {}
) {
    val search = remember { mutableStateOf("") }
    val showMenu = remember { mutableStateOf(false) }
    val showDialog = remember { mutableStateOf(false) }
    val editingHost = remember { mutableStateOf<HostConnection?>(null) }
    val nameState = remember { mutableStateOf("") }
    val hostState = remember { mutableStateOf("") }
    val portState = remember { mutableStateOf("22") }
    val userState = remember { mutableStateOf("") }
    val groupState = remember { mutableStateOf("") }
    val notesState = remember { mutableStateOf("") }
    val authState = remember { mutableStateOf(AuthMethod.IDENTITY) }
    val authMenuExpanded = remember { mutableStateOf(false) }
    val modeState = remember { mutableStateOf(ConnectionMode.SSH) }
    val modeExpanded = remember { mutableStateOf(false) }

    fun openDialog(host: HostConnection?) {
        editingHost.value = host
        nameState.value = host?.name ?: ""
        hostState.value = host?.host ?: ""
        portState.value = host?.port?.toString() ?: "22"
        userState.value = host?.username ?: ""
        groupState.value = host?.group ?: ""
        notesState.value = host?.notes ?: ""
        authState.value = host?.preferredAuth ?: AuthMethod.IDENTITY
        modeState.value = host?.defaultMode ?: ConnectionMode.SSH
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
        editingHost.value = null
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = search.value,
                onValueChange = { search.value = it },
                placeholder = { Text("Search hosts") },
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Column {
                IconButton(onClick = { showMenu.value = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showMenu.value, onDismissRequest = { showMenu.value = false }) {
                    DropdownMenuItem(
                        text = { Text("Last Used", fontWeight = if (sortMode == SortMode.LAST_USED) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            showMenu.value = false
                            onSortModeChange(SortMode.LAST_USED)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alphabetical", fontWeight = if (sortMode == SortMode.ALPHABETICAL) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            showMenu.value = false
                            onSortModeChange(SortMode.ALPHABETICAL)
                        }
                    )
                }
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { openDialog(null) }, modifier = Modifier.weight(1f)) {
                        Text("Add host")
                    }
                    Button(onClick = onImportFromQr, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Import QR")
                    }
                }
            }
            items(hosts.filter { it.name.contains(search.value, ignoreCase = true) }, key = { it.id }) { host ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HostCard(host = host, onAction = onStartSession)
                    if (editMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { openDialog(host) }) { Text("Edit") }
                            TextButton(onClick = { onDeleteHost(host.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (editingHost.value != null) "Edit Host" else "Add Host") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameState.value,
                        onValueChange = { nameState.value = it },
                        label = { Text("Name") }
                    )
                    OutlinedTextField(
                        value = hostState.value,
                        onValueChange = { hostState.value = it },
                        label = { Text("Host / IP") }
                    )
                    OutlinedTextField(
                        value = portState.value,
                        onValueChange = { portState.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") }
                    )
                    OutlinedTextField(
                        value = userState.value,
                        onValueChange = { userState.value = it },
                        label = { Text("Username") }
                    )
                    OutlinedTextField(
                        value = groupState.value,
                        onValueChange = { groupState.value = it },
                        label = { Text("Group (optional)") }
                    )
                    OutlinedTextField(
                        value = notesState.value,
                        onValueChange = { notesState.value = it },
                        label = { Text("Notes") }
                    )
                    ExposedDropdownMenuBox(
                        expanded = authMenuExpanded.value,
                        onExpandedChange = { authMenuExpanded.value = !authMenuExpanded.value }
                    ) {
                        TextField(
                            value = authState.value.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Authentication") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authMenuExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = authMenuExpanded.value,
                            onDismissRequest = { authMenuExpanded.value = false }
                        ) {
                            AuthMethod.values().forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.name) },
                                    onClick = {
                                        authState.value = method
                                        authMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded.value,
                        onExpandedChange = { modeExpanded.value = !modeExpanded.value }
                    ) {
                        TextField(
                            value = modeState.value.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Default mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded.value,
                            onDismissRequest = { modeExpanded.value = false }
                        ) {
                            ConnectionMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name) },
                                    onClick = {
                                        modeState.value = mode
                                        modeExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val port = portState.value.toIntOrNull() ?: 22
                    if (editingHost.value == null) {
                        onAdd(
                            nameState.value,
                            hostState.value,
                            port,
                            userState.value,
                            authState.value,
                            groupState.value.ifBlank { null },
                            notesState.value,
                            modeState.value
                        )
                    } else {
                        onUpdate(
                            editingHost.value!!.id,
                            nameState.value,
                            hostState.value,
                            port,
                            userState.value,
                            authState.value,
                            groupState.value.ifBlank { null },
                            notesState.value,
                            modeState.value
                        )
                    }
                    closeDialog()
                }) {
                    Text(if (editingHost.value == null) "Add" else "Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingHost.value != null) {
                        TextButton(onClick = {
                            editingHost.value?.let { onDeleteHost(it.id) }
                            closeDialog()
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { closeDialog() }) { Text("Cancel") }
                }
            }
        )
    }
}
