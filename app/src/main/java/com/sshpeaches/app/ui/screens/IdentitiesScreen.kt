package com.majordaftapps.sshpeaches.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.DeleteConfirmationDialog
import com.majordaftapps.sshpeaches.app.ui.components.GroupSectionHeader
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrPayload
import com.majordaftapps.sshpeaches.app.ui.components.buildGroupedSections
import com.majordaftapps.sshpeaches.app.ui.components.generateIdentityQr
import com.majordaftapps.sshpeaches.app.ui.components.processIdentityQrImport
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.ui.util.rememberDialogBodyMaxHeight
import com.majordaftapps.sshpeaches.app.ui.util.updatePasswordStateWithReveal
import com.majordaftapps.sshpeaches.app.util.GeneratedIdentityKeyPair
import com.majordaftapps.sshpeaches.app.util.IdentityEcdsaCurve
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import com.majordaftapps.sshpeaches.app.util.computeSshPublicKeyFingerprint
import com.majordaftapps.sshpeaches.app.util.isValidFingerprint
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.launch

private data class IdentityOverwrite(
    val targetId: String,
    val label: String,
    val fingerprint: String,
    val username: String?,
    val group: String?,
    val keyPayload: String?
)

private enum class DialogKeyFileType {
    PRIVATE_KEY,
    PUBLIC_KEY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitiesScreen(
    items: List<Identity>,
    hosts: List<HostConnection>,
    isLocked: Boolean,
    addRequestKey: Int = 0,
    editRequestKey: Int = 0,
    editRequestId: String? = null,
    importRequestKey: Int = 0,
    onAdd: (label: String, fingerprint: String, username: String?, group: String?, suppliedId: String?) -> Unit = { _, _, _, _, _ -> },
    onUpdate: (id: String, label: String, fingerprint: String, username: String?, group: String?) -> Unit = { _, _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onImportIdentityKey: (id: String, payload: String, passphrase: String) -> Boolean = { _, _, _ -> false },
    onImportIdentityKeyPlain: (id: String, key: String) -> Boolean = { _, _ -> false },
    onStoreIdentityPublicKey: (id: String, key: String) -> Boolean = { _, _ -> false },
    onStoreIdentityKeyPassphrase: (id: String, passphrase: String?) -> Unit = { _, _ -> },
    onCopyKeyToHost: suspend (identityId: String, hostId: String, hostPassword: String?, identityPassphrase: String?) -> Boolean = { _, _, _, _ -> false },
    onRemoveIdentityKey: (id: String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onImportFromQr: () -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val search = rememberSaveable { mutableStateOf("") }
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
    val groupState = remember { mutableStateOf("") }
    val fingerprintState = remember { mutableStateOf("") }
    val dialogPrivateKeyState = remember { mutableStateOf("") }
    val dialogPublicKeyState = remember { mutableStateOf("") }
    val dialogFileType = remember { mutableStateOf<DialogKeyFileType?>(null) }
    val dialogKeyStatus = remember { mutableStateOf<String?>(null) }
    val dialogError = remember { mutableStateOf<String?>(null) }
    val shareIdentity = remember { mutableStateOf<Identity?>(null) }
    val shareQrBitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val sharePassphrasePrompt = remember { mutableStateOf<Identity?>(null) }
    val sharePassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.identity.orEmpty()) }
    val sharePassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val shareConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.identity.orEmpty()) }
    val shareConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val sharePassphraseError = remember { mutableStateOf<String?>(null) }
    val pendingOverwrite = remember { mutableStateOf<IdentityOverwrite?>(null) }
    val pendingKeyImport = remember { mutableStateOf<Pair<String, String>?>(null) }
    val importPassphraseState = remember { mutableStateOf("") }
    val importPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val importPassphraseError = remember { mutableStateOf<String?>(null) }
    val fileImportTarget = remember { mutableStateOf<String?>(null) }
    val dialogBodyMaxHeight = rememberDialogBodyMaxHeight()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogKeyPassphraseState = remember { mutableStateOf("") }
    val dialogKeyPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val showGenerateDialog = remember { mutableStateOf(false) }
    val generationAlgorithm = remember { mutableStateOf(IdentityKeyAlgorithm.ED25519) }
    val generationAlgorithmExpanded = remember { mutableStateOf(false) }
    val generationRsaBits = remember { mutableStateOf(4096) }
    val generationRsaBitsExpanded = remember { mutableStateOf(false) }
    val generationCurve = remember { mutableStateOf(IdentityEcdsaCurve.P256) }
    val generationPassphrase = remember { mutableStateOf("") }
    val generationPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val generationConfirmPassphrase = remember { mutableStateOf("") }
    val generationConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val generationError = remember { mutableStateOf<String?>(null) }
    val copyKeyIdentity = remember { mutableStateOf<Identity?>(null) }
    val copyHostId = remember { mutableStateOf<String?>(null) }
    val copyHostPassword = remember { mutableStateOf("") }
    val copyHostPasswordRevealIndex = remember { mutableIntStateOf(-1) }
    val copyIdentityPassphrase = remember { mutableStateOf("") }
    val copyIdentityPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val copyInProgress = remember { mutableStateOf(false) }
    val copyError = remember { mutableStateOf<String?>(null) }
    val pendingDeleteIdentity = remember { mutableStateOf<Identity?>(null) }
    val overflowIdentityId = remember { mutableStateOf<String?>(null) }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    val handledEditRequestKey = rememberSaveable { mutableIntStateOf(0) }
    AutoHidePasswordReveal(sharePassphraseRevealIndex)
    AutoHidePasswordReveal(shareConfirmPassphraseRevealIndex)
    AutoHidePasswordReveal(importPassphraseRevealIndex)
    AutoHidePasswordReveal(dialogKeyPassphraseRevealIndex)
    AutoHidePasswordReveal(generationPassphraseRevealIndex)
    AutoHidePasswordReveal(generationConfirmPassphraseRevealIndex)
    AutoHidePasswordReveal(copyHostPasswordRevealIndex)
    AutoHidePasswordReveal(copyIdentityPassphraseRevealIndex)

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val keyText = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (keyText.isNullOrBlank()) {
            onShowMessage("Unable to read key file")
            return@rememberLauncherForActivityResult
        }
        val sanitized = keyText.trim()
        val pendingDialogType = dialogFileType.value
        if (pendingDialogType != null) {
            dialogFileType.value = null
            when (pendingDialogType) {
                DialogKeyFileType.PRIVATE_KEY -> {
                    if (!sanitized.startsWith("-----BEGIN")) {
                        dialogError.value = "Invalid private key format."
                        return@rememberLauncherForActivityResult
                    }
                    dialogPrivateKeyState.value = sanitized
                    dialogError.value = null
                    if (fingerprintState.value.isBlank()) {
                        fingerprintState.value = computeFingerprintFromKeyMaterial(sanitized)
                    }
                    dialogKeyStatus.value = "Private key selected."
                }
                DialogKeyFileType.PUBLIC_KEY -> {
                    dialogPublicKeyState.value = sanitized
                    dialogError.value = null
                    fingerprintState.value = computeFingerprintFromKeyMaterial(sanitized)
                    dialogKeyStatus.value = "Public key selected."
                }
            }
            return@rememberLauncherForActivityResult
        }

        val targetId = fileImportTarget.value ?: return@rememberLauncherForActivityResult
        fileImportTarget.value = null
        if (!sanitized.startsWith("-----BEGIN")) {
            onShowMessage("Invalid key format")
            return@rememberLauncherForActivityResult
        }
        val success = onImportIdentityKeyPlain(targetId, sanitized)
        if (success) {
            val comment = items.firstOrNull { it.id == targetId }?.label.orEmpty()
            val derivedPublic = SshKeyGenerator.derivePublicKeyFromPrivate(sanitized, comment).orEmpty()
            if (derivedPublic.isNotBlank()) {
                onStoreIdentityPublicKey(targetId, derivedPublic)
            }
        }
        onShowMessage(if (success) "Private key imported" else "Failed to import key")
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (
            val processed = processIdentityQrImport(
                contents = contents,
                existingIdentities = items
            )
        ) {
            is IdentityQrImportResult.Error -> onShowMessage(processed.message)
            is IdentityQrImportResult.NeedsOverwrite -> {
                pendingOverwrite.value = IdentityOverwrite(
                    targetId = processed.overwrite.targetId,
                    label = processed.overwrite.label,
                    fingerprint = processed.overwrite.fingerprint,
                    username = processed.overwrite.username,
                    group = processed.overwrite.group,
                    keyPayload = processed.overwrite.encryptedKeyPayload
                )
            }
            is IdentityQrImportResult.Ready -> {
                val imported = processed.data.identity
                onAdd(
                    imported.label,
                    imported.fingerprint,
                    imported.username,
                    imported.group,
                    imported.id
                )
                onImportFromQr()
                if (!processed.data.encryptedKeyPayload.isNullOrBlank()) {
                    pendingKeyImport.value = imported.id to processed.data.encryptedKeyPayload
                    importPassphraseState.value = ""
                    importPassphraseError.value = null
                } else {
                    onShowMessage("Identity imported")
                }
            }
        }
    }

    fun openDialog(identity: Identity?) {
        editingId.value = identity?.id
        labelState.value = identity?.label ?: ""
        groupState.value = identity?.group ?: ""
        fingerprintState.value = identity?.fingerprint ?: ""
        dialogPrivateKeyState.value = ""
        dialogPublicKeyState.value = ""
        dialogKeyPassphraseState.value = ""
        dialogFileType.value = null
        dialogKeyStatus.value = null
        dialogError.value = null
        generationError.value = null
        generationAlgorithmExpanded.value = false
        generationRsaBitsExpanded.value = false
        showGenerateDialog.value = false
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
        dialogFileType.value = null
        generationAlgorithmExpanded.value = false
        generationRsaBitsExpanded.value = false
        showGenerateDialog.value = false
    }

    fun openCopyKeyDialog(identity: Identity) {
        if (isLocked) {
            onShowMessage("Unlock the app before installing keys.")
            return
        }
        copyKeyIdentity.value = identity
        copyHostId.value = hosts.firstOrNull()?.id
        copyHostPassword.value = ""
        copyIdentityPassphrase.value = ""
        copyError.value = null
        copyInProgress.value = false
    }

    LaunchedEffect(addRequestKey) {
        if (addRequestKey > 0) {
            openDialog(null)
        }
    }

    LaunchedEffect(editRequestKey, editRequestId, items) {
        if (editRequestKey > handledEditRequestKey.intValue) {
            handledEditRequestKey.intValue = editRequestKey
            items.firstOrNull { it.id == editRequestId }?.let(::openDialog)
        }
    }

    LaunchedEffect(importRequestKey) {
        if (importRequestKey > 0) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan SSH identity QR")
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
            it.fingerprint.contains(query, ignoreCase = true) ||
            (it.username?.contains(query, ignoreCase = true) == true) ||
            (it.group?.contains(query, ignoreCase = true) == true)
    }
    val groupedItems = buildGroupedSections(
        items = filteredItems,
        groupSelector = { it.group },
        itemComparator = compareBy<Identity> { it.label.lowercase() }
    )
    val showEmptyState = items.isEmpty() || filteredItems.isEmpty()
    LaunchedEffect(showEmptyState) {
        onEmptyStateVisibleChanged(showEmptyState)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_IDENTITIES)
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
                    .testTag(UiTestTags.IDENTITY_SEARCH_INPUT),
                value = search.value,
                onValueChange = { search.value = it },
                placeholder = { Text("Search identities") },
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
                item { EmptyState(itemLabel = "identity") }
            } else if (filteredItems.isEmpty()) {
                item { EmptyState(itemLabel = "result") }
            } else {
                groupedItems.forEach { section ->
                    item(key = "identity_header_${section.key}") {
                        GroupSectionHeader(
                            vertical = "identities",
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
                        items(section.items, key = { it.id }) { identity ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(identity.label, style = MaterialTheme.typography.titleMedium)
                                            Text(identity.fingerprint, style = MaterialTheme.typography.bodySmall)
                                            identity.username?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = if (identity.favorite) "Unfavorite" else "Favorite",
                                                tint = if (identity.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .testTag(UiTestTags.identityFavorite(identity.id))
                                                    .clickable { onToggleFavorite(identity.id) }
                                            )
                                            if (identity.hasPrivateKey) {
                                                Icon(
                                                    Icons.Default.VpnKey,
                                                    contentDescription = "Has private key",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Icon(
                                                Icons.Default.QrCode,
                                                contentDescription = "Share identity",
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .testTag(UiTestTags.identityShare(identity.id))
                                                    .clickable {
                                                        if (identity.hasPrivateKey) {
                                                            sharePassphrasePrompt.value = identity
                                                            sharePassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                            shareConfirmPassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                            sharePassphraseError.value = null
                                                        } else {
                                                            shareQrBitmap.value = generateIdentityQr(identity, passphrase = null)
                                                            shareIdentity.value = identity
                                                        }
                                                    }
                                            )
                                            Icon(
                                                Icons.Default.VpnKey,
                                                contentDescription = "Import key file",
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable {
                                                        if (isLocked) {
                                                            onShowMessage("Unlock the app before importing keys.")
                                                        } else {
                                                            fileImportTarget.value = identity.id
                                                            fileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                                        }
                                                    }
                                            )
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy key to host",
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable { openCopyKeyDialog(identity) }
                                            )
                                            IconButton(
                                                onClick = { overflowIdentityId.value = identity.id },
                                                modifier = Modifier.testTag(UiTestTags.identityOverflowButton(identity.id))
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                            }
                                            DropdownMenu(
                                                expanded = overflowIdentityId.value == identity.id,
                                                onDismissRequest = { overflowIdentityId.value = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit") },
                                                    onClick = {
                                                        overflowIdentityId.value = null
                                                        openDialog(identity)
                                                    },
                                                    modifier = Modifier.testTag(UiTestTags.identityOverflowAction(identity.id, "edit"))
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        overflowIdentityId.value = null
                                                        pendingDeleteIdentity.value = identity
                                                    },
                                                    modifier = Modifier.testTag(UiTestTags.identityOverflowAction(identity.id, "delete"))
                                                )
                                            }
                                        }
                                    }
                                    TextButton(onClick = { openCopyKeyDialog(identity) }) {
                                        Text("Install Key To Host")
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

    pendingDeleteIdentity.value?.let { identity ->
        DeleteConfirmationDialog(
            title = "Delete identity?",
            message = "Delete ${identity.label}?",
            onConfirm = {
                onDelete(identity.id)
                pendingDeleteIdentity.value = null
            },
            onDismiss = { pendingDeleteIdentity.value = null }
        )
    }

    if (showDialog.value) {
        val isEdit = editingId.value != null
        val editingIdentity = items.firstOrNull { it.id == editingId.value }
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit identity" else "Add identity") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogBodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_DIALOG_LABEL_INPUT),
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
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_GROUP_FIELD),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    KeyInputRow(
                        label = "Private key",
                        value = dialogPrivateKeyState.value,
                        onValueChange = { next ->
                            dialogPrivateKeyState.value = next
                            if (dialogError.value != null) {
                                dialogError.value = null
                            }
                            val trimmed = next.trim()
                            if (trimmed.isNotBlank() && dialogPublicKeyState.value.isBlank()) {
                                fingerprintState.value = computeFingerprintFromKeyMaterial(trimmed)
                            }
                        },
                        onBrowse = {
                            if (isLocked) {
                                dialogError.value = "Unlock the app before importing keys."
                            } else {
                                dialogFileType.value = DialogKeyFileType.PRIVATE_KEY
                                fileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                            }
                        }
                    )
                    KeyInputRow(
                        label = "Public key",
                        value = dialogPublicKeyState.value,
                        onValueChange = { next ->
                            dialogPublicKeyState.value = next
                            if (dialogError.value != null) {
                                dialogError.value = null
                            }
                            val trimmed = next.trim()
                            if (trimmed.isNotBlank()) {
                                fingerprintState.value = computeFingerprintFromKeyMaterial(trimmed)
                            } else {
                                val privateTrimmed = dialogPrivateKeyState.value.trim()
                                if (privateTrimmed.isNotBlank()) {
                                    fingerprintState.value = computeFingerprintFromKeyMaterial(privateTrimmed)
                                }
                            }
                        },
                        onBrowse = {
                            dialogFileType.value = DialogKeyFileType.PUBLIC_KEY
                            fileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                        }
                    )
                    OutlinedTextField(
                        value = dialogKeyPassphraseState.value,
                        onValueChange = { updatePasswordStateWithReveal(dialogKeyPassphraseState, dialogKeyPassphraseRevealIndex, it) },
                        label = { Text("Key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(dialogKeyPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    Button(
                        onClick = {
                            generationAlgorithm.value = IdentityKeyAlgorithm.ED25519
                            generationRsaBits.value = 4096
                            generationCurve.value = IdentityEcdsaCurve.P256
                            generationPassphrase.value = ""
                            generationConfirmPassphrase.value = ""
                            generationError.value = null
                            showGenerateDialog.value = true
                        },
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_DIALOG_GENERATE_BUTTON)
                    ) {
                        Text("Generate keypair")
                    }
                    OutlinedTextField(
                        value = fingerprintState.value,
                        onValueChange = {},
                        label = { Text("Fingerprint") },
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_DIALOG_FINGERPRINT_INPUT)
                    )
                    dialogKeyStatus.value?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    dialogError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fp = fingerprintState.value.trim()
                    if (fp.isEmpty()) {
                        dialogError.value = "Select a key file so fingerprint can be calculated."
                        return@TextButton
                    }
                    val duplicate = items.any { it.fingerprint.equals(fp, true) && it.id != editingId.value }
                    if (duplicate) {
                        dialogError.value = "An identity with that fingerprint already exists."
                        return@TextButton
                    }
                    if (!isValidFingerprint(fp)) {
                        dialogError.value = "Fingerprint format looks invalid."
                        return@TextButton
                    }
                    dialogError.value = null
                    val privateKey = dialogPrivateKeyState.value.trim()
                    val publicKeyInput = dialogPublicKeyState.value.trim()
                    val keyPassphrase = dialogKeyPassphraseState.value.trim().takeIf { it.isNotBlank() }
                    val identityId = if (isEdit) {
                        editingId.value!!
                    } else {
                        UUID.randomUUID().toString()
                    }
                    if (isEdit) {
                        onUpdate(
                            identityId,
                            labelState.value,
                            fp,
                            null,
                            groupState.value.trim().ifBlank { null }
                        )
                        if (privateKey.isNotBlank()) {
                            val imported = onImportIdentityKeyPlain(identityId, privateKey)
                            onShowMessage(if (imported) "Private key imported" else "Failed to import private key")
                        }
                    } else {
                        onAdd(
                            labelState.value.ifBlank { "Identity ${UUID.randomUUID()}" },
                            fp,
                            null,
                            groupState.value.trim().ifBlank { null },
                            identityId
                        )
                    }
                    if (privateKey.isNotBlank()) {
                        val imported = onImportIdentityKeyPlain(identityId, privateKey)
                        onShowMessage(if (imported) "Private key imported" else "Failed to import private key")
                    }
                    val publicKey = when {
                        publicKeyInput.isNotBlank() -> publicKeyInput
                        privateKey.isNotBlank() -> SshKeyGenerator.derivePublicKeyFromPrivate(privateKey, labelState.value.trim()).orEmpty()
                        else -> ""
                    }.trim()
                    if (publicKey.isNotBlank()) {
                        onStoreIdentityPublicKey(identityId, publicKey)
                    }
                    onStoreIdentityKeyPassphrase(identityId, keyPassphrase)
                    closeDialog()
                }) { Text(if (editingId.value == null) "Add" else "Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingId.value != null && editingIdentity?.hasPrivateKey == true) {
                        TextButton(onClick = {
                            if (isLocked) {
                                onShowMessage("Unlock the app before removing keys.")
                            } else {
                                onRemoveIdentityKey(editingId.value!!)
                                onShowMessage("Private key removed")
                                closeDialog()
                            }
                        }) { Text("Remove key") }
                    }
                    if (editingId.value != null) {
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

    if (showGenerateDialog.value) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog.value = false },
            title = { Text("Generate keypair") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = generationAlgorithmExpanded.value,
                        onExpandedChange = { generationAlgorithmExpanded.value = !generationAlgorithmExpanded.value }
                    ) {
                        OutlinedTextField(
                            value = when (generationAlgorithm.value) {
                                IdentityKeyAlgorithm.ED25519 -> "Ed25519"
                                IdentityKeyAlgorithm.RSA -> "RSA"
                                IdentityKeyAlgorithm.ECDSA -> "ECDSA"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Algorithm") },
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = generationAlgorithmExpanded.value)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = generationAlgorithmExpanded.value,
                            onDismissRequest = { generationAlgorithmExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ed25519") },
                                onClick = {
                                    generationAlgorithm.value = IdentityKeyAlgorithm.ED25519
                                    generationAlgorithmExpanded.value = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("RSA") },
                                onClick = {
                                    generationAlgorithm.value = IdentityKeyAlgorithm.RSA
                                    generationAlgorithmExpanded.value = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ECDSA") },
                                onClick = {
                                    generationAlgorithm.value = IdentityKeyAlgorithm.ECDSA
                                    generationAlgorithmExpanded.value = false
                                }
                            )
                        }
                    }
                    if (generationAlgorithm.value == IdentityKeyAlgorithm.RSA) {
                        ExposedDropdownMenuBox(
                            expanded = generationRsaBitsExpanded.value,
                            onExpandedChange = { generationRsaBitsExpanded.value = !generationRsaBitsExpanded.value }
                        ) {
                            OutlinedTextField(
                                value = generationRsaBits.value.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("RSA bits") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = generationRsaBitsExpanded.value)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = generationRsaBitsExpanded.value,
                                onDismissRequest = { generationRsaBitsExpanded.value = false }
                            ) {
                                listOf(2048, 3072, 4096).forEach { bits ->
                                    DropdownMenuItem(
                                        text = { Text(bits.toString()) },
                                        onClick = {
                                            generationRsaBits.value = bits
                                            generationRsaBitsExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (generationAlgorithm.value == IdentityKeyAlgorithm.ECDSA) {
                        Text("Curve")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdentityEcdsaCurve.values().forEach { curve ->
                                KeyGenOptionButton(
                                    label = curve.name,
                                    selected = generationCurve.value == curve,
                                    onClick = { generationCurve.value = curve }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = generationPassphrase.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(generationPassphrase, generationPassphraseRevealIndex, it)
                            generationError.value = null
                        },
                        label = { Text("Key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(generationPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    OutlinedTextField(
                        value = generationConfirmPassphrase.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(generationConfirmPassphrase, generationConfirmPassphraseRevealIndex, it)
                            generationError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(generationConfirmPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    generationError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val phrase = generationPassphrase.value.trim()
                    if (phrase.isNotBlank()) {
                        if (phrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH) {
                            generationError.value =
                                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                            return@TextButton
                        }
                        if (phrase != generationConfirmPassphrase.value) {
                            generationError.value = "Passphrases do not match."
                            return@TextButton
                        }
                    }
                    val spec = IdentityKeyGenerationSpec(
                        algorithm = generationAlgorithm.value,
                        rsaBits = generationRsaBits.value,
                        ecdsaCurve = generationCurve.value,
                        comment = labelState.value.trim().ifBlank { "sshpeaches" },
                        keyPassphrase = phrase.takeIf { it.isNotBlank() }
                    )
                    val generated = runCatching { SshKeyGenerator.generate(spec) }.getOrNull()
                    if (generated == null) {
                        generationError.value = "Failed to generate keypair."
                        return@TextButton
                    }
                    applyGeneratedKeyPair(
                        generated = generated,
                        privateKeyState = dialogPrivateKeyState,
                        publicKeyState = dialogPublicKeyState,
                        fingerprintState = fingerprintState,
                        passphraseState = dialogKeyPassphraseState,
                        keyStatusState = dialogKeyStatus,
                        passphrase = phrase
                    )
                    dialogError.value = null
                    showGenerateDialog.value = false
                    generationError.value = null
                }) { Text("Generate") }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    copyKeyIdentity.value?.let { identity ->
        AlertDialog(
            onDismissRequest = {
                if (!copyInProgress.value) {
                    copyKeyIdentity.value = null
                }
            },
            title = { Text("Copy Key to Host") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hosts.isEmpty()) {
                        Text("Add a host first, then try copying this key.")
                    } else {
                        Text("Choose destination host")
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(hosts, key = { it.id }) { host ->
                                val selected = copyHostId.value == host.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .clickable(enabled = !copyInProgress.value) { copyHostId.value = host.id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(host.name.ifBlank { host.host })
                                    if (selected) {
                                        Text("Selected", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = copyHostPassword.value,
                        onValueChange = { updatePasswordStateWithReveal(copyHostPassword, copyHostPasswordRevealIndex, it) },
                        label = { Text("Host password (optional)") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(copyHostPasswordRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    OutlinedTextField(
                        value = copyIdentityPassphrase.value,
                        onValueChange = { updatePasswordStateWithReveal(copyIdentityPassphrase, copyIdentityPassphraseRevealIndex, it) },
                        label = { Text("Identity key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(copyIdentityPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    copyError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !copyInProgress.value && hosts.isNotEmpty(),
                    onClick = {
                        val hostId = copyHostId.value
                        if (hostId.isNullOrBlank()) {
                            copyError.value = "Select a host."
                            return@TextButton
                        }
                        copyError.value = null
                        copyInProgress.value = true
                        scope.launch {
                            val success = onCopyKeyToHost(
                                identity.id,
                                hostId,
                                copyHostPassword.value.trim().ifBlank { null },
                                copyIdentityPassphrase.value.trim().ifBlank { null }
                            )
                            copyInProgress.value = false
                            if (success) {
                                onShowMessage("Key copied to host")
                                copyKeyIdentity.value = null
                            } else {
                                copyError.value = "Failed to copy key. Verify authentication details."
                            }
                        }
                    }
                ) {
                    Text(if (copyInProgress.value) "Copying..." else "Copy")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !copyInProgress.value,
                    onClick = { copyKeyIdentity.value = null }
                ) { Text("Cancel") }
            }
        )
    }

    shareIdentity.value?.let { identity ->
        AlertDialog(
            onDismissRequest = { shareIdentity.value = null },
            confirmButton = { TextButton(onClick = { shareIdentity.value = null }) { Text("Close") } },
            title = { Text("Share ${identity.label}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    shareQrBitmap.value?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Identity QR",
                            modifier = Modifier.size(220.dp)
                        )
                    } ?: Text("Unable to generate QR")
                    identity.username?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(identity.fingerprint, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }

    sharePassphrasePrompt.value?.let { identity ->
        AlertDialog(
            onDismissRequest = {
                sharePassphrasePrompt.value = null
                sharePassphraseError.value = null
            },
            title = { Text("Protect exported key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a passphrase to encrypt this identity's private key.")
                    OutlinedTextField(
                        value = sharePassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(sharePassphraseState, sharePassphraseRevealIndex, it)
                            sharePassphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(sharePassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_SHARE_PASSPHRASE_INPUT)
                    )
                    OutlinedTextField(
                        value = shareConfirmPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(shareConfirmPassphraseState, shareConfirmPassphraseRevealIndex, it)
                            sharePassphraseError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(shareConfirmPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.testTag(UiTestTags.IDENTITY_SHARE_CONFIRM_PASSPHRASE_INPUT)
                    )
                    sharePassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val phrase = sharePassphraseState.value
                    when {
                        phrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH ->
                            sharePassphraseError.value =
                                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        phrase != shareConfirmPassphraseState.value -> sharePassphraseError.value = "Passphrases do not match."
                        else -> {
                            val bitmap = generateIdentityQr(identity, phrase)
                            if (bitmap != null) {
                                shareQrBitmap.value = bitmap
                                shareIdentity.value = identity
                                sharePassphrasePrompt.value = null
                                sharePassphraseState.value = phrase
                                shareConfirmPassphraseState.value = phrase
                                sharePassphraseError.value = null
                                ExportPassphraseCache.identity = phrase
                            } else {
                                sharePassphraseError.value = "Unable to export key. Ensure the key exists and try again."
                            }
                        }
                    }
                }) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sharePassphrasePrompt.value = null
                    sharePassphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }

    pendingKeyImport.value?.let { (identityId, payload) ->
        AlertDialog(
            onDismissRequest = {
                pendingKeyImport.value = null
                importPassphraseState.value = ""
                importPassphraseError.value = null
            },
            title = { Text("Decrypt imported key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when exporting this identity.")
                    OutlinedTextField(
                        value = importPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(importPassphraseState, importPassphraseRevealIndex, it)
                            importPassphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(importPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    importPassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val phrase = importPassphraseState.value
                    if (phrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH) {
                        importPassphraseError.value =
                            "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        return@TextButton
                    }
                    val success = onImportIdentityKey(identityId, payload, phrase)
                    if (success) {
                        onShowMessage("Private key imported")
                        pendingKeyImport.value = null
                        importPassphraseState.value = ""
                        importPassphraseError.value = null
                    } else {
                        importPassphraseError.value = "Incorrect passphrase."
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingKeyImport.value = null
                    importPassphraseState.value = ""
                    importPassphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }

    pendingOverwrite.value?.let { overwrite ->
        AlertDialog(
            onDismissRequest = {
                pendingOverwrite.value = null
            },
            title = { Text("Replace identity?") },
            text = { Text("An identity with that fingerprint already exists. Overwrite it with the new details?") },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(
                        overwrite.targetId,
                        overwrite.label,
                        overwrite.fingerprint,
                        overwrite.username,
                        overwrite.group
                    )
                    pendingOverwrite.value = null
                    if (!overwrite.keyPayload.isNullOrBlank()) {
                        pendingKeyImport.value = overwrite.targetId to overwrite.keyPayload
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite.value = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun KeyInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBrowse: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true
        )
        IconButton(onClick = onBrowse) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Browse $label"
            )
        }
    }
}

@Composable
private fun RowScope.KeyGenOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.weight(1f),
        onClick = onClick
    ) {
        Text(if (selected) "$label *" else label)
    }
}

private fun applyGeneratedKeyPair(
    generated: GeneratedIdentityKeyPair,
    privateKeyState: androidx.compose.runtime.MutableState<String>,
    publicKeyState: androidx.compose.runtime.MutableState<String>,
    fingerprintState: androidx.compose.runtime.MutableState<String>,
    passphraseState: androidx.compose.runtime.MutableState<String>,
    keyStatusState: androidx.compose.runtime.MutableState<String?>,
    passphrase: String
) {
    privateKeyState.value = generated.privateKey
    publicKeyState.value = generated.publicKey
    fingerprintState.value = generated.fingerprint
    passphraseState.value = passphrase
    keyStatusState.value = "Generated ${generated.publicKey.substringBefore(' ')} keypair."
}

private fun computeFingerprintFromKeyMaterial(keyText: String): String {
    val publicFingerprint = computeSshPublicKeyFingerprint(keyText)
    if (!publicFingerprint.isNullOrBlank()) return publicFingerprint
    val digest = MessageDigest.getInstance("SHA-256").digest(keyText.toByteArray())
    val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
    return "SHA256:$encoded"
}
