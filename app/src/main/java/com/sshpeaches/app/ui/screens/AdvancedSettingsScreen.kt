package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
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
    onBoostTerminalToggle: (Boolean) -> Unit = {},
    showConnectionLatency: Boolean = false,
    onShowConnectionLatencyToggle: (Boolean) -> Unit = {},
    onBoostTcpFallbackToggle: (Boolean) -> Unit = {},
    onBoostPortsChange: (String) -> Unit = {},
    onRestoreDefaultSettings: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    terminalSettings: (@Composable () -> Unit)? = null
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
    val boostPortsState = remember(sftpTransferSettings.boostPorts) {
        mutableStateOf(sftpTransferSettings.boostPorts)
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
            terminalSettings?.invoke()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium)
                    AdvancedSettingsToggleRow(
                        title = "Show connection latency",
                        description = "Show each terminal and file browser session's round trip to the server (for example \"42 ms\") in its top bar, checked every 5 seconds. Tap it for the last minute's figures.",
                        checked = showConnectionLatency,
                        onCheckedChange = onShowConnectionLatencyToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_SHOW_LATENCY_SWITCH)
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("SFTP transfers", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each transfer splits the file into chunks and moves them over several connections of its own, so browsing never waits for it. Keeping requests in flight keeps high-latency links busy.",
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
                            Text("Bytes per SFTP read (--sftp-read-size). Default 261120 (255 KiB), the most OpenSSH sends.")
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
                        label = { Text("Requests in flight") },
                        supportingText = {
                            Text("Reads each connection keeps in flight, up to 8 MiB (--sftp-max-requests). Default 32.")
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
                        label = { Text("Connections per transfer") },
                        supportingText = {
                            Text("Connections a download or upload uses at once (--parallel-downloads). Default 4.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_PARALLEL_DOWNLOADS_INPUT)
                    )
                    AdvancedSettingsToggleRow(
                        title = "Fast SFTP preset",
                        description = "Apply --sftp-fast: 261120-byte reads, 32 requests in flight, 4 connections per transfer.",
                        checked = sftpTransferSettings.sftpFast,
                        onCheckedChange = { enabled ->
                            if (enabled) onApplySftpFastPreset()
                        },
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_SFTP_FAST_SWITCH)
                    )
                    AdvancedSettingsToggleRow(
                        title = "Boost terminal",
                        description = "With Boost on, also run SSH terminals through its helper, so a terminal survives dropped and changing networks like Mosh, without installing anything. Turn off to boost only the file browser.",
                        checked = sftpTransferSettings.boostTerminal,
                        onCheckedChange = onBoostTerminalToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BOOST_TERMINAL_SWITCH)
                    )
                    AdvancedSettingsToggleRow(
                        title = "Boost TCP fallback",
                        description = "When Boost can't get UDP through, reach its helper over TCP (TLS) instead of going back to SSH. Turn off to never use TCP for Boost.",
                        checked = sftpTransferSettings.boostTcpFallback,
                        onCheckedChange = onBoostTcpFallbackToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BOOST_TCP_FALLBACK_SWITCH)
                    )
                    val boostPortsValid = SftpTransferSettings.isValidBoostPorts(boostPortsState.value)
                    OutlinedTextField(
                        value = boostPortsState.value,
                        onValueChange = { next ->
                            val text = next.filter { it.isDigit() || it == '-' || it == ' ' }.take(11)
                            boostPortsState.value = text
                            if (SftpTransferSettings.isValidBoostPorts(text)) onBoostPortsChange(text)
                        },
                        label = { Text("Boost ports") },
                        supportingText = {
                            Text(
                                if (boostPortsValid) {
                                    "Server ports Boost's helper listens on (UDP, and TCP for the fallback), one per session. Default 60000-61000, the range mosh uses, so a firewall open for mosh lets it through. Leave blank for any port."
                                } else {
                                    "Enter a port or a range like 60000-61000, between 1024 and 65535."
                                }
                            )
                        },
                        isError = !boostPortsValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SETTINGS_BOOST_PORTS_INPUT)
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

