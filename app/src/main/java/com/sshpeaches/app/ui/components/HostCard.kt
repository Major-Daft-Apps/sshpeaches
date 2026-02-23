package com.sshpeaches.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import android.util.Base64
import android.graphics.Color as AndroidColor
import android.widget.Toast
import org.json.JSONObject
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.OsFamily
import com.sshpeaches.app.data.model.OsMetadata
import com.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.sshpeaches.app.security.SecurityManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sshpeaches.app.ui.util.ExportPassphraseCache

@Composable
fun HostCard(
    host: HostConnection,
    modifier: Modifier = Modifier,
    onToggleFavorite: (String) -> Unit = {},
    onAction: (HostConnection, ConnectionMode) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val showInfo = remember { mutableStateOf(false) }
    val showQr = remember { mutableStateOf(false) }
    val showPassphrasePrompt = remember { mutableStateOf(false) }
    val passphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val confirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val passphraseError = remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(host.id) {
        showQr.value = false
        showPassphrasePrompt.value = false
        passphraseState.value = ExportPassphraseCache.host.orEmpty()
        confirmPassphraseState.value = ExportPassphraseCache.host.orEmpty()
        passphraseError.value = null
        qrBitmap.value = null
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
                    val imageContext = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(imageContext)
                            .data(host.osMetadata.iconRes())
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = host.osMetadata.label(),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(28.dp)
                    )
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
                    selected = host.defaultMode == ConnectionMode.SSH,
                    onClick = { onAction(host, ConnectionMode.SSH) }
                )
                HostActionButton(
                    label = "SFTP",
                    selected = host.defaultMode == ConnectionMode.SFTP,
                    onClick = { onAction(host, ConnectionMode.SFTP) }
                )
                HostActionButton(
                    label = "SCP",
                    selected = host.defaultMode == ConnectionMode.SCP,
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
                            when {
                                !SecurityManager.isPinSet() -> {
                                    Toast.makeText(context, "Set a PIN before exporting passwords.", Toast.LENGTH_SHORT).show()
                                }
                                SecurityManager.isLocked() -> {
                                    Toast.makeText(context, "Unlock with your PIN before exporting.", Toast.LENGTH_SHORT).show()
                                }
                                else -> showPassphrasePrompt.value = true
                            }
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
            onDismissRequest = { showInfo.value = false },
            confirmButton = { TextButton(onClick = { showInfo.value = false }) { Text("Close") } },
            title = { Text(host.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Address: ${host.host}:${host.port}")
                    Text("User: ${host.username}")
                    host.group?.let { Text("Group: $it") }
                    Text("Auth: ${host.preferredAuth.toSentenceCaseLabel()}")
                    Text("Default: ${host.defaultMode.toSentenceCaseLabel()}")
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
    val json = JSONObject().apply {
        put("id", host.id)
        put("name", host.name)
        put("host", host.host)
        put("port", host.port)
        put("user", host.username)
        put("prefAuth", host.preferredAuth.name)
        put("mode", host.defaultMode.name)
        put("group", host.group ?: "")
        put("notes", host.notes)
        put("hasPassword", host.hasPassword)
        put("useMosh", host.useMosh)
        put("preferredForwardId", host.preferredForwardId ?: "")
        put("startupScript", host.startupScript)
        put("backgroundBehavior", host.backgroundBehavior.name)
        if (host.hasPassword && !passphrase.isNullOrBlank()) {
            val encrypted = SecurityManager.exportHostPasswordPayload(host.id, passphrase) ?: return null
            put("pwdPayload", encrypted)
        }
    }
    val payload = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
    OsMetadata.Undetected -> "?"
}

@Composable
private fun OsMetadata.toColor(): Color = when (this) {
    is OsMetadata.Known -> Color(android.graphics.Color.parseColor(family.colorHex))
    else -> MaterialTheme.colorScheme.primary
}

private fun OsMetadata.iconRes(): Int = when (this) {
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
        else -> R.raw.linux
    }
    else -> R.raw.linux
}
