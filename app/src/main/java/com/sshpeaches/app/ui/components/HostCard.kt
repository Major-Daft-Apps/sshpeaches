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
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import android.graphics.Bitmap
import android.widget.Toast
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.ssh.HostSystemInfo
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.updatePasswordStateWithReveal
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.ui.adaptive.desktopHoverable
import com.majordaftapps.sshpeaches.app.ui.adaptive.rememberDesktopHoverState
import com.majordaftapps.sshpeaches.app.ui.adaptive.secondaryClickToOpen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostCard(
    host: HostConnection,
    modifier: Modifier = Modifier,
    onToggleFavorite: (String) -> Unit = {},
    onAction: (HostConnection, ConnectionMode, FileTransferEntryMode?) -> Unit = { _, _, _ -> },
    onFetchSystemInfo: (HostConnection, (HostSystemInfo) -> Unit) -> Unit = { _, _ -> },
    onDetails: ((HostConnection) -> Unit)? = null,
    onEdit: ((HostConnection) -> Unit)? = null,
    onDelete: ((HostConnection) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showInfo = remember { mutableStateOf(false) }
    val showQr = remember { mutableStateOf(false) }
    val showPassphrasePrompt = remember { mutableStateOf(false) }
    val passphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val passphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val confirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val confirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val passphraseError = remember { mutableStateOf<String?>(null) }
    val qrBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val showOverflow = rememberSaveable(host.id) { mutableStateOf(false) }
    val systemInfo = remember(host.id) { mutableStateOf<HostSystemInfo?>(null) }
    val systemInfoLoading = remember(host.id) { mutableStateOf(false) }
    val hasDesktopActions = onDetails != null || onEdit != null || onDelete != null
    val (cardInteractionSource, cardHovered) = rememberDesktopHoverState(
        enabled = hasDesktopActions
    )
    AutoHidePasswordReveal(passphraseRevealIndex)
    AutoHidePasswordReveal(confirmPassphraseRevealIndex)

    fun requestSystemInfo() {
        systemInfoLoading.value = true
        onFetchSystemInfo(host) { result ->
            systemInfo.value = result
            systemInfoLoading.value = false
        }
    }

    LaunchedEffect(host.id) {
        showQr.value = false
        showPassphrasePrompt.value = false
        passphraseState.value = ExportPassphraseCache.host.orEmpty()
        confirmPassphraseState.value = ExportPassphraseCache.host.orEmpty()
        passphraseError.value = null
        qrBitmap.value = null
        showOverflow.value = false
        systemInfo.value = null
        systemInfoLoading.value = false
    }

    LaunchedEffect(showInfo.value, host.id) {
        if (showInfo.value) {
            requestSystemInfo()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .desktopHoverable(
                enabled = hasDesktopActions,
                interactionSource = cardInteractionSource
            )
            .secondaryClickToOpen(enabled = hasDesktopActions) {
                showOverflow.value = true
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = cardElevation(defaultElevation = if (cardHovered) 6.dp else 0.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { onToggleFavorite(host.id) },
                        modifier = Modifier.testTag(UiTestTags.hostFavorite(host.id))
                    ) {
                        Icon(
                            imageVector = if (host.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (host.favorite) "Unfavorite" else "Favorite"
                        )
                    }
                    if (hasDesktopActions) {
                        Box {
                            IconButton(
                                onClick = { showOverflow.value = true },
                                modifier = Modifier.testTag(UiTestTags.hostOverflowButton(host.id))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More actions"
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow.value,
                                onDismissRequest = { showOverflow.value = false }
                            ) {
                                onDetails?.let {
                                    DropdownMenuItem(
                                        text = { Text("Details") },
                                        onClick = {
                                            showOverflow.value = false
                                            it(host)
                                        }
                                    )
                                }
                                onEdit?.let {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            showOverflow.value = false
                                            it(host)
                                        },
                                        modifier = Modifier.testTag(UiTestTags.hostOverflowAction(host.id, "edit"))
                                    )
                                }
                                onDelete?.let {
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showOverflow.value = false
                                            it(host)
                                        },
                                        modifier = Modifier.testTag(UiTestTags.hostOverflowAction(host.id, "delete"))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HostActionButton(
                        icon = Icons.Default.Terminal,
                        contentDescription = "SSH terminal",
                        selected = true,
                        modifier = Modifier.testTag(UiTestTags.hostAction(host.id, "ssh")),
                        onClick = { onAction(host, ConnectionMode.SSH, null) }
                    )
                    HostActionButton(
                        icon = Icons.Default.CloudUpload,
                        contentDescription = "Upload files",
                        selected = false,
                        modifier = Modifier.testTag(UiTestTags.hostAction(host.id, "sftp")),
                        onClick = {
                            onAction(host, ConnectionMode.SCP, FileTransferEntryMode.UPLOAD)
                        }
                    )
                    HostActionButton(
                        icon = Icons.Default.CloudDownload,
                        contentDescription = "Download files",
                        selected = false,
                        modifier = Modifier.testTag(UiTestTags.hostAction(host.id, "scp")),
                        onClick = {
                            onAction(host, ConnectionMode.SCP, FileTransferEntryMode.DOWNLOAD)
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showInfo.value = true },
                        modifier = Modifier.testTag(UiTestTags.hostAction(host.id, "info"))
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info"
                        )
                    }
                    IconButton(
                        onClick = {
                            if (host.hasPassword) {
                                showPassphrasePrompt.value = true
                            } else {
                                scope.launch {
                                    qrBitmap.value = generateHostQr(host, passphrase = null)
                                    if (qrBitmap.value != null) {
                                        showQr.value = true
                                    } else {
                                        Toast.makeText(context, "Unable to generate QR.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag(UiTestTags.hostAction(host.id, "qr"))
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Share"
                        )
                    }
                }
            }
        }
    }

    if (showInfo.value) {
        AlertDialog(
            onDismissRequest = { showInfo.value = false },
            modifier = Modifier.testTag(UiTestTags.HOST_INFO_DIALOG),
            confirmButton = {
                TextButton(onClick = { showInfo.value = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(
                    enabled = !systemInfoLoading.value,
                    onClick = { requestSystemInfo() },
                    modifier = Modifier.testTag(UiTestTags.HOST_INFO_REFRESH_BUTTON)
                ) { Text("Refresh") }
            },
            title = { Text(host.name) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (systemInfoLoading.value && systemInfo.value == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Collecting live system info…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        val info = systemInfo.value
                        if (info == null) {
                            Text(
                                "Live system info is not available yet.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            if (systemInfoLoading.value) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Refreshing…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            info.error?.takeIf { it.isNotBlank() }?.let { error ->
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag(UiTestTags.HOST_INFO_STATUS)
                                )
                            }
                            HostInfoRow("OS", info.osName)
                            HostInfoRow("Version", info.osVersion)
                            HostInfoRow("Hostname", info.hostname)
                            HostInfoRow("Kernel", info.kernel)
                            HostInfoRow("Arch", info.architecture)
                            HostInfoRow(
                                "CPU",
                                when {
                                    !info.cpu.isNullOrBlank() && !info.cpuCores.isNullOrBlank() ->
                                        "${info.cpu} (${info.cpuCores} cores)"
                                    else -> info.cpu ?: info.cpuCores?.let { "$it cores" }
                                }
                            )
                            HostInfoBlock("Memory (free -h)", info.freeOutput)
                            if (info.freeOutput.isNullOrBlank()) {
                                HostInfoRow("Memory", info.memory)
                            }
                            HostInfoBlock("Disk (df -h)", info.dfOutput)
                            if (info.dfOutput.isNullOrBlank()) {
                                HostInfoRow("Disk /", info.disk)
                            }
                            HostInfoRow("Uptime", info.uptime)
                        }
                    }
                }
            }
        )
    }

    if (showQr.value) {
        AlertDialog(
            onDismissRequest = { showQr.value = false },
            modifier = Modifier.testTag(UiTestTags.HOST_QR_DIALOG),
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
            modifier = Modifier.testTag(UiTestTags.HOST_EXPORT_PASSWORD_DIALOG),
            title = { Text("Protect exported password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a passphrase to encrypt this host's password. You will need it on the receiving device.")
                    OutlinedTextField(
                        value = passphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(passphraseState, passphraseRevealIndex, it)
                            passphraseError.value = null
                        },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(passphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.testTag(UiTestTags.HOST_EXPORT_PASSWORD_INPUT)
                    )
                    OutlinedTextField(
                        value = confirmPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(confirmPassphraseState, confirmPassphraseRevealIndex, it)
                            passphraseError.value = null
                        },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(confirmPassphraseRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.testTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_INPUT)
                    )
                    passphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(UiTestTags.HOST_EXPORT_PASSWORD_CONFIRM_BUTTON),
                    onClick = {
                    val passphrase = passphraseState.value
                    when {
                        passphrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH -> {
                            passphraseError.value =
                                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        }
                        passphrase != confirmPassphraseState.value -> {
                            passphraseError.value = "Passphrases do not match."
                        }
                        else -> {
                            scope.launch {
                                val bitmap = generateHostQr(host, passphrase)
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

@Composable
private fun HostInfoRow(label: String, value: String?) {
    val display = value?.trim()?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(display, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HostInfoBlock(label: String, value: String?) {
    val display = value?.trim()?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            display,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun HostActionButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun OsMetadata.label(): String = when (this) {
    is OsMetadata.Known -> family.displayName
    is OsMetadata.Custom -> label
    OsMetadata.Undetected -> "Unknown OS"
}


@Composable
private fun OsMetadata.toColor(): Color = when (this) {
    is OsMetadata.Known -> Color(family.colorHex.toColorInt())
    else -> Color(OsFamily.UNKNOWN.colorHex.toColorInt())
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
        else -> R.raw.linux
    }
    else -> null
}
