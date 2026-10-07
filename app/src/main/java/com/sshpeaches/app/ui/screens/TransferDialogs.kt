package com.majordaftapps.sshpeaches.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.transfer.LanTransfer
import com.majordaftapps.sshpeaches.app.transfer.LanTransferPeer
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

enum class TransferMethod { FILE, QR, WIFI, SHARE }

internal fun renderQrBitmap(text: String, size: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bmp ->
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
    }
}.getOrNull()

/** "Export" / "Import": choose File, QR code, or Wi-Fi. */
@Composable
internal fun TransferMethodDialog(
    exporting: Boolean,
    onChoose: (TransferMethod) -> Unit,
    onDismiss: () -> Unit
) {
    @Composable
    fun option(method: TransferMethod, icon: ImageVector, title: String, detail: String, tag: String) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(detail) },
            leadingContent = { Icon(icon, contentDescription = null) },
            modifier = Modifier
                .clickable { onChoose(method) }
                .testTag(tag)
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UiTestTags.SETTINGS_TRANSFER_METHOD_DIALOG),
        title = { Text(if (exporting) "Export" else "Import") },
        text = {
            Column {
                if (exporting) {
                    option(TransferMethod.FILE, Icons.Default.Description, "File", "Save a file you can copy or back up.", UiTestTags.SETTINGS_EXPORT_FILE_BUTTON)
                    option(TransferMethod.QR, Icons.Default.QrCode2, "QR code", "Show a code for another phone to scan. Best for a few hosts.", UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
                    option(TransferMethod.WIFI, Icons.Default.Wifi, "Wi-Fi", "Send to a phone on the same network. Any size.", UiTestTags.SETTINGS_EXPORT_WIFI_BUTTON)
                    option(TransferMethod.SHARE, Icons.Default.Share, "Share", "Send the file with Bluetooth, Quick Share, email, or another app.", UiTestTags.SETTINGS_EXPORT_SHARE_BUTTON)
                } else {
                    option(TransferMethod.FILE, Icons.Default.Description, "File", "Open an SSHPeaches export file, such as one received over Bluetooth.", UiTestTags.SETTINGS_IMPORT_FILE_BUTTON)
                    option(TransferMethod.QR, Icons.Default.QrCodeScanner, "QR code", "Scan the code shown by another phone.", UiTestTags.SETTINGS_IMPORT_QR_BUTTON)
                    option(TransferMethod.WIFI, Icons.Default.Wifi, "Wi-Fi", "Receive from a phone on the same network.", UiTestTags.SETTINGS_IMPORT_WIFI_BUTTON)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shown on the sending phone while it waits for the receiver. */
@Composable
internal fun WifiSendDialog(
    host: String,
    port: Int,
    code: String,
    qr: Bitmap?,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(UiTestTags.SETTINGS_WIFI_SEND_DIALOG),
        title = { Text("Send over Wi-Fi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("On the other phone (same Wi-Fi), open Settings → Import → Wi-Fi, then scan this or enter the code.")
                qr?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "Wi-Fi transfer QR code", modifier = Modifier.size(220.dp))
                }
                Text(
                    LanTransfer.formatCode(code),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_WIFI_SEND_CODE)
                )
                Text(
                    "$host:$port",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_WIFI_SEND_ADDRESS)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Waiting for the other phone…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** Shown on the receiving phone: pick a nearby sender (or type its address), then enter the code. */
@Composable
internal fun WifiReceiveDialog(
    peers: List<LanTransferPeer>,
    receiving: Boolean,
    error: String?,
    onScanQr: () -> Unit,
    onReceive: (host: String, port: Int, code: String) -> Unit,
    onDismiss: () -> Unit
) {
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val parsedAddress = parseHostPort(address)
    val normalizedCode = LanTransfer.normalizeCode(code)
    AlertDialog(
        onDismissRequest = { if (!receiving) onDismiss() },
        modifier = Modifier.testTag(UiTestTags.SETTINGS_WIFI_RECEIVE_DIALOG),
        title = { Text("Receive over Wi-Fi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("On the other phone (same Wi-Fi), open Settings → Export → Wi-Fi.")
                OutlinedButton(onClick = onScanQr, enabled = !receiving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("Scan its QR code", modifier = Modifier.padding(start = 8.dp))
                }
                Text("Or pick it and type the code:", style = MaterialTheme.typography.bodySmall)
                if (peers.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Looking for phones on this network…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                peers.forEach { peer ->
                    val value = "${peer.host}:${peer.port}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { address = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = address == value, onClick = { address = value })
                        Column {
                            Text(peer.name)
                            Text(value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("Address (shown under the code)") },
                    placeholder = { Text("192.168.1.20:40123") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SETTINGS_WIFI_RECEIVE_ADDRESS)
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(16) },
                    label = { Text("Code") },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SETTINGS_WIFI_RECEIVE_CODE)
                )
                if (receiving) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Receiving…")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !receiving && parsedAddress != null && normalizedCode != null,
                onClick = {
                    val (host, port) = parsedAddress ?: return@TextButton
                    onReceive(host, port, normalizedCode ?: return@TextButton)
                },
                modifier = Modifier.testTag(UiTestTags.SETTINGS_WIFI_RECEIVE_CONFIRM)
            ) { Text("Receive") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !receiving) { Text("Cancel") } }
    )
}

internal fun parseHostPort(text: String): Pair<String, Int>? {
    val value = text.trim()
    val separator = value.lastIndexOf(':')
    if (separator <= 0) return null
    val host = value.substring(0, separator).removePrefix("[").removeSuffix("]")
    val port = value.substring(separator + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return host.takeIf { it.isNotBlank() }?.let { it to port }
}
