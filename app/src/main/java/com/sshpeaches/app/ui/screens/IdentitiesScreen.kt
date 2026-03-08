package com.majordaftapps.sshpeaches.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrPayload
import com.majordaftapps.sshpeaches.app.ui.components.generateIdentityQr
import com.majordaftapps.sshpeaches.app.ui.components.processIdentityQrImport
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.ui.util.rememberDialogBodyMaxHeight
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
    val keyPayload: String?
)

private enum class DialogKeyFileType {
    PRIVATE_KEY,
    PUBLIC_KEY
}

@Composable
fun IdentitiesScreen(
    items: List<Identity>,
    hosts: List<HostConnection>,
    isLocked: Boolean,
    onAdd: (label: String, fingerprint: String, username: String?, suppliedId: String?) -> Unit = { _, _, _, _ -> },
    onUpdate: (id: String, label: String, fingerprint: String, username: String?) -> Unit = { _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onImportIdentityKey: (id: String, payload: String, passphrase: String) -> Boolean = { _, _, _ -> false },
    onImportIdentityKeyPlain: (id: String, key: String) -> Boolean = { _, _ -> false },
    onStoreIdentityPublicKey: (id: String, key: String) -> Boolean = { _, _ -> false },
    onStoreIdentityKeyPassphrase: (id: String, passphrase: String?) -> Unit = { _, _ -> },
    onCopyKeyToHost: suspend (identityId: String, hostId: String, hostPassword: String?, identityPassphrase: String?) -> Boolean = { _, _, _, _ -> false },
    onRemoveIdentityKey: (id: String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    editMode: Boolean = false,
    onImportFromQr: () -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val search = remember { mutableStateOf("") }
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
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
    val shareConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.identity.orEmpty()) }
    val sharePassphraseError = remember { mutableStateOf<String?>(null) }
    val pendingOverwrite = remember { mutableStateOf<IdentityOverwrite?>(null) }
    val pendingKeyImport = remember { mutableStateOf<Pair<String, String>?>(null) }
    val importPassphraseState = remember { mutableStateOf("") }
    val importPassphraseError = remember { mutableStateOf<String?>(null) }
    val fileImportTarget = remember { mutableStateOf<String?>(null) }
    val dialogBodyMaxHeight = rememberDialogBodyMaxHeight()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogKeyPassphraseState = remember { mutableStateOf("") }
    val showGenerateDialog = remember { mutableStateOf(false) }
    val generationAlgorithm = remember { mutableStateOf(IdentityKeyAlgorithm.ED25519) }
    val generationRsaBits = remember { mutableStateOf(4096) }
    val generationCurve = remember { mutableStateOf(IdentityEcdsaCurve.P256) }
    val generationPassphrase = remember { mutableStateOf("") }
    val generationConfirmPassphrase = remember { mutableStateOf("") }
    val generationError = remember { mutableStateOf<String?>(null) }
    val copyKeyIdentity = remember { mutableStateOf<Identity?>(null) }
    val copyHostId = remember { mutableStateOf<String?>(null) }
    val copyHostPassword = remember { mutableStateOf("") }
    val copyIdentityPassphrase = remember { mutableStateOf("") }
    val copyInProgress = remember { mutableStateOf(false) }
    val copyError = remember { mutableStateOf<String?>(null) }

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
                    keyPayload = processed.overwrite.encryptedKeyPayload
                )
            }
            is IdentityQrImportResult.Ready -> {
                val imported = processed.data.identity
                onAdd(
                    imported.label,
                    imported.fingerprint,
                    imported.username,
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
        fingerprintState.value = identity?.fingerprint ?: ""
        dialogPrivateKeyState.value = ""
        dialogPublicKeyState.value = ""
        dialogKeyPassphraseState.value = ""
        dialogFileType.value = null
        dialogKeyStatus.value = null
        dialogError.value = null
        generationError.value = null
        showGenerateDialog.value = false
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
        dialogFileType.value = null
        showGenerateDialog.value = false
    }

    val filteredItems = items.filter {
        it.label.contains(search.value, ignoreCase = true) ||
            it.fingerprint.contains(search.value, ignoreCase = true) ||
            (it.username?.contains(search.value, ignoreCase = true) == true)
    }
    val showEmptyState = items.isEmpty() || filteredItems.isEmpty()
    LaunchedEffect(showEmptyState) {
        onEmptyStateVisibleChanged(showEmptyState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_IDENTITIES)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { openDialog(null) }, modifier = Modifier.weight(1f)) {
                        Text("Add identity")
                    }
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan SSH identity QR")
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
            if (items.isEmpty()) {
                item { EmptyState(itemLabel = "identity") }
            } else if (filteredItems.isEmpty()) {
                item { EmptyState(itemLabel = "result") }
            } else {
                items(filteredItems, key = { it.id }) { identity ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
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
                                        .clickable {
                                            copyKeyIdentity.value = identity
                                            copyHostId.value = hosts.firstOrNull()?.id
                                            copyHostPassword.value = ""
                                            copyIdentityPassphrase.value = ""
                                            copyError.value = null
                                            copyInProgress.value = false
                                        }
                                )
                                if (editMode) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(start = 8.dp)
                                            .clickable { openDialog(identity) }
                                    )
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { onDelete(identity.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
                        singleLine = true
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
                        onValueChange = { dialogKeyPassphraseState.value = it },
                        label = { Text("Key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(onClick = {
                        generationAlgorithm.value = IdentityKeyAlgorithm.ED25519
                        generationRsaBits.value = 4096
                        generationCurve.value = IdentityEcdsaCurve.P256
                        generationPassphrase.value = ""
                        generationConfirmPassphrase.value = ""
                        generationError.value = null
                        showGenerateDialog.value = true
                    }) {
                        Text("Generate keypair")
                    }
                    OutlinedTextField(
                        value = fingerprintState.value,
                        onValueChange = {},
                        label = { Text("Fingerprint") },
                        singleLine = true,
                        readOnly = true
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
                            null
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
                    Text("Algorithm")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyGenOptionButton(
                            label = "Ed25519",
                            selected = generationAlgorithm.value == IdentityKeyAlgorithm.ED25519,
                            onClick = { generationAlgorithm.value = IdentityKeyAlgorithm.ED25519 }
                        )
                        KeyGenOptionButton(
                            label = "RSA",
                            selected = generationAlgorithm.value == IdentityKeyAlgorithm.RSA,
                            onClick = { generationAlgorithm.value = IdentityKeyAlgorithm.RSA }
                        )
                        KeyGenOptionButton(
                            label = "ECDSA",
                            selected = generationAlgorithm.value == IdentityKeyAlgorithm.ECDSA,
                            onClick = { generationAlgorithm.value = IdentityKeyAlgorithm.ECDSA }
                        )
                    }
                    if (generationAlgorithm.value == IdentityKeyAlgorithm.RSA) {
                        Text("RSA bits")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2048, 3072, 4096).forEach { bits ->
                                KeyGenOptionButton(
                                    label = bits.toString(),
                                    selected = generationRsaBits.value == bits,
                                    onClick = { generationRsaBits.value = bits }
                                )
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
                            generationPassphrase.value = it
                            generationError.value = null
                        },
                        label = { Text("Key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = generationConfirmPassphrase.value,
                        onValueChange = {
                            generationConfirmPassphrase.value = it
                            generationError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
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
                        if (phrase.length < 4) {
                            generationError.value = "Passphrase must be at least 4 characters."
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
                        onValueChange = { copyHostPassword.value = it },
                        label = { Text("Host password (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = copyIdentityPassphrase.value,
                        onValueChange = { copyIdentityPassphrase.value = it },
                        label = { Text("Identity key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
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
                            sharePassphraseState.value = it
                            sharePassphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = shareConfirmPassphraseState.value,
                        onValueChange = {
                            shareConfirmPassphraseState.value = it
                            sharePassphraseError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
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
                        phrase.length < 4 -> sharePassphraseError.value = "Passphrase must be at least 4 characters."
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
                    if (phrase.length < 4) {
                        importPassphraseError.value = "Passphrase required."
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
                        overwrite.username
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
