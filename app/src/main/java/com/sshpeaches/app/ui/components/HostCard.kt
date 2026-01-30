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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.OsFamily
import com.sshpeaches.app.data.model.OsMetadata
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun HostCard(host: HostConnection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val showInfo = remember { mutableStateOf(false) }
    val showQr = remember { mutableStateOf(false) }

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
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
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
                TextButton(onClick = { /* TODO status */ }) {
                    Text(host.lastUsedEpochMillis?.let { "Last used" } ?: "New")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HostActionButton(label = "SSH", selected = host.defaultMode == ConnectionMode.SSH)
                HostActionButton(label = "SFTP", selected = host.defaultMode == ConnectionMode.SFTP)
                HostActionButton(label = "SCP", selected = host.defaultMode == ConnectionMode.SCP)
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.clickable { showInfo.value = true }
                )
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = "Share",
                    modifier = Modifier.clickable {
                        showQr.value = true
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
                    Text("Auth: ${host.preferredAuth}")
                    Text("Default: ${host.defaultMode}")
                }
            }
        )
    }

    if (showQr.value) {
        val qrBitmap = remember(host) { generateQr(host) }
        AlertDialog(
            onDismissRequest = { showQr.value = false },
            confirmButton = { TextButton(onClick = { showQr.value = false }) { Text("Close") } },
            title = { Text("Share ${host.name}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    qrBitmap?.let {
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
}

private fun generateQr(host: HostConnection): Bitmap? {
    val json = buildString {
        append("{")
        append("\"id\":\"${host.id}\",")
        append("\"name\":\"${host.name}\",")
        append("\"host\":\"${host.host}\",")
        append("\"port\":${host.port},")
        append("\"user\":\"${host.username}\"")
        append("}")
    }
    val payload = android.util.Base64.encodeToString(json.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
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
private fun HostActionButton(label: String, selected: Boolean) {
    TextButton(onClick = { /* TODO */ }) {
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
