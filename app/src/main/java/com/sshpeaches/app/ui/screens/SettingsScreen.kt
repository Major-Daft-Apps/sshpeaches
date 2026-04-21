package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.state.BackgroundSessionTimeout
import com.majordaftapps.sshpeaches.app.ui.state.LockTimeout
import com.majordaftapps.sshpeaches.app.ui.state.TerminalBellMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.calculatePasswordRevealIndex
import com.majordaftapps.sshpeaches.app.ui.util.updatePasswordStateWithReveal
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    shellLayoutMode: ShellLayoutMode = ShellLayoutMode.COMPACT,
    onThemeChange: (ThemeMode) -> Unit,
    currentAppIcon: AppIconOption,
    onAppIconChange: (AppIconOption) -> Unit,
    allowBackgroundSessions: Boolean,
    onBackgroundToggle: (Boolean) -> Unit,
    backgroundSessionTimeout: BackgroundSessionTimeout,
    onBackgroundSessionTimeoutChange: (BackgroundSessionTimeout) -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    lockTimeout: LockTimeout,
    onLockTimeoutChange: (LockTimeout) -> Unit,
    customLockTimeoutMinutes: Int,
    onCustomLockTimeoutMinutesChange: (Int) -> Unit,
    snippetRunTimeoutSeconds: Int,
    onSnippetRunTimeoutSecondsChange: (Int) -> Unit,
    terminalEmulation: TerminalEmulation,
    onTerminalEmulationChange: (TerminalEmulation) -> Unit,
    terminalSelectionMode: TerminalSelectionMode,
    onTerminalSelectionModeChange: (TerminalSelectionMode) -> Unit,
    terminalBellMode: TerminalBellMode,
    onTerminalBellModeChange: (TerminalBellMode) -> Unit,
    useVolumeButtonsToAdjustFontSize: Boolean,
    onUseVolumeButtonsToAdjustFontSizeChange: (Boolean) -> Unit,
    terminalMarginPx: Int,
    onTerminalMarginPxChange: (Int) -> Unit,
    moshServerCommand: String,
    onMoshServerCommandChange: (String) -> Unit,
    crashReportsEnabled: Boolean,
    onCrashReportsToggle: (Boolean) -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsToggle: (Boolean) -> Unit,
    diagnosticsLoggingEnabled: Boolean,
    onDiagnosticsToggle: (Boolean) -> Unit,
    includeSecretsInQr: Boolean,
    onIncludeSecretsInQrToggle: (Boolean) -> Unit,
    autoStartForwards: Boolean,
    onAutoStartForwardsToggle: (Boolean) -> Unit,
    hostKeyPromptEnabled: Boolean,
    onHostKeyPromptToggle: (Boolean) -> Unit,
    autoTrustHostKey: Boolean,
    onAutoTrustHostKeyToggle: (Boolean) -> Unit,
    usageReportsEnabled: Boolean,
    onUsageReportsToggle: (Boolean) -> Unit,
    onRestoreDefaultSettings: () -> Unit,
    pinConfigured: Boolean,
    isLocked: Boolean,
    biometricAvailable: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onGenerateExportPayload: (String?) -> String?,
    onTransferPayloadRequiresPassphrase: (String) -> Boolean = { false },
    onImportFromQrPayload: (String, String?) -> String = { _, _ -> "Invalid export QR." },
    onShowMessage: (String) -> Unit = {},
    corePermissions: List<CorePermissionStatus> = emptyList(),
    onManagePermissions: () -> Unit = {}
) {
    val expanded = remember { mutableStateOf(false) }
    val lockExpanded = remember { mutableStateOf(false) }
    val backgroundTimeoutExpanded = remember { mutableStateOf(false) }
    val terminalExpanded = remember { mutableStateOf(false) }
    val bellExpanded = remember { mutableStateOf(false) }
    val showTransferDialog = rememberSaveable { mutableStateOf(false) }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to "Automatic",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    val appIconOptions = listOf(
        AppIconChoice(
            option = AppIconOption.DEFAULT,
            title = "Default",
            description = "Current SSHPeaches icon.",
            backgroundColorResId = R.color.ic_launcher_background,
            tintColorResId = null,
            previewAssetResId = R.drawable.sshpeaches,
            previewPaddingDp = 2
        ),
        AppIconChoice(
            option = AppIconOption.PEACH_LIGHT,
            title = "Orange Peach",
            description = "Orange peach on the light-mode navbar background.",
            backgroundColorResId = R.color.color_vanilla,
            tintColorResId = R.color.peachy_orange,
            previewAssetResId = R.drawable.sshpeaches_activitybar,
            previewPaddingDp = 15
        ),
        AppIconChoice(
            option = AppIconOption.PEACH_DARK,
            title = "White Peach",
            description = "White peach on the dark-mode background.",
            backgroundColorResId = R.color.color_hard_black,
            tintColorResId = android.R.color.white,
            previewAssetResId = R.drawable.sshpeaches_activitybar,
            previewPaddingDp = 15
        )
    )
    val timeoutOptions = listOf(
        LockTimeout.IMMEDIATE,
        LockTimeout.ONE_MIN,
        LockTimeout.FIVE_MIN,
        LockTimeout.FIFTEEN_MIN,
        LockTimeout.CUSTOM
    )
    val backgroundTimeoutOptions = listOf(
        BackgroundSessionTimeout.ONE_MIN,
        BackgroundSessionTimeout.FIVE_MIN,
        BackgroundSessionTimeout.TEN_MIN,
        BackgroundSessionTimeout.THIRTY_MIN,
        BackgroundSessionTimeout.ONE_HOUR,
        BackgroundSessionTimeout.FOREVER
    )
    val terminalOptions = listOf(TerminalEmulation.XTERM, TerminalEmulation.VT100)
    val bellOptions = listOf(
        TerminalBellMode.DISABLED,
        TerminalBellMode.VIBRATE_DEVICE,
        TerminalBellMode.SHOW_NOTIFICATION
    )
    val selectionOptions = listOf(TerminalSelectionMode.NATURAL, TerminalSelectionMode.BLOCK)
    val showPinDialog = remember { mutableStateOf(false) }
    val pinEntry = remember { mutableStateOf("") }
    val pinRevealIndex = remember { mutableIntStateOf(-1) }
    val confirmPinEntry = remember { mutableStateOf("") }
    val confirmPinRevealIndex = remember { mutableIntStateOf(-1) }
    val showDisablePinDialog = remember { mutableStateOf(false) }
    val showRestoreDefaultsDialog = remember { mutableStateOf(false) }
    val customMinutesState = remember(customLockTimeoutMinutes) { mutableStateOf(customLockTimeoutMinutes.toString()) }
    val snippetTimeoutState = remember(snippetRunTimeoutSeconds) { mutableStateOf(snippetRunTimeoutSeconds.toString()) }
    val terminalMarginState = remember(terminalMarginPx) { mutableStateOf(terminalMarginPx.toString()) }
    val moshServerCommandState = remember(moshServerCommand) { mutableStateOf(moshServerCommand) }
    val exportQrBitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val exportPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
    val exportPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val exportConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
    val exportConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val exportPassphraseError = rememberSaveable { mutableStateOf<String?>(null) }
    val pendingImportPayload = remember { mutableStateOf<String?>(null) }
    val importPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
    val importPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val importPassphraseError = rememberSaveable { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        val contents = result.contents.orEmpty()
        if (contents.isBlank()) {
            onShowMessage("QR scan cancelled.")
        } else {
            if (onTransferPayloadRequiresPassphrase(contents)) {
                pendingImportPayload.value = contents
                importPassphraseState.value = ExportPassphraseCache.transfer.orEmpty()
                importPassphraseError.value = null
            } else {
                onShowMessage(onImportFromQrPayload(contents, null))
            }
        }
    }
    AutoHidePasswordReveal(pinRevealIndex)
    AutoHidePasswordReveal(confirmPinRevealIndex)
    AutoHidePasswordReveal(exportPassphraseRevealIndex)
    AutoHidePasswordReveal(exportConfirmPassphraseRevealIndex)
    AutoHidePasswordReveal(importPassphraseRevealIndex)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_SETTINGS)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (shellLayoutMode == ShellLayoutMode.WIDE) 1280.dp else 980.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .testTag(UiTestTags.SETTINGS_SCROLL_CONTAINER)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                    corePermissions.forEach { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(permission.title)
                                Text(
                                    permission.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!permission.granted) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Missing permission",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Button(
                        onClick = onManagePermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage permissions")
                    }
                }
            }
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
                                .testTag(UiTestTags.SETTINGS_THEME_MODE_FIELD)
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
                                    },
                                    modifier = Modifier.testTag(UiTestTags.settingsThemeOption(label))
                                )
                            }
                        }
                    }
                    Text("App Icon", style = MaterialTheme.typography.titleSmall)
                    appIconOptions.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppIconChange(choice.option) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconPreview(choice)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp, end = 12.dp)
                            ) {
                                Text(choice.title)
                                Text(
                                    choice.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            RadioButton(
                                selected = currentAppIcon == choice.option,
                                onClick = { onAppIconChange(choice.option) },
                                modifier = Modifier.testTag(UiTestTags.settingsAppIconOption(choice.title))
                            )
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
                    Switch(
                        checked = allowBackgroundSessions,
                        onCheckedChange = onBackgroundToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BACKGROUND_SWITCH)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = backgroundTimeoutExpanded.value,
                    onExpandedChange = {
                        if (allowBackgroundSessions) {
                            backgroundTimeoutExpanded.value = !backgroundTimeoutExpanded.value
                        }
                    }
                ) {
                    TextField(
                        value = backgroundSessionTimeout.label,
                        onValueChange = {},
                        readOnly = true,
                        enabled = allowBackgroundSessions,
                        label = { Text("Background connection timeout") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = backgroundTimeoutExpanded.value)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = backgroundTimeoutExpanded.value,
                        onDismissRequest = { backgroundTimeoutExpanded.value = false }
                    ) {
                        backgroundTimeoutOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    backgroundTimeoutExpanded.value = false
                                    onBackgroundSessionTimeoutChange(option)
                                }
                            )
                        }
                    }
                }
                Text(
                    "When app is backgrounded, sessions are stopped after this timeout.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
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
                            onTerminalMarginPxChange(0)
                        } else {
                            val parsed = digits.toIntOrNull()
                            if (parsed != null) {
                                val clamped = parsed.coerceIn(0, 128)
                                terminalMarginState.value = clamped.toString()
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
                ExposedDropdownMenuBox(
                    expanded = bellExpanded.value,
                    onExpandedChange = { bellExpanded.value = !bellExpanded.value }
                ) {
                    TextField(
                        value = terminalBellMode.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Bell") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bellExpanded.value)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag(UiTestTags.SETTINGS_TERMINAL_BELL_FIELD)
                    )
                    ExposedDropdownMenu(
                        expanded = bellExpanded.value,
                        onDismissRequest = { bellExpanded.value = false }
                    ) {
                        bellOptions.forEach { option ->
                            DropdownMenuItem(
                                modifier = Modifier.testTag(
                                    UiTestTags.settingsTerminalBellOption(option.label)
                                ),
                                text = { Text(option.label) },
                                onClick = {
                                    bellExpanded.value = false
                                    onTerminalBellModeChange(option)
                                }
                            )
                        }
                    }
                }
                Text(
                    terminalBellMode.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Use volume buttons to adjust font size")
                        Text(
                            "When enabled, volume up/down changes terminal font size instead of device volume while a terminal session is focused.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useVolumeButtonsToAdjustFontSize,
                        onCheckedChange = onUseVolumeButtonsToAdjustFontSizeChange,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_TERMINAL_VOLUME_BUTTONS_SWITCH)
                    )
                }
                OutlinedTextField(
                    value = moshServerCommandState.value,
                    onValueChange = { next ->
                        moshServerCommandState.value = next
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
                        enabled = biometricAvailable && pinConfigured,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BIOMETRIC_SWITCH)
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
                    Switch(
                        checked = hostKeyPromptEnabled,
                        onCheckedChange = onHostKeyPromptToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_HOST_KEY_PROMPT_SWITCH)
                    )
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
                    Switch(
                        checked = autoTrustHostKey,
                        onCheckedChange = onAutoTrustHostKeyToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_AUTO_TRUST_HOST_KEY_SWITCH)
                    )
                }
                Text(
                    if (pinConfigured) "PIN lock configured."
                    else "PIN lock not configured.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_STATUS_TEXT)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showPinDialog.value = true },
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_SET_PIN_BUTTON)
                    ) {
                        Text(if (pinConfigured) "Change PIN" else "Set PIN")
                    }
                    if (pinConfigured) {
                        Button(
                            onClick = { showDisablePinDialog.value = true },
                            enabled = !isLocked,
                            modifier = Modifier.testTag(UiTestTags.SETTINGS_DISABLE_PIN_BUTTON)
                        ) { Text("Disable PIN") }
                    }
                }
                if (pinConfigured && isLocked) {
                    Text(
                        "Unlock before disabling PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                SettingsToggleRow(
                    title = "Crash reports",
                    description = "Send anonymous crash details",
                    checked = crashReportsEnabled,
                    onCheckedChange = onCrashReportsToggle
                )
                SettingsToggleRow(
                    title = "Usage analytics",
                    description = "Help improve SSHPeaches by sharing usage stats",
                    checked = analyticsEnabled,
                    onCheckedChange = onAnalyticsToggle
                )
                SettingsToggleRow(
                    title = "Session diagnostics",
                    description = "Capture local session logs for troubleshooting. Not uploaded.",
                    checked = diagnosticsLoggingEnabled,
                    onCheckedChange = onDiagnosticsToggle,
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_DIAGNOSTICS_SWITCH)
                )
                SettingsToggleRow(
                    title = "Send usage reports",
                    description = "Upload a usage report every 7 days",
                    checked = usageReportsEnabled,
                    onCheckedChange = onUsageReportsToggle
                )
            }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Restore Defaults", style = MaterialTheme.typography.titleMedium)
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
                    Text("Restore Default Settings")
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transfer Data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export hosts, identities, favorites, port forwards, snippets, terminal themes, custom keys, and app settings via QR code.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { showTransferDialog.value = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SETTINGS_EXPORT_QR_BUTTON)
                ) {
                    Text("Export via QR")
                }
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan SSHPeaches export QR")
                            setBeepEnabled(false)
                            setCaptureActivity(com.majordaftapps.sshpeaches.app.ui.qr.PortraitCaptureActivity::class.java)
                            setOrientationLocked(true)
                        }
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("Import via QR")
                }
            }
        }
    }
    }
    if (showTransferDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTransferDialog.value = false },
            modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_DIALOG),
            title = { Text("Export data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hosts, identities, favorites, port forwards, snippets, terminal themes, custom keys, and app settings are always included in transfer exports.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    SettingsToggleRow(
                        title = "Include passwords and private keys",
                        description = "Encrypt saved passwords and private key material with a passphrase.",
                        checked = includeSecretsInQr,
                        onCheckedChange = onIncludeSecretsInQrToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_INCLUDE_SECRETS_SWITCH)
                    )
                    if (includeSecretsInQr) {
                        OutlinedTextField(
                            value = exportPassphraseState.value,
                            onValueChange = {
                                updatePasswordStateWithReveal(
                                    exportPassphraseState,
                                    exportPassphraseRevealIndex,
                                    it
                                )
                                exportPassphraseError.value = null
                            },
                            label = { Text("Export passphrase") },
                            singleLine = true,
                            visualTransformation = TailRevealPasswordVisualTransformation(
                                exportPassphraseRevealIndex.intValue
                            ),
                            keyboardOptions = KeyboardOptions(
                                autoCorrect = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_PASSPHRASE_INPUT)
                        )
                        OutlinedTextField(
                            value = exportConfirmPassphraseState.value,
                            onValueChange = {
                                updatePasswordStateWithReveal(
                                    exportConfirmPassphraseState,
                                    exportConfirmPassphraseRevealIndex,
                                    it
                                )
                                exportPassphraseError.value = null
                            },
                            label = { Text("Confirm passphrase") },
                            singleLine = true,
                            visualTransformation = TailRevealPasswordVisualTransformation(
                                exportConfirmPassphraseRevealIndex.intValue
                            ),
                            keyboardOptions = KeyboardOptions(
                                autoCorrect = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_CONFIRM_PASSPHRASE_INPUT)
                        )
                        Text(
                            "Use this same passphrase when importing on another device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    exportPassphraseError.value?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_ERROR)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val passphrase = if (includeSecretsInQr) exportPassphraseState.value else null
                        when {
                            includeSecretsInQr &&
                                passphrase.orEmpty().length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH -> {
                                exportPassphraseError.value =
                                    "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                                return@TextButton
                            }
                            includeSecretsInQr && passphrase != exportConfirmPassphraseState.value -> {
                                exportPassphraseError.value = "Passphrases do not match."
                                return@TextButton
                            }
                        }
                        val payload = onGenerateExportPayload(passphrase)
                        if (payload == null) {
                            exportPassphraseError.value = if (includeSecretsInQr) {
                                "Unable to export protected data. Unlock the app and try again."
                            } else {
                                "Unable to generate export QR."
                            }
                            return@TextButton
                        }
                        exportQrBitmap.value = runCatching {
                            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640)
                            val bmp = createBitmap(
                                matrix.width,
                                matrix.height,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            for (x in 0 until matrix.width) {
                                for (y in 0 until matrix.height) {
                                    bmp[x, y] =
                                        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                }
                            }
                            bmp
                        }.getOrNull()
                        if (exportQrBitmap.value == null) {
                            onShowMessage("Unable to generate export QR.")
                        } else if (includeSecretsInQr) {
                            ExportPassphraseCache.transfer = passphrase
                        }
                        exportPassphraseState.value = ""
                        exportConfirmPassphraseState.value = ""
                        exportPassphraseRevealIndex.intValue = -1
                        exportConfirmPassphraseRevealIndex.intValue = -1
                        exportPassphraseError.value = null
                        showTransferDialog.value = false
                    },
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON)
                ) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog.value = false }) { Text("Cancel") }
            }
        )
    }
    exportQrBitmap.value?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { exportQrBitmap.value = null },
            modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_QR_DIALOG),
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
    pendingImportPayload.value?.let { payload ->
        AlertDialog(
            onDismissRequest = {
                pendingImportPayload.value = null
                importPassphraseState.value = ExportPassphraseCache.transfer.orEmpty()
                importPassphraseError.value = null
            },
            title = { Text("Decrypt imported secrets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This export includes encrypted passwords or private keys.")
                    OutlinedTextField(
                        value = importPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(
                                importPassphraseState,
                                importPassphraseRevealIndex,
                                it
                            )
                            importPassphraseError.value = null
                        },
                        label = { Text("Import passphrase") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(
                            importPassphraseRevealIndex.intValue
                        ),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    importPassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val passphrase = importPassphraseState.value
                    if (passphrase.isBlank()) {
                        importPassphraseError.value = "Enter the export passphrase."
                        return@TextButton
                    }
                    if (passphrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH) {
                        importPassphraseError.value =
                            "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        return@TextButton
                    }
                    ExportPassphraseCache.transfer = passphrase
                    onShowMessage(onImportFromQrPayload(payload, passphrase))
                    pendingImportPayload.value = null
                    importPassphraseError.value = null
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImportPayload.value = null
                    importPassphraseState.value = ExportPassphraseCache.transfer.orEmpty()
                    importPassphraseError.value = null
                }) { Text("Cancel") }
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
            modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_DIALOG),
            title = { Text(if (pinConfigured) "Change PIN" else "Set PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pinEntry.value,
                        onValueChange = {
                            val previous = pinEntry.value
                            val next = it.filter { ch -> ch.isDigit() }
                            pinEntry.value = next
                            pinRevealIndex.intValue = calculatePasswordRevealIndex(previous, next)
                        },
                        label = { Text("Enter PIN") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(pinRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_INPUT)
                    )
                    OutlinedTextField(
                        value = confirmPinEntry.value,
                        onValueChange = {
                            val previous = confirmPinEntry.value
                            val next = it.filter { ch -> ch.isDigit() }
                            confirmPinEntry.value = next
                            confirmPinRevealIndex.intValue = calculatePasswordRevealIndex(previous, next)
                        },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(confirmPinRevealIndex.intValue),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_CONFIRM_INPUT)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinEntry.value.length < 4 || pinEntry.value != confirmPinEntry.value) return@TextButton
                        onSetPin(pinEntry.value)
                        pinEntry.value = ""
                        confirmPinEntry.value = ""
                        showPinDialog.value = false
                    },
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_SAVE_BUTTON)
                ) { Text("Save") }
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

    if (showDisablePinDialog.value) {
        AlertDialog(
            onDismissRequest = { showDisablePinDialog.value = false },
            title = { Text("Disable PIN lock?") },
            text = {
                Text("This removes the PIN requirement. Biometric lock will also be disabled.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearPin()
                        showDisablePinDialog.value = false
                    },
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_DISABLE_PIN_CONFIRM)
                ) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePinDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreDefaultsDialog.value) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultsDialog.value = false },
            title = { Text("Restore default settings?") },
            text = {
                Text(
                    "This resets app settings (theme, terminal, lock timeout, host key preferences, diagnostics, and keyboard layout) to defaults."
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

private data class AppIconChoice(
    val option: AppIconOption,
    val title: String,
    val description: String,
    val backgroundColorResId: Int,
    val tintColorResId: Int?,
    val previewAssetResId: Int,
    val previewPaddingDp: Int
)

@Composable
private fun AppIconPreview(choice: AppIconChoice) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorResource(choice.backgroundColorResId)),
        modifier = Modifier.size(84.dp)
    ) {
        Image(
            painter = painterResource(id = choice.previewAssetResId),
            contentDescription = null,
            colorFilter = choice.tintColorResId?.let { ColorFilter.tint(colorResource(it)) },
            modifier = Modifier
                .size(84.dp)
                .padding(choice.previewPaddingDp.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
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
