package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.ui.state.LockTimeout
import com.sshpeaches.app.ui.state.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    allowBackgroundSessions: Boolean,
    onBackgroundToggle: (Boolean) -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    lockTimeout: LockTimeout,
    onLockTimeoutChange: (LockTimeout) -> Unit,
    crashReportsEnabled: Boolean,
    onCrashReportsToggle: (Boolean) -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsToggle: (Boolean) -> Unit,
    diagnosticsLoggingEnabled: Boolean,
    onDiagnosticsToggle: (Boolean) -> Unit,
    includeIdentities: Boolean,
    onIncludeIdentitiesToggle: (Boolean) -> Unit,
    includeSettings: Boolean,
    onIncludeSettingsToggle: (Boolean) -> Unit,
    autoStartForwards: Boolean,
    onAutoStartForwardsToggle: (Boolean) -> Unit,
    hostKeyPromptEnabled: Boolean,
    onHostKeyPromptToggle: (Boolean) -> Unit,
    usageReportsEnabled: Boolean,
    onUsageReportsToggle: (Boolean) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val lockExpanded = remember { mutableStateOf(false) }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    val timeoutOptions = listOf(
        LockTimeout.IMMEDIATE,
        LockTimeout.ONE_MIN,
        LockTimeout.FIVE_MIN,
        LockTimeout.FIFTEEN_MIN,
        LockTimeout.CUSTOM
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(
                    expanded = expanded.value,
                    onExpandedChange = { expanded.value = !expanded.value }
                ) {
                    TextField(
                        value = themeOptions.first { it.first == currentTheme }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false }
                    ) {
                        themeOptions.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded.value = false
                                    onThemeChange(mode)
                                }
                            )
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Background Sessions", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Run shells in background")
                        Text(
                            "Keep SSH/Mosh sessions alive while app is backgrounded",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = allowBackgroundSessions, onCheckedChange = onBackgroundToggle)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Security", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Biometric lock")
                        Text("Require fingerprint/face after inactivity", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = biometricEnabled, onCheckedChange = onBiometricToggle)
                }
                ExposedDropdownMenuBox(
                    expanded = lockExpanded.value,
                    onExpandedChange = { lockExpanded.value = !lockExpanded.value }
                ) {
                    TextField(
                        value = lockTimeout.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lock timeout") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lockExpanded.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = lockExpanded.value,
                        onDismissRequest = { lockExpanded.value = false }
                    ) {
                        timeoutOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    lockExpanded.value = false
                                    onLockTimeoutChange(option)
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Host key prompts")
                        Text("Warn when host fingerprints change", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = hostKeyPromptEnabled, onCheckedChange = onHostKeyPromptToggle)
                }
            }
        }
        val showTransferDialog = remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transfer Data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export hosts, identities, and settings via QR code.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { showTransferDialog.value = true }) {
                    Text("Export via QR")
                }
            }
        }
        if (showTransferDialog.value) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showTransferDialog.value = false },
                title = { Text("Export data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Include identities")
                                Text("Attach keys when sharing/exporting", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = includeIdentities, onCheckedChange = onIncludeIdentitiesToggle)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Include settings")
                                Text("Share app preferences when exporting", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = includeSettings, onCheckedChange = onIncludeSettingsToggle)
                        }
                        Text("Next: generate a QR code that bundles your selections.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: trigger QR generation flow
                        showTransferDialog.value = false
                    }) { Text("Generate QR") }
                },
                dismissButton = {
                    TextButton(onClick = { showTransferDialog.value = false }) { Text("Cancel") }
                }
            )
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Port Forwards", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Auto-start associated forwards")
                        Text("Start linked tunnels when connecting", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoStartForwards, onCheckedChange = onAutoStartForwardsToggle)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diagnostics & Privacy", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Crash reports")
                        Text("Send anonymous crash details", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = crashReportsEnabled, onCheckedChange = onCrashReportsToggle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Usage analytics")
                        Text("Help improve SSHPeaches by sharing usage stats", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = analyticsEnabled, onCheckedChange = onAnalyticsToggle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Session diagnostics")
                        Text("Capture anonymized session logs", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = diagnosticsLoggingEnabled, onCheckedChange = onDiagnosticsToggle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Send usage reports (Advanced)")
                        Text("Periodically send diagnostics bundle", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = usageReportsEnabled, onCheckedChange = onUsageReportsToggle)
                }
            }
        }
    }
}
