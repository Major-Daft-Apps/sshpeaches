package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
    customLockTimeoutMinutes: Int,
    onCustomLockTimeoutMinutesChange: (Int) -> Unit,
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
    autoTrustHostKey: Boolean,
    onAutoTrustHostKeyToggle: (Boolean) -> Unit,
    usageReportsEnabled: Boolean,
    onUsageReportsToggle: (Boolean) -> Unit,
    pinConfigured: Boolean,
    isLocked: Boolean,
    biometricAvailable: Boolean,
    onSetPin: (String) -> Unit,
    onLockApp: () -> Unit,
    onUnlockWithPin: (String) -> Boolean,
    onGenerateExportPayload: () -> String,
    onShowMessage: (String) -> Unit = {}
) {
    val expanded = remember { mutableStateOf(false) }
    val lockExpanded = remember { mutableStateOf(false) }
    val showTransferDialog = remember { mutableStateOf(false) }
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
    val showPinDialog = remember { mutableStateOf(false) }
    val pinEntry = remember { mutableStateOf("") }
    val confirmPinEntry = remember { mutableStateOf("") }
    val showUnlockDialog = remember { mutableStateOf(false) }
    val unlockEntry = remember { mutableStateOf("") }
    val unlockError = remember { mutableStateOf<String?>(null) }
    val customMinutesState = remember(customLockTimeoutMinutes) { mutableStateOf(customLockTimeoutMinutes.toString()) }
    val exportQrBitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
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
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = onBiometricToggle,
                        enabled = biometricAvailable && pinConfigured
                    )
                }
                if (!biometricAvailable) {
                    Text(
                        "Biometric hardware not available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!pinConfigured) {
                    Text(
                        "Set a PIN to enable biometric unlock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                if (lockTimeout == LockTimeout.CUSTOM) {
                    OutlinedTextField(
                        value = customMinutesState.value,
                        onValueChange = { next ->
                            val digits = next.filter { it.isDigit() }.take(3)
                            customMinutesState.value = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null) {
                                onCustomLockTimeoutMinutesChange(parsed.coerceIn(1, 720))
                            }
                        },
                        label = { Text("Custom timeout (minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text("Automatically trust host key")
                        Text(
                            "If disabled, you will be prompted before trusting unknown host keys.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = autoTrustHostKey, onCheckedChange = onAutoTrustHostKeyToggle)
                }
                Text(
                    if (pinConfigured) "PIN lock configured. Status: ${if (isLocked) "Locked" else "Unlocked"}"
                    else "PIN lock not configured.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showPinDialog.value = true }) {
                        Text(if (pinConfigured) "Change PIN" else "Set PIN")
                    }
                    if (pinConfigured) {
                        Button(
                            onClick = onLockApp,
                            enabled = !isLocked
                        ) { Text("Lock now") }
                        Button(
                            onClick = { showUnlockDialog.value = true },
                            enabled = isLocked
                        ) { Text("Unlock (PIN)") }
                    }
                }
            }
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
                    val payload = onGenerateExportPayload()
                    exportQrBitmap.value = runCatching {
                        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640)
                        val bmp = android.graphics.Bitmap.createBitmap(
                            matrix.width,
                            matrix.height,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        for (x in 0 until matrix.width) {
                            for (y in 0 until matrix.height) {
                                bmp.setPixel(
                                    x,
                                    y,
                                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                )
                            }
                        }
                        bmp
                    }.getOrNull()
                    if (exportQrBitmap.value == null) {
                        onShowMessage("Unable to generate export QR.")
                    }
                    showTransferDialog.value = false
                }) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog.value = false }) { Text("Cancel") }
            }
        )
    }
    exportQrBitmap.value?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { exportQrBitmap.value = null },
            title = { Text("Export QR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Export QR",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Scan this QR on another device to import.")
                }
            },
            confirmButton = {
                TextButton(onClick = { exportQrBitmap.value = null }) { Text("Close") }
            }
        )
    }
    if (showPinDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog.value = false
                pinEntry.value = ""
                confirmPinEntry.value = ""
            },
            title = { Text(if (pinConfigured) "Change PIN" else "Set PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pinEntry.value,
                        onValueChange = { pinEntry.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Enter PIN") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPinEntry.value,
                        onValueChange = { confirmPinEntry.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Confirm PIN") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinEntry.value.length < 4 || pinEntry.value != confirmPinEntry.value) return@TextButton
                    onSetPin(pinEntry.value)
                    pinEntry.value = ""
                    confirmPinEntry.value = ""
                    showPinDialog.value = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog.value = false
                    pinEntry.value = ""
                    confirmPinEntry.value = ""
                }) { Text("Cancel") }
            }
        )
    }

    if (showUnlockDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showUnlockDialog.value = false
                unlockEntry.value = ""
                unlockError.value = null
            },
            title = { Text("Unlock with PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unlockEntry.value,
                        onValueChange = { unlockEntry.value = it.filter { ch -> ch.isDigit() } },
                        label = { Text("PIN") },
                        singleLine = true
                    )
                    unlockError.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = onUnlockWithPin(unlockEntry.value)
                    if (ok) {
                        unlockEntry.value = ""
                        unlockError.value = null
                        showUnlockDialog.value = false
                    } else {
                        unlockError.value = "Incorrect PIN"
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlockDialog.value = false
                    unlockEntry.value = ""
                    unlockError.value = null
                }) { Text("Cancel") }
            }
        )
    }
}
