package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.majordaftapps.sshpeaches.app.util.snippetReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostCard(
    host: HostConnection,
    snippets: List<Snippet> = emptyList(),
    modifier: Modifier = Modifier,
    onToggleFavorite: (String) -> Unit = {},
    onAction: (HostConnection, ConnectionMode) -> Unit = { _, _ -> },
    canRunInfoCommands: Boolean = false,
    onRunInfoCommand: (HostConnection, String) -> Boolean = { _, _ -> false },
    onInfoCommandsChange: (HostConnection, List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val showInfo = remember { mutableStateOf(false) }
    val showQr = remember { mutableStateOf(false) }
    val showPassphrasePrompt = remember { mutableStateOf(false) }
    val passphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val confirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val passphraseError = remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val infoCommandsState = rememberSaveable(host.id) {
        mutableStateOf(host.infoCommands)
    }
    val infoSnippetExpanded = rememberSaveable(host.id) { mutableStateOf(false) }
    val infoCommandStatus = rememberSaveable(host.id) { mutableStateOf<String?>(null) }

    fun persistInfoCommands(next: List<String>) {
        val normalized = next.map { it.trim() }.filter { it.isNotBlank() }
        infoCommandsState.value = normalized
    }

    fun commitInfoCommands() {
        onInfoCommandsChange(host, infoCommandsState.value)
    }

    LaunchedEffect(host.id, host.infoCommands) {
        showQr.value = false
        showPassphrasePrompt.value = false
        passphraseState.value = ExportPassphraseCache.host.orEmpty()
        confirmPassphraseState.value = ExportPassphraseCache.host.orEmpty()
        passphraseError.value = null
        qrBitmap.value = null
        infoCommandStatus.value = null
        infoSnippetExpanded.value = false
        infoCommandsState.value = host.infoCommands
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val color = host.osMetadata.toColor()
                Box(
                    modifier = Modifier
                        .background(color = color, shape = CircleShape)
                        .padding(10.dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconRes = host.osMetadata.iconResOrNull()
                    if (iconRes != null) {
                        val imageContext = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(imageContext)
                                .data(iconRes)
                                .decoderFactory(SvgDecoder.Factory())
                                .build(),
                            contentDescription = host.osMetadata.label(),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = host.osMetadata.label(),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                    Text("${host.username}@${host.host}:${host.port}", style = MaterialTheme.typography.bodyMedium)
                    host.group?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = { onToggleFavorite(host.id) }) {
                    Icon(
                        imageVector = if (host.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (host.favorite) "Unfavorite" else "Favorite"
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HostActionButton(
                    label = "SSH",
                    selected = true,
                    onClick = { onAction(host, ConnectionMode.SSH) }
                )
                HostActionButton(
                    label = "SFTP",
                    selected = false,
                    onClick = { onAction(host, ConnectionMode.SFTP) }
                )
                HostActionButton(
                    label = "SCP",
                    selected = false,
                    onClick = { onAction(host, ConnectionMode.SCP) }
                )
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.clickable { showInfo.value = true }
                )
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = "Share",
                    modifier = Modifier.clickable {
                        if (host.hasPassword) {
                            showPassphrasePrompt.value = true
                        } else {
                            qrBitmap.value = generateQr(host, passphrase = null)
                            if (qrBitmap.value != null) {
                                showQr.value = true
                            } else {
                                Toast.makeText(context, "Unable to generate QR.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showInfo.value) {
        AlertDialog(
            onDismissRequest = {
                commitInfoCommands()
                showInfo.value = false
            },
            confirmButton = {
                TextButton(onClick = {
                    commitInfoCommands()
                    showInfo.value = false
                }) { Text("Close") }
            },
            title = { Text(host.name) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Address: ${host.host}:${host.port}")
                    Text("User: ${host.username}")
                    host.group?.let { Text("Group: $it") }
                    Text("Auth: ${host.preferredAuth.toSentenceCaseLabel()}")
                    Text("Transport: ${if (host.useMosh) "Mosh" else "SSH"}")
                    Text("Info snippets", style = MaterialTheme.typography.titleSmall)
                    if (snippets.isEmpty()) {
                        Text(
                            "No snippets available. Create snippets first.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = infoSnippetExpanded.value,
                            onExpandedChange = { infoSnippetExpanded.value = !infoSnippetExpanded.value }
                        ) {
                            TextField(
                                value = "Add snippet",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Snippet") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = infoSnippetExpanded.value)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = infoSnippetExpanded.value,
                                onDismissRequest = { infoSnippetExpanded.value = false }
                            ) {
                                snippets.forEach { snippet ->
                                    val token = snippetReference(snippet.id)
                                    val alreadyAdded = infoCommandsState.value.contains(token)
                                    DropdownMenuItem(
                                        text = { Text(snippet.title) },
                                        enabled = !alreadyAdded,
                                        onClick = {
                                            if (!alreadyAdded) {
                                                persistInfoCommands(infoCommandsState.value + token)
                                                infoCommandStatus.value = "Added snippet: ${snippet.title}"
                                            }
                                            infoSnippetExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (infoCommandsState.value.isEmpty()) {
                        Text(
                            "No info snippets selected.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        infoCommandsState.value.forEachIndexed { index, entry ->
                            val snippetId = parseSnippetReference(entry)
                            val snippet = snippets.firstOrNull { it.id == snippetId }
                            val runCommand = snippet?.command ?: if (snippetId == null) entry.trim() else ""
                            val displayTitle = when {
                                snippet != null -> snippet.title
                                snippetId != null -> "Missing snippet"
                                else -> "Legacy command"
                            }
                            val displayBody = when {
                                snippet != null -> snippet.command
                                snippetId != null -> "Snippet no longer exists. Remove this entry."
                                else -> entry
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(displayTitle, style = MaterialTheme.typography.labelLarge)
                                Text(displayBody, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = canRunInfoCommands && runCommand.isNotBlank(),
                                        onClick = {
                                            val dispatched = onRunInfoCommand(host, runCommand)
                                            infoCommandStatus.value = if (dispatched) {
                                                "Queued command."
                                            } else {
                                                "No active SSH session for this host."
                                            }
                                        }
                                    ) {
                                        Text("Run")
                                    }
                                    TextButton(
                                        onClick = {
                                            val current = infoCommandsState.value.toMutableList()
                                            current.removeAt(index)
                                            persistInfoCommands(current)
                                            if (current.isEmpty()) {
                                                infoCommandStatus.value = "No info snippets selected."
                                            }
                                        }
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                persistInfoCommands(emptyList())
                                infoCommandStatus.value = "Cleared info snippets."
                            }
                        ) {
                            Text("Clear all")
                        }
                    }
                    if (!canRunInfoCommands) {
                        Text(
                            "Start an SSH session to run commands.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    infoCommandStatus.value?.let { status ->
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        )
    }

    if (showQr.value) {
        AlertDialog(
            onDismissRequest = { showQr.value = false },
            confirmButton = { TextButton(onClick = { showQr.value = false }) { Text("Close") } },
            title = { Text("Share ${host.name}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    qrBitmap.value?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Host QR",
                            modifier = Modifier
                                .size(220.dp)
                        )
                    } ?: Text("Unable to generate QR")
                    Text(
                        "${host.username}@${host.host}:${host.port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }

    if (showPassphrasePrompt.value) {
        AlertDialog(
            onDismissRequest = {
                showPassphrasePrompt.value = false
                passphraseState.value = ""
                confirmPassphraseState.value = ""
                passphraseError.value = null
            },
            title = { Text("Protect exported password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a passphrase to encrypt this host's password. You will need it on the receiving device.")
                    OutlinedTextField(
                        value = passphraseState.value,
                        onValueChange = {
                            passphraseState.value = it
                            passphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPassphraseState.value,
                        onValueChange = {
                            confirmPassphraseState.value = it
                            passphraseError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true
                    )
                    passphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val passphrase = passphraseState.value
                    when {
                        passphrase.length < 4 -> {
                            passphraseError.value = "Passphrase must be at least 4 characters."
                        }
                        passphrase != confirmPassphraseState.value -> {
                            passphraseError.value = "Passphrases do not match."
                        }
                        else -> {
                            val bitmap = generateQr(host, passphrase)
                            if (bitmap != null) {
                                qrBitmap.value = bitmap
                                showPassphrasePrompt.value = false
                                showQr.value = true
                                ExportPassphraseCache.host = passphrase
                                passphraseState.value = passphrase
                                confirmPassphraseState.value = passphrase
                                passphraseError.value = null
                            } else {
                                passphraseError.value = "Unable to export password. Unlock the app and try again."
                            }
                        }
                    }
                }) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPassphrasePrompt.value = false
                    passphraseState.value = ""
                    confirmPassphraseState.value = ""
                    passphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }
}

private fun generateQr(host: HostConnection, passphrase: String?): Bitmap? {
    val encrypted = if (host.hasPassword && !passphrase.isNullOrBlank()) {
        SecurityManager.exportHostPasswordPayload(host.id, passphrase) ?: return null
    } else {
        null
    }
    val payload = encodeHostPayload(host = host, encryptedPasswordPayload = encrypted)
    return runCatching {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    }.getOrNull()
}

@Composable
private fun HostActionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun OsMetadata.label(): String = when (this) {
    is OsMetadata.Known -> family.displayName
    is OsMetadata.Custom -> label
    OsMetadata.Undetected -> "Unknown OS"
}


@Composable
private fun OsMetadata.toColor(): Color = when (this) {
    is OsMetadata.Known -> Color(android.graphics.Color.parseColor(family.colorHex))
    else -> Color(android.graphics.Color.parseColor(OsFamily.UNKNOWN.colorHex))
}

private fun OsMetadata.iconResOrNull(): Int? = when (this) {
    is OsMetadata.Known -> when (family) {
        OsFamily.UBUNTU -> R.raw.ubuntu
        OsFamily.DEBIAN -> R.raw.debian
        OsFamily.FEDORA -> R.raw.fedora
        OsFamily.MINT -> R.raw.linux_mint
        OsFamily.ARCH -> R.raw.arch
        OsFamily.SUSE -> R.raw.suse
        OsFamily.REDHAT -> R.raw.redhat
        OsFamily.MAC -> R.raw.apple
        OsFamily.WINDOWS -> R.raw.windows
        OsFamily.BSD -> R.raw.bsd
        OsFamily.GENERIC -> R.raw.linux
        else -> null
    }
    else -> null
}