/** Terminal, Mosh, and snippet options most people never change (moved from Settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedTerminalSettings(
    terminalEmulation: TerminalEmulation,
    onTerminalEmulationChange: (TerminalEmulation) -> Unit,
    terminalSelectionMode: TerminalSelectionMode,
    onTerminalSelectionModeChange: (TerminalSelectionMode) -> Unit,
    terminalMarginPx: Int,
    onTerminalMarginPxChange: (Int) -> Unit,
    moshServerCommand: String,
    onMoshServerCommandChange: (String) -> Unit,
    snippetRunTimeoutSeconds: Int,
    onSnippetRunTimeoutSecondsChange: (Int) -> Unit
) {
    val terminalExpanded = remember { mutableStateOf(false) }
    val terminalOptions = listOf(TerminalEmulation.XTERM, TerminalEmulation.VT100)
    val selectionOptions = listOf(TerminalSelectionMode.NATURAL, TerminalSelectionMode.BLOCK)
    val snippetTimeoutState = rememberPersistedField(snippetRunTimeoutSeconds.toString())
    val terminalMarginState = rememberPersistedField(terminalMarginPx.toString())
    val moshServerCommandState = rememberPersistedField(moshServerCommand)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Terminal", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = terminalExpanded.value,
                onExpandedChange = { terminalExpanded.value = !terminalExpanded.value }
            ) {
                TextField(
                    value = terminalEmulation.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Emulation mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = terminalExpanded.value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .testTag(UiTestTags.SETTINGS_TERMINAL_EMULATION_FIELD)
                )
                ExposedDropdownMenu(
                    expanded = terminalExpanded.value,
                    onDismissRequest = { terminalExpanded.value = false }
                ) {
                    terminalOptions.forEach { option ->
                        DropdownMenuItem(
                            modifier = Modifier.testTag(
                                UiTestTags.settingsTerminalOption(option.label)
                            ),
                            text = { Text(option.label) },
                            onClick = {
                                terminalExpanded.value = false
                                onTerminalEmulationChange(option)
                            }
                        )
                    }
                }
            }
            Text(
                "xterm is the default and recommended mode.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = terminalMarginState.value,
                onValueChange = { next ->
                    val digits = next.filter { it.isDigit() }.take(3)
                    terminalMarginState.value = digits
                    if (digits.isEmpty()) {
                        terminalMarginState.saved("0")
                        onTerminalMarginPxChange(0)
                    } else {
                        val parsed = digits.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(0, 128)
                            terminalMarginState.value = clamped.toString()
                            terminalMarginState.saved(clamped.toString())
                            onTerminalMarginPxChange(clamped)
                        }
                    }
                },
                label = { Text("Terminal margin (px)") },
                supportingText = {
                    Text(
                        "Adds space around the terminal content for screen protectors. Use 0 to disable. 8 or 16 is a good starting point."
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_TERMINAL_MARGIN_INPUT)
            )
            OutlinedTextField(
                value = moshServerCommandState.value,
                onValueChange = { next ->
                    moshServerCommandState.value = next
                    moshServerCommandState.saved(next)
                    onMoshServerCommandChange(next)
                },
                label = { Text("Mosh server command") },
                supportingText = {
                    Text(
                        "Command executed on the remote host to start mosh-server. Leave blank to use the default: $DEFAULT_MOSH_SERVER_COMMAND"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_MOSH_SERVER_COMMAND_INPUT)
            )
            Text(
                "Selection mode",
                style = MaterialTheme.typography.titleSmall
            )
            selectionOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = terminalSelectionMode == option,
                        onClick = { onTerminalSelectionModeChange(option) }
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(option.label)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Snippets", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = snippetTimeoutState.value,
                    onValueChange = { next ->
                        val digits = next.filter { it.isDigit() }.take(2)
                        snippetTimeoutState.value = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(1, 60)
                            snippetTimeoutState.value = clamped.toString()
                            snippetTimeoutState.saved(clamped.toString())
                            onSnippetRunTimeoutSecondsChange(clamped)
                        }
                    },
                    label = { Text("Run timeout (seconds)") },
                    supportingText = {
                        Text("Used when running snippets on an open SSH session.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
