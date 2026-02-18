package com.sshpeaches.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.ui.components.EmptyState
import com.sshpeaches.app.ui.components.IdentityQrPayload
import com.sshpeaches.app.ui.components.decodeIdentityFromQr
import com.sshpeaches.app.ui.components.generateIdentityQr
import com.sshpeaches.app.ui.util.ExportPassphraseCache
import com.sshpeaches.app.util.isValidFingerprint
import java.util.UUID

private data class IdentityOverwrite(
    val targetId: String,
    val label: String,
    val fingerprint: String,
    val username: String?,
    val keyPayload: String?
)

@Composable
fun IdentitiesScreen(
    items: List<Identity>,
    pinConfigured: Boolean,
    isLocked: Boolean,
    onAdd: (label: String, fingerprint: String, username: String?, suppliedId: String?) -> Unit = { _, _, _, _ -> },
    onUpdate: (id: String, label: String, fingerprint: String, username: String?) -> Unit = { _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onImportIdentityKey: (id: String, payload: String, passphrase: String) -> Boolean = { _, _, _ -> false },
    onImportIdentityKeyPlain: (id: String, key: String) -> Boolean = { _, _ -> false },
    editMode: Boolean = false,
    onImportFromQr: () -> Unit = {}
) {
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
    val fingerprintState = remember { mutableStateOf("") }
    val usernameState = remember { mutableStateOf("") }
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
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = fileImportTarget.value ?: return@rememberLauncherForActivityResult
        fileImportTarget.value = null
        if (uri == null) return@rememberLauncherForActivityResult
        val keyText = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (keyText.isNullOrBlank()) {
            Toast.makeText(context, "Unable to read key file", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val success = onImportIdentityKeyPlain(targetId, keyText.trim())
        Toast.makeText(
            context,
            if (success) "Private key imported" else "Failed to import key",
            Toast.LENGTH_SHORT
        ).show()
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val payload = decodeIdentityFromQr(contents)
        val imported = payload?.identity
        if (imported != null) {
            val existing = items.find { it.fingerprint.equals(imported.fingerprint, true) }
            if (existing != null) {
                pendingOverwrite.value = IdentityOverwrite(
                    targetId = existing.id,
                    label = imported.label.ifBlank { existing.label },
                    fingerprint = imported.fingerprint,
                    username = imported.username ?: existing.username,
                    keyPayload = payload.encryptedKeyPayload
                )
            } else {
                val targetId = imported.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                onAdd(
                    imported.label.ifBlank { "Imported Identity" },
                    imported.fingerprint,
                    imported.username,
                    targetId
                )
                onImportFromQr()
                    if (!payload.encryptedKeyPayload.isNullOrBlank()) {
                        if (!pinConfigured) {
                            Toast.makeText(context, "Set a PIN before importing encrypted keys.", Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            Toast.makeText(context, "Unlock with your PIN before importing encrypted keys.", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingKeyImport.value = targetId to payload.encryptedKeyPayload
                            importPassphraseState.value = ""
                            importPassphraseError.value = null
                        }
                } else {
                    Toast.makeText(context, "Identity imported", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Invalid identity QR", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDialog(identity: Identity?) {
        editingId.value = identity?.id
        labelState.value = identity?.label ?: ""
        fingerprintState.value = identity?.fingerprint ?: ""
        usernameState.value = identity?.username ?: ""
        dialogError.value = null
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("Import QR")
            }
        }
        if (items.isEmpty()) {
            item { EmptyState(itemLabel = "identity") }
        } else {
            items(items, key = { it.id }) { identity ->
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
                            if (identity.favorite) {
                                Icon(Icons.Default.Star, contentDescription = null)
                            }
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
                                            when {
                                                !pinConfigured -> Toast.makeText(context, "Set a PIN before exporting private keys.", Toast.LENGTH_SHORT).show()
                                                isLocked -> Toast.makeText(context, "Unlock with your PIN before exporting.", Toast.LENGTH_SHORT).show()
                                                else -> {
                                                    sharePassphrasePrompt.value = identity
                                                    sharePassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                    shareConfirmPassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                    sharePassphraseError.value = null
                                                }
                                            }
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
                                        if (!pinConfigured) {
                                            Toast.makeText(context, "Set a PIN before importing keys.", Toast.LENGTH_SHORT).show()
                                        } else if (isLocked) {
                                            Toast.makeText(context, "Unlock with your PIN before importing keys.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            fileImportTarget.value = identity.id
                                            fileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                        }
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

    if (showDialog.value) {
        val isEdit = editingId.value != null
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit identity" else "Add identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        label = { Text("Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = usernameState.value,
                        onValueChange = { usernameState.value = it },
                        label = { Text("Username (optional)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fingerprintState.value,
                        onValueChange = { fingerprintState.value = it },
                        label = { Text("Fingerprint") },
                        singleLine = true
                    )
                    dialogError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fp = fingerprintState.value.trim()
                    if (fp.isEmpty()) {
                        dialogError.value = "Fingerprint is required."
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
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            labelState.value,
                            fp,
                            usernameState.value.ifBlank { null }
                        )
                    } else {
                        onAdd(
                            labelState.value.ifBlank { "Identity ${UUID.randomUUID()}" },
                            fp,
                            usernameState.value.ifBlank { null },
                            null
                        )
                    }
                    closeDialog()
                }) { Text(if (editingId.value == null) "Add" else "Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                sharePassphraseError.value = "Unable to export key. Unlock the app and try again."
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
                        Toast.makeText(context, "Private key imported", Toast.LENGTH_SHORT).show()
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
                        if (!pinConfigured) {
                            Toast.makeText(context, "Set a PIN before importing encrypted keys.", Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            Toast.makeText(context, "Unlock with your PIN before importing encrypted keys.", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingKeyImport.value = overwrite.targetId to overwrite.keyPayload
                        }
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite.value = null }) { Text("Cancel") }
            }
        )
    }
}
