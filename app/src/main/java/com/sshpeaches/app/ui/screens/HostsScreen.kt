package com.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.components.decodeHostFromQr
import com.sshpeaches.app.ui.state.SortMode
import com.sshpeaches.app.util.isValidHostAddress
import com.sshpeaches.app.util.parsePort
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sshpeaches.app.security.SecurityManager
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    hosts: List<HostConnection>,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    editMode: Boolean = false,
    pinConfigured: Boolean,
    canStoreCredentials: Boolean,
    onAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode, String?, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onImportFromQr: () -> Unit = {},
    onDeleteHost: (String) -> Unit = {},
    onUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onStartSession: (HostConnection, ConnectionMode) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onStopSession: (String) -> Unit = {}
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
    val passwordState = remember { mutableStateOf("") }
    val clearPasswordState = remember { mutableStateOf(false) }
    val dialogError = remember { mutableStateOf<String?>(null) }
    val pendingEncryptedImport = remember { mutableStateOf<Pair<String, String>?>(null) }
    val importPassphraseState = remember { mutableStateOf("") }
    val importPassphraseError = remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val payload = decodeHostFromQr(contents)
        val imported = payload?.host
        if (imported == null || imported.host.isBlank() || imported.username.isBlank()) {
            Toast.makeText(context, "Invalid host QR", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (hosts.any { it.name.equals(imported.name, true) }) {
            Toast.makeText(context, "Host already exists", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val encryptedPayload = payload.encryptedPasswordPayload
        val legacyPassword = payload.legacyPassword?.takeIf { pinConfigured && !SecurityManager.isLocked() }
        if (payload.legacyPassword != null && legacyPassword == null) {
            Toast.makeText(context, "Unlock and configure a PIN to import passwords.", Toast.LENGTH_SHORT).show()
        }
        val targetId = imported.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        onAdd(
            imported.name.ifBlank { imported.host },
            imported.host,
            imported.port,
            imported.username,
            imported.preferredAuth,
            imported.group,
            imported.notes,
            imported.defaultMode,
            legacyPassword,
            targetId
        )
        if (encryptedPayload != null) {
            if (!pinConfigured) {
                Toast.makeText(context, "Set a PIN before importing encrypted passwords.", Toast.LENGTH_SHORT).show()
            } else if (SecurityManager.isLocked()) {
                Toast.makeText(context, "Unlock with your PIN before importing encrypted passwords.", Toast.LENGTH_SHORT).show()
            } else {
                pendingEncryptedImport.value = encryptedPayload to targetId
                importPassphraseState.value = ""
                importPassphraseError.value = null
            }
        }
        onImportFromQr()
        Toast.makeText(context, "Host imported", Toast.LENGTH_SHORT).show()
    }

    pendingEncryptedImport.value?.let { (payload, targetId) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingEncryptedImport.value = null
                importPassphraseState.value = ""
                importPassphraseError.value = null
            },
            title = { Text("Decrypt imported password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when exporting this host.")
                    OutlinedTextField(
                        value = importPassphraseState.value,
                        onValueChange = {
                            importPassphraseState.value = it
                            importPassphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    importPassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val phrase = importPassphraseState.value
                    when {
                        phrase.length < 4 -> importPassphraseError.value = "Enter the export passphrase."
                        payload.isBlank() -> importPassphraseError.value = "Encrypted payload missing."
                        else -> {
                            runCatching {
                                SecurityManager.importHostPasswordPayload(targetId, payload, phrase)
                            }.onSuccess {
                                Toast.makeText(context, "Password imported", Toast.LENGTH_SHORT).show()
                                pendingEncryptedImport.value = null
                                importPassphraseState.value = ""
                                importPassphraseError.value = null
                            }.onFailure {
                                importPassphraseError.value = "Incorrect passphrase."
                            }
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingEncryptedImport.value = null
                    importPassphraseState.value = ""
                    importPassphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }

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
        passwordState.value = ""
        clearPasswordState.value = false
        dialogError.value = null
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
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan SSH host QR")
                                setBeepEnabled(false)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
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
                    if (pinConfigured && canStoreCredentials) {
                        OutlinedTextField(
                            value = passwordState.value,
                            onValueChange = { passwordState.value = it },
                            label = {
                                Text(
                                    if (editingHost.value?.hasPassword == true)
                                        "Password (leave blank to keep)"
                                    else
                                        "Password (optional)"
                                )
                            },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        if (editingHost.value?.hasPassword == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Checkbox(
                                    checked = clearPasswordState.value,
                                    onCheckedChange = { clearPasswordState.value = it }
                                )
                                Text("Remove stored password")
                            }
                        }
                    } else if (!pinConfigured) {
                        Text(
                            "Set a PIN in Settings to store passwords securely.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Unlock the app to edit stored passwords.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    dialogError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
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
                    val port = parsePort(portState.value) ?: run {
                        dialogError.value = "Enter a valid port between 1 and 65535."
                        return@TextButton
                    }
                    val requiresPassword = authState.value != AuthMethod.IDENTITY
                    when {
                        nameState.value.isBlank() || hostState.value.isBlank() || userState.value.isBlank() -> {
                            dialogError.value = "Name, host, and username are required."
                            return@TextButton
                        }
                        !isValidHostAddress(hostState.value) -> {
                            dialogError.value = "Enter a valid hostname or IP address."
                            return@TextButton
                        }
                        !isValidHostAddress(hostState.value) -> {
                            dialogError.value = "Enter a valid hostname or IP address."
                            return@TextButton
                        }
                        hosts.any { it.name.equals(nameState.value.trim(), true) && it.id != editingHost.value?.id } -> {
                            dialogError.value = "A host with that name already exists."
                            return@TextButton
                        }
                        requiresPassword && !pinConfigured -> {
                            dialogError.value = "Set a PIN to store passwords for password authentication."
                            return@TextButton
                        }
                        requiresPassword && !canStoreCredentials -> {
                            dialogError.value = "Unlock the app to store passwords."
                            return@TextButton
                        }
                        dialogError.value != null -> dialogError.value = null
                    }
                    val hasExistingPassword = editingHost.value?.hasPassword == true && !clearPasswordState.value
                    if (requiresPassword && passwordState.value.isBlank() && !hasExistingPassword) {
                        dialogError.value = "Password is required for the selected authentication method."
                        return@TextButton
                    }
                    val passwordValue = when {
                        !pinConfigured || !canStoreCredentials -> null
                        clearPasswordState.value -> ""
                        passwordState.value.isNotBlank() -> passwordState.value
                        else -> null
                    }
                    if (editingHost.value == null) {
                        onAdd(
                            nameState.value.trim(),
                            hostState.value.trim(),
                            port,
                            userState.value.trim(),
                            authState.value,
                            groupState.value.ifBlank { null },
                            notesState.value,
                            modeState.value,
                            passwordValue,
                            null
                        )
                    } else {
                        onUpdate(
                            editingHost.value!!.id,
                            nameState.value.trim(),
                            hostState.value.trim(),
                            port,
                            userState.value.trim(),
                            authState.value,
                            groupState.value.ifBlank { null },
                            notesState.value,
                            modeState.value,
                            passwordValue
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
