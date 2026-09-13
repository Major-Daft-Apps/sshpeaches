package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferSettings
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun AdvancedSettingsScreen(
    sftpTransferSettings: SftpTransferSettings,
    onSftpReadSizeChange: (Int) -> Unit,
    onSftpMaxRequestsChange: (Int) -> Unit,
    onParallelDownloadsChange: (Int) -> Unit,
    onApplySftpFastPreset: () -> Unit,
    onRestoreDefaultSettings: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val showRestoreDefaultsDialog = remember { mutableStateOf(false) }
    val sftpReadSizeState = remember(sftpTransferSettings.sftpReadSize) {
        mutableStateOf(sftpTransferSettings.sftpReadSize.toString())
    }
    val sftpMaxRequestsState = remember(sftpTransferSettings.sftpMaxRequests) {
        mutableStateOf(sftpTransferSettings.sftpMaxRequests.toString())
    }
    val parallelDownloadsState = remember(sftpTransferSettings.parallelDownloads) {
        mutableStateOf(sftpTransferSettings.parallelDownloads.toString())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_ADVANCED_SETTINGS)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("SFTP downloads", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep many read requests in flight so high-latency links stay busy. Larger chunks mean fewer round trips; parallel downloads share the existing session.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = sftpReadSizeState.value,
                        onValueChange = { next ->
                            val digits = next.filter { it.isDigit() }.take(7)
                            sftpReadSizeState.value = digits
                            val parsed = digits.toIntOrNull() ?: return@OutlinedTextField
                            val clamped = parsed.coerceIn(
                                SftpTransferSettings.MIN_SFTP_READ_SIZE,
                                SftpTransferSettings.MAX_SFTP_READ_SIZE
                            )
                            sftpReadSizeState.value = clamped.toString()
                            onSftpReadSizeChange(clamped)
                        },
                        label = { Text("Read size") },
                        supportingText = {
                            Text("Bytes per SFTP read (--sftp-read-size). Default 262144 (256 KiB).")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_SFTP_READ_SIZE_INPUT)
                    )
                    OutlinedTextField(
                        value = sftpMaxRequestsState.value,
                        onValueChange = { next ->
                            val digits = next.filter { it.isDigit() }.take(4)
                            sftpMaxRequestsState.value = digits
                            val parsed = digits.toIntOrNull() ?: return@OutlinedTextField
                            val clamped = parsed.coerceIn(
                                SftpTransferSettings.MIN_SFTP_MAX_REQUESTS,
                                SftpTransferSettings.MAX_SFTP_MAX_REQUESTS
                            )
                            sftpMaxRequestsState.value = clamped.toString()
                            onSftpMaxRequestsChange(clamped)
                        },
                        label = { Text("Outstanding requests") },
                        supportingText = {
                            Text("SFTP reads kept in flight (--sftp-max-requests). Default 128.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_SFTP_MAX_REQUESTS_INPUT)
                    )
                    OutlinedTextField(
                        value = parallelDownloadsState.value,
                        onValueChange = { next ->
                            val digits = next.filter { it.isDigit() }.take(3)
                            parallelDownloadsState.value = digits
                            val parsed = digits.toIntOrNull() ?: return@OutlinedTextField
                            val clamped = parsed.coerceIn(
                                SftpTransferSettings.MIN_PARALLEL_DOWNLOADS,
                                SftpTransferSettings.MAX_PARALLEL_DOWNLOADS
                            )
                            parallelDownloadsState.value = clamped.toString()
                            onParallelDownloadsChange(clamped)
                        },
                        label = { Text("Parallel downloads") },
                        supportingText = {
                            Text("Concurrent file transfers on one session (--parallel-downloads). Default 4.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_PARALLEL_DOWNLOADS_INPUT)
                    )
                    AdvancedSettingsToggleRow(
                        title = "Fast SFTP preset",
                        description = "Apply --sftp-fast: 262144-byte reads, 128 outstanding requests, 4 parallel downloads.",
                        checked = sftpTransferSettings.sftpFast,
                        onCheckedChange = { enabled ->
                            if (enabled) onApplySftpFastPreset()
                        },
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_SFTP_FAST_SWITCH)
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Restore defaults", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Reset app settings to default values. Hosts, identities, snippets, and saved secrets are unchanged.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { showRestoreDefaultsDialog.value = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_BUTTON)
                    ) {
                        Text("Restore default settings")
                    }
                }
            }
        }
    }

    if (showRestoreDefaultsDialog.value) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultsDialog.value = false },
            title = { Text("Restore default settings?") },
            text = {
                Text(
                    "This resets app settings (theme, terminal, lock timeout, host key preferences, diagnostics, SFTP transfers, and keyboard layout) to defaults."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestoreDefaultSettings()
                        showRestoreDefaultsDialog.value = false
                        onShowMessage("Settings restored to defaults.")
                    },
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_RESTORE_DEFAULTS_CONFIRM)
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultsDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdvancedSettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.align(Alignment.CenterVertically)
        )
    }
}
