package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.components.HostCard
import com.majordaftapps.sshpeaches.app.ui.components.HostQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.processHostQrImport
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.state.SortMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.rememberDialogBodyMaxHeight
import com.majordaftapps.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.majordaftapps.sshpeaches.app.util.isValidHostAddress
import com.majordaftapps.sshpeaches.app.util.parsePort
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.majordaftapps.sshpeaches.app.util.snippetReference
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    hosts: List<HostConnection>,
    identities: List<Identity>,
    portForwards: List<PortForward>,
    snippets: List<Snippet>,
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    editMode: Boolean = false,
    canStoreCredentials: Boolean,
    onAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String?, String, BackgroundBehavior, String?, String?, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onImportFromQr: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onDeleteHost: (String) -> Unit = {},
    onUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String?, String, BackgroundBehavior, String?, String?) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onStartSession: (HostConnection, ConnectionMode, String?) -> Unit = { _, _, _ -> },
    @Suppress("UNUSED_PARAMETER") onStopSession: (String) -> Unit = {},
    activeSshSessionHostIds: Set<String> = emptySet(),
    openSessions: List<SessionService.SessionSnapshot> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    onDisconnectSession: (String) -> Unit = {},
    onRunInfoCommand: (HostConnection, String) -> Boolean = { _, _ -> false },
    onInfoCommandsChange: (HostConnection, List<String>) -> Unit = { _, _ -> }
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
    val preferredIdentityIdState = remember { mutableStateOf<String?>(null) }
    val identityExpanded = remember { mutableStateOf(false) }
    val useMoshState = remember { mutableStateOf(false) }
    val terminalProfileIdState = remember { mutableStateOf<String?>(null) }
    val terminalProfileExpanded = remember { mutableStateOf(false) }
    val preferredForwardIdState = remember { mutableStateOf<String?>(null) }
    val forwardExpanded = remember { mutableStateOf(false) }
    val startupScriptState = remember { mutableStateOf("") }
    val startupSnippetExpanded = remember { mutableStateOf(false) }
    val backgroundBehaviorState = remember { mutableStateOf(BackgroundBehavior.INHERIT) }
    val backgroundExpanded = remember { mutableStateOf(false) }
    val passwordState = remember { mutableStateOf("") }
    val clearPasswordState = remember { mutableStateOf(false) }
    val dialogError = remember { mutableStateOf<String?>(null) }
    val showClearHostKeyDialog = remember { mutableStateOf(false) }
    val pendingEncryptedImport = remember { mutableStateOf<Pair<String, String>?>(null) }
    val importPassphraseState = remember { mutableStateOf("") }
    val importPassphraseError = remember { mutableStateOf<String?>(null) }
    val dialogBodyMaxHeight = rememberDialogBodyMaxHeight()
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (val processed = processHostQrImport(contents = contents, existingHosts = hosts)) {
            is HostQrImportResult.Error -> {
                Toast.makeText(context, processed.message, Toast.LENGTH_SHORT).show()
            }
            is HostQrImportResult.Ready -> {
                val imported = processed.data.host
                onAdd(
                    imported.name.ifBlank { imported.host },
                    imported.host,
                    imported.port,
                    imported.username,
                    imported.preferredAuth,
                    imported.group,
                    imported.notes,
                    ConnectionMode.SSH,
                    imported.useMosh,
                    imported.preferredIdentityId,
                    imported.preferredForwardId,
                    imported.startupScript,
                    imported.backgroundBehavior,
                    imported.terminalProfileId,
                    processed.data.legacyPassword,
                    processed.data.targetId
                )
                if (processed.data.encryptedPasswordPayload != null) {
                    pendingEncryptedImport.value = processed.data.encryptedPasswordPayload to processed.data.targetId
                    importPassphraseState.value = ""
                    importPassphraseError.value = null
                }
                onImportFromQr()
                Toast.makeText(context, "Host imported", Toast.LENGTH_SHORT).show()
            }
        }
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
        preferredIdentityIdState.value = host?.preferredIdentityId
        useMoshState.value = host?.useMosh ?: false
        terminalProfileIdState.value = host?.terminalProfileId
        preferredForwardIdState.value = host?.preferredForwardId
        startupScriptState.value = host?.startupScript ?: ""
        startupSnippetExpanded.value = false
        backgroundBehaviorState.value = host?.backgroundBehavior ?: BackgroundBehavior.INHERIT
        showDialog.value = true
        passwordState.value = ""
        clearPasswordState.value = false
        dialogError.value = null
        showClearHostKeyDialog.value = false
    }

    fun closeDialog() {
        showDialog.value = false
        editingHost.value = null
        startupSnippetExpanded.value = false
        showClearHostKeyDialog.value = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_HOSTS)
    ) {
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Open Sessions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (openSessions.isEmpty()) {
                        Text(
                            text = "No open sessions.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        openSessions.forEach { session ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${session.host.name.ifBlank { session.host.host }} • ${session.mode.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = session.statusMessage ?: session.status.name,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onOpenSession(session.hostId) }) {
                                        Text("Open")
                                    }
                                    TextButton(onClick = { onDisconnectSession(session.hostId) }) {
                                        Text("Disconnect")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { openDialog(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(UiTestTags.HOST_ADD_BUTTON)
                    ) {
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
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HostCard(
                        host = host,
                        snippets = snippets,
                        onToggleFavorite = onToggleFavorite,
                        canRunInfoCommands = activeSshSessionHostIds.contains(host.id),
                        onRunInfoCommand = onRunInfoCommand,
                        onInfoCommandsChange = onInfoCommandsChange,
                        onAction = { selected, mode ->
                            onStartSession(selected, mode, null)
                        }
                    )
                    AnimatedVisibility(
                        visible = editMode,
                        enter = fadeIn(animationSpec = tween(220)) +
                            slideInVertically(
                                animationSpec = tween(220),
                                initialOffsetY = { fullHeight -> fullHeight / 2 }
                            ),
                        exit = fadeOut(animationSpec = tween(180)) +
                            slideOutVertically(
                                animationSpec = tween(180),
                                targetOffsetY = { fullHeight -> fullHeight / 2 }
                            )
                    ) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogBodyMaxHeight)
                        .testTag(UiTestTags.HOST_DIALOG_SCROLL)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = nameState.value,
                        onValueChange = { nameState.value = it },
                        label = { Text("Name") },
                        modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_NAME_INPUT)
                    )
                    OutlinedTextField(
                        value = hostState.value,
                        onValueChange = { hostState.value = it },
                        label = { Text("Host / IP") },
                        modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_HOST_INPUT)
                    )
                    OutlinedTextField(
                        value = portState.value,
                        onValueChange = { portState.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_PORT_INPUT)
                    )
                    OutlinedTextField(
                        value = userState.value,
                        onValueChange = { userState.value = it },
                        label = { Text("Username") },
                        modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_USERNAME_INPUT)
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
                    if (editingHost.value != null) {
                        TextButton(
                            onClick = {
                                dialogError.value = null
                                showClearHostKeyDialog.value = true
                            }
                        ) {
                            Text("Clear stored host key")
                        }
                        Text(
                            "Use this if you hit host key verification failures after server key changes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!canStoreCredentials) {
                        Text(
                            "Passwords cannot be saved while the secure store is locked.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    dialogError.value?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_ERROR)
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = authMenuExpanded.value,
                        onExpandedChange = { authMenuExpanded.value = !authMenuExpanded.value }
                    ) {
                        TextField(
                            value = authState.value.toSentenceCaseLabel(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Authentication") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authMenuExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag(UiTestTags.HOST_DIALOG_AUTH_FIELD)
                        )
                        ExposedDropdownMenu(
                            expanded = authMenuExpanded.value,
                            onDismissRequest = { authMenuExpanded.value = false }
                        ) {
                            AuthMethod.values().forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.toSentenceCaseLabel()) },
                                    modifier = Modifier.testTag(UiTestTags.hostDialogAuthOption(method.name)),
                                    onClick = {
                                        authState.value = method
                                        authMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    val availableIdentities = identities
                    ExposedDropdownMenuBox(
                        expanded = identityExpanded.value,
                        onExpandedChange = { identityExpanded.value = !identityExpanded.value }
                    ) {
                        val selectedIdentity = availableIdentities.firstOrNull { it.id == preferredIdentityIdState.value }
                        TextField(
                            value = selectedIdentity?.label ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Identity key") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = identityExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = identityExpanded.value,
                            onDismissRequest = { identityExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    preferredIdentityIdState.value = null
                                    identityExpanded.value = false
                                }
                            )
                            availableIdentities.forEach { identity ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (identity.hasPrivateKey) {
                                                identity.label
                                            } else {
                                                "${identity.label} (no key)"
                                            }
                                        )
                                    },
                                    onClick = {
                                        preferredIdentityIdState.value = identity.id
                                        identityExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    if (authState.value != AuthMethod.PASSWORD && preferredIdentityIdState.value == null) {
                        Text(
                            "Select an identity key for identity authentication.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (authState.value != AuthMethod.PASSWORD) {
                        val selectedHasKey = identities.firstOrNull { it.id == preferredIdentityIdState.value }?.hasPrivateKey == true
                        if (!selectedHasKey) {
                            Text(
                                "Selected identity has no private key imported.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transport")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { useMoshState.value = false }
                            ) { Text(if (!useMoshState.value) "SSH ✓" else "SSH") }
                            TextButton(
                                onClick = { useMoshState.value = true }
                            ) { Text(if (useMoshState.value) "Mosh ✓" else "Mosh") }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = terminalProfileExpanded.value,
                        onExpandedChange = { terminalProfileExpanded.value = !terminalProfileExpanded.value }
                    ) {
                        val effectiveProfileId = terminalProfileIdState.value ?: defaultTerminalProfileId
                        TextField(
                            value = terminalProfiles.firstOrNull { it.id == effectiveProfileId }?.name ?: "App default",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Terminal profile") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = terminalProfileExpanded.value)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = terminalProfileExpanded.value,
                            onDismissRequest = { terminalProfileExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("App default") },
                                onClick = {
                                    terminalProfileIdState.value = null
                                    terminalProfileExpanded.value = false
                                }
                            )
                            terminalProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        terminalProfileIdState.value = profile.id
                                        terminalProfileExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = forwardExpanded.value,
                        onExpandedChange = { forwardExpanded.value = !forwardExpanded.value }
                    ) {
                        val localForwards = portForwards.filter {
                            it.type == com.majordaftapps.sshpeaches.app.data.model.PortForwardType.LOCAL
                        }
                        TextField(
                            value = localForwards.firstOrNull { it.id == preferredForwardIdState.value }?.label ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Use local forwarded port") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = forwardExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = forwardExpanded.value,
                            onDismissRequest = { forwardExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    preferredForwardIdState.value = null
                                    forwardExpanded.value = false
                                }
                            )
                            localForwards.forEach { forward ->
                                DropdownMenuItem(
                                    text = { Text(forward.label) },
                                    onClick = {
                                        preferredForwardIdState.value = forward.id
                                        forwardExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = startupSnippetExpanded.value,
                        onExpandedChange = {
                            if (snippets.isNotEmpty()) {
                                startupSnippetExpanded.value = !startupSnippetExpanded.value
                            }
                        }
                    ) {
                        val startupSnippetId = parseSnippetReference(startupScriptState.value)
                        val startupSnippet = snippets.firstOrNull { it.id == startupSnippetId }
                        val startupDisplay = when {
                            startupScriptState.value.isBlank() -> "None"
                            startupSnippet != null -> startupSnippet.title
                            startupSnippetId != null -> "Missing snippet"
                            else -> "Legacy command"
                        }
                        TextField(
                            value = startupDisplay,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Startup snippet") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = startupSnippetExpanded.value)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = startupSnippetExpanded.value,
                            onDismissRequest = { startupSnippetExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    startupScriptState.value = ""
                                    startupSnippetExpanded.value = false
                                }
                            )
                            snippets.forEach { snippet ->
                                DropdownMenuItem(
                                    text = { Text(snippet.title) },
                                    onClick = {
                                        startupScriptState.value = snippetReference(snippet.id)
                                        startupSnippetExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    val startupSnippetId = parseSnippetReference(startupScriptState.value)
                    val startupSnippet = snippets.firstOrNull { it.id == startupSnippetId }
                    when {
                        startupSnippet != null -> {
                            Text(
                                startupSnippet.command,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        startupSnippetId != null -> {
                            Text(
                                "Selected startup snippet no longer exists.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        startupScriptState.value.isNotBlank() -> {
                            Text(
                                "Legacy startup command detected. Pick a snippet to replace it.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                startupScriptState.value,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { startupScriptState.value = "" }) {
                                Text("Clear legacy command")
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = backgroundExpanded.value,
                        onExpandedChange = { backgroundExpanded.value = !backgroundExpanded.value }
                    ) {
                        TextField(
                            value = when (backgroundBehaviorState.value) {
                                BackgroundBehavior.INHERIT -> "Inherit global"
                                BackgroundBehavior.ALWAYS_ALLOW -> "Always allow"
                                BackgroundBehavior.ALWAYS_STOP -> "Always stop"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Background behavior") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backgroundExpanded.value) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = backgroundExpanded.value,
                            onDismissRequest = { backgroundExpanded.value = false }
                        ) {
                            BackgroundBehavior.values().forEach { behavior ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (behavior) {
                                                BackgroundBehavior.INHERIT -> "Inherit global"
                                                BackgroundBehavior.ALWAYS_ALLOW -> "Always allow"
                                                BackgroundBehavior.ALWAYS_STOP -> "Always stop"
                                            }
                                        )
                                    },
                                    onClick = {
                                        backgroundBehaviorState.value = behavior
                                        backgroundExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                    val port = parsePort(portState.value) ?: run {
                        dialogError.value = "Enter a valid port between 1 and 65535."
                        return@TextButton
                    }
                    when {
                        nameState.value.isBlank() || hostState.value.isBlank() || userState.value.isBlank() -> {
                            dialogError.value = "Name, host, and username are required."
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
                        authState.value != AuthMethod.PASSWORD && preferredIdentityIdState.value == null -> {
                            dialogError.value = "Select an identity key for identity authentication."
                            return@TextButton
                        }
                        dialogError.value != null -> dialogError.value = null
                    }
                    val passwordValue = when {
                        !canStoreCredentials -> null
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
                            ConnectionMode.SSH,
                            useMoshState.value,
                            preferredIdentityIdState.value,
                            preferredForwardIdState.value,
                            startupScriptState.value,
                            backgroundBehaviorState.value,
                            terminalProfileIdState.value,
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
                            ConnectionMode.SSH,
                            useMoshState.value,
                            preferredIdentityIdState.value,
                            preferredForwardIdState.value,
                            startupScriptState.value,
                            backgroundBehaviorState.value,
                            terminalProfileIdState.value,
                            passwordValue
                        )
                    }
                    closeDialog()
                },
                    modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_CONFIRM_BUTTON)
                ) {
                    Text(if (editingHost.value == null) "Add" else "Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { closeDialog() },
                        modifier = Modifier.testTag(UiTestTags.HOST_DIALOG_CANCEL_BUTTON)
                    ) { Text("Cancel") }
                }
            }
        )
    }

    if (showClearHostKeyDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearHostKeyDialog.value = false },
            title = { Text("Clear host key") },
            text = {
                Text(
                    "Remove the saved SSH host key for ${hostState.value.trim()}:${portState.value.trim().ifBlank { "22" }}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hostValue = hostState.value.trim()
                        val portValue = parsePort(portState.value) ?: editingHost.value?.port ?: 22
                        if (hostValue.isBlank()) {
                            dialogError.value = "Enter a host before clearing its key."
                        } else {
                            val cleared = SshClientProvider.clearKnownHostEntry(
                                context = context,
                                host = hostValue,
                                port = portValue
                            )
                            if (cleared) {
                                Toast.makeText(
                                    context,
                                    "Cleared stored host key for $hostValue:$portValue",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                dialogError.value = "Couldn't clear the host key. Try again."
                            }
                        }
                        showClearHostKeyDialog.value = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHostKeyDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}
