package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import com.majordaftapps.sshpeaches.app.transfer.LanTransferPeer
import com.majordaftapps.sshpeaches.app.transfer.LanTransferBrowser
import com.majordaftapps.sshpeaches.app.transfer.LanTransferAdvertiser
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.transfer.LanTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import com.majordaftapps.sshpeaches.app.ui.qr.buildQrScanOptions
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

private enum class SettingsCategory(
    val title: String,
    val description: String
) {
    PERMISSIONS("Permissions", "App access and required OS permissions"),
    APPEARANCE("Appearance", "Theme and launcher icon"),
    BACKGROUND("Background", "Session lifetime while backgrounded"),
    TERMINAL("Terminal", "Emulation, custom keyboard, margins, bell, and Mosh"),
    SECURITY("Security", "PIN, biometrics, and host key policy"),
    AUTOMATION("Automation", "Snippets and port forward defaults"),
    DIAGNOSTICS("Diagnostics", "Crash, usage, and session diagnostics"),
    TRANSFER("Transfer", "Export and import via QR or file"),
    ADVANCED("Advanced", "SFTP transfer tuning and other expert options")
}

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
    useBuiltInKeyboard: Boolean,
    onUseBuiltInKeyboardToggle: (Boolean) -> Unit,
    confirmPasswordInsert: Boolean,
    onConfirmPasswordInsertToggle: (Boolean) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectToggle: (Boolean) -> Unit,
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
    onOpenAdvancedSettings: () -> Unit = {},
    pinConfigured: Boolean,
    isLocked: Boolean,
    biometricAvailable: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onGenerateExportPayload: (String?) -> String?,
    onTransferPayloadRequiresPassphrase: (String) -> Boolean = { false },
    onImportFromQrPayload: (String, String?) -> String = { _, _ -> "Invalid export payload." },
    onShowMessage: (String) -> Unit = {},
    corePermissions: List<CorePermissionStatus> = emptyList(),
    onManagePermissions: () -> Unit = {},
    incomingImportUri: String? = null,
    onIncomingImportHandled: () -> Unit = {}
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
    val pinDialogError = remember { mutableStateOf<String?>(null) }
    val confirmPinRevealIndex = remember { mutableIntStateOf(-1) }
    val showDisablePinDialog = remember { mutableStateOf(false) }
    val customMinutesState = rememberPersistedField(customLockTimeoutMinutes.toString())
    val snippetTimeoutState = rememberPersistedField(snippetRunTimeoutSeconds.toString())
    val terminalMarginState = rememberPersistedField(terminalMarginPx.toString())
    val moshServerCommandState = rememberPersistedField(moshServerCommand)
    val scope = rememberCoroutineScope()
    val transferWorking = remember { mutableStateOf(false) }

    // Import runs PBKDF2 per protected item (about 1-2 s each), so keep it off the main thread.
    fun runImport(contents: String, passphrase: String?) {
        transferWorking.value = true
        scope.launch {
                val message = withContext(Dispatchers.Default) {
                    runCatching { onImportFromQrPayload(contents, passphrase) }
                        .getOrElse {
                            TelemetryInitializer.recordNonFatal("import", it)
                            "Import failed: ${it.message ?: it.javaClass.simpleName}"
                        }
                }
                transferWorking.value = false
                onShowMessage(message)
            }
        }
        val context = LocalContext.current
        val exportQrBitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        val exportToFile = rememberSaveable { mutableStateOf(false) }
        val exportToWifi = rememberSaveable { mutableStateOf(false) }
        val exportToShare = rememberSaveable { mutableStateOf(false) }
        val incomingImport = remember { mutableStateOf<Pair<String, String>?>(null) } // name to contents
        val transferChooser = rememberSaveable { mutableStateOf<String?>(null) } // "export" / "import"
        val showWifiReceive = rememberSaveable { mutableStateOf(false) }
        val wifiReceiving = remember { mutableStateOf(false) }
        val wifiReceiveError = remember { mutableStateOf<String?>(null) }
        val wifiPeers = remember { mutableStateOf<List<LanTransferPeer>>(emptyList()) }
        val wifiSend = remember { mutableStateOf<WifiSendState?>(null) }
        val wifiSender = remember { mutableStateOf<LanTransfer.Sender?>(null) }
        val pendingExportPayload = remember { mutableStateOf<String?>(null) }
        val exportPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
        val exportPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
        val exportConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
        val exportConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
        val exportPassphraseError = rememberSaveable { mutableStateOf<String?>(null) }
        val pendingImportPayload = remember { mutableStateOf<String?>(null) }
        val importPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.transfer.orEmpty()) }
        val importPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
        val importPassphraseError = rememberSaveable { mutableStateOf<String?>(null) }
        val advertiser = remember { LanTransferAdvertiser(context) }

        fun handleReceivedPayload(contents: String) {
            if (onTransferPayloadRequiresPassphrase(contents)) {
                pendingImportPayload.value = contents
                importPassphraseState.value = ExportPassphraseCache.transfer.orEmpty()
                importPassphraseError.value = null
            } else {
                runImport(contents, null)
            }
        }

        fun shareExport(payload: String) {
            scope.launch {
                val uri = withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                        directory.listFiles()?.forEach { it.delete() }
                        val file = java.io.File(directory, "sshpeaches-export.json")
                        file.writeText(payload)
                        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
                    }.getOrNull()
                }
                if (uri == null) {
                    onShowMessage("Couldn't prepare the export file.")
                    return@launch
                }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri("sshpeaches-export.json", uri)
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(send, "Share SSHPeaches export"))
                }.onFailure { onShowMessage("No app can share files on this device.") }
            }
        }

        fun receiveOverWifi(host: String, port: Int, code: String) {
            if (wifiReceiving.value) return
            wifiReceiving.value = true
            wifiReceiveError.value = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { LanTransfer.receive(host, port, code) } }
                wifiReceiving.value = false
                result.onSuccess { contents ->
                    showWifiReceive.value = false
                    handleReceivedPayload(contents)
                }.onFailure { error ->
                    wifiReceiveError.value = when (error) {
                        is LanTransfer.WrongCodeException -> error.message
                        is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.NoRouteToHostException ->
                            "Couldn't reach that phone. Check both are on the same Wi-Fi and it still shows the code."
                        else -> {
                            TelemetryInitializer.recordNonFatal("wifi_receive", error)
                            "Wi-Fi transfer failed: ${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                }
            }
        }

        fun sendOverWifi(payload: String) {
            scope.launch {
                val address = withContext(Dispatchers.IO) { LanTransfer.localNetworkAddress() }
                if (address == null) {
                    onShowMessage("Connect this phone to Wi-Fi to send over the local network.")
                    return@launch
                }
                val sender = withContext(Dispatchers.Default) {
                    runCatching { LanTransfer.Sender(payload, address) }.getOrNull()
                }
                if (sender == null) {
                    onShowMessage("Couldn't start the Wi-Fi transfer.")
                    return@launch
                }
                val qr = withContext(Dispatchers.Default) {
                    renderQrBitmap(LanTransfer.pairingUri(sender.host, sender.port, sender.code))
                }
                wifiSender.value = sender
                wifiSend.value = WifiSendState(sender.host, sender.port, sender.code, qr)
                advertiser.start(sender.port)
                try {
                    val peer = withContext(Dispatchers.IO) { sender.awaitTransfer(WIFI_TRANSFER_TIMEOUT_MS) }
                    onShowMessage("Sent to ${peer.hostAddress}.")
                } catch (_: java.net.SocketTimeoutException) {
                    onShowMessage("No phone connected within 5 minutes.")
                } catch (error: Exception) {
                    if (wifiSender.value != null) {
                        TelemetryInitializer.recordNonFatal("wifi_send", error)
                        onShowMessage("Wi-Fi transfer failed: ${error.message ?: error.javaClass.simpleName}")
                    }
                } finally {
                    advertiser.stop()
                    sender.close()
                    wifiSender.value = null
                    wifiSend.value = null
                }
            }
        }

        LaunchedEffect(incomingImportUri, isLocked) {
            val uriText = incomingImportUri ?: return@LaunchedEffect
            if (isLocked) return@LaunchedEffect
            val uri = android.net.Uri.parse(uriText)
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        ?: uri.lastPathSegment
                        ?: "export file"
                    val contents = context.contentResolver.openInputStream(uri)?.use { input ->
                        // Exports are small; refuse anything absurd rather than read it into memory.
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            buffer.write(chunk, 0, read)
                            require(buffer.size() <= MAX_IMPORT_FILE_BYTES) { "too large" }
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }.orEmpty()
                    name to contents
                }.getOrNull()
            }
            onIncomingImportHandled()
            if (read == null || read.second.isBlank()) {
                onShowMessage("Couldn't read that file.")
            } else {
                incomingImport.value = read
            }
        }

        val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
            val contents = result.contents.orEmpty()
            val pairing = LanTransfer.parsePairingUri(contents)
            if (pairing != null) {
                showWifiReceive.value = true
                receiveOverWifi(pairing.host, pairing.port, pairing.code)
            } else if (contents.isBlank()) {
                onShowMessage("QR scan cancelled.")
            } else {
                handleReceivedPayload(contents)
            }
        }
        val createExportFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val payload = pendingExportPayload.value
            pendingExportPayload.value = null
            if (uri == null || payload == null) {
                onShowMessage("File export cancelled.")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val wrote = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(payload.toByteArray(Charsets.UTF_8))
                            output.flush()
                        } ?: error("missing output stream")
                    }.isSuccess
                }
                onShowMessage(if (wrote) "Exported connections to file." else "Unable to write export file.")
            }
        }
        val openImportFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                onShowMessage("File import cancelled.")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                // Document providers can be slow (cloud storage), so read off the main thread.
                val contents = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().toString(Charsets.UTF_8)
                        }
                    }.getOrNull().orEmpty()
                }
                if (contents.isBlank()) {
                    onShowMessage("Unable to read export file.")
                } else if (onTransferPayloadRequiresPassphrase(contents)) {
                    pendingImportPayload.value = contents
                    importPassphraseState.value = ExportPassphraseCache.transfer.orEmpty()
                    importPassphraseError.value = null
                } else {
                    runImport(contents, null)
                }
            }
        }
        AutoHidePasswordReveal(pinRevealIndex)
        AutoHidePasswordReveal(confirmPinRevealIndex)
        AutoHidePasswordReveal(exportPassphraseRevealIndex)
        AutoHidePasswordReveal(exportConfirmPassphraseRevealIndex)
        AutoHidePasswordReveal(importPassphraseRevealIndex)
        val selectedCategory = rememberSaveable { mutableStateOf(SettingsCategory.PERMISSIONS) }

        @Composable
        fun SettingsSections(
            visibleCategories: Set<SettingsCategory>,
            modifier: Modifier = Modifier
        ) {
            Column(
                modifier = modifier
                    .verticalScroll(rememberScrollState())
                    .testTag(UiTestTags.SETTINGS_SCROLL_CONTAINER)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (SettingsCategory.PERMISSIONS in visibleCategories) {
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
                }
                if (SettingsCategory.APPEARANCE in visibleCategories) {
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
                }
                if (SettingsCategory.BACKGROUND in visibleCategories) {
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
                    SettingsToggleRow(
                        title = "Reconnect automatically",
                        description = "When the network drops or changes (for example Wi-Fi to mobile data), SSH terminals reconnect instead of closing. Turn on Attach to tmux on a host to also get your shell back.",
                        checked = autoReconnect,
                        onCheckedChange = onAutoReconnectToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_AUTO_RECONNECT_SWITCH)
                    )
                }
            }
                }
                if (SettingsCategory.TERMINAL in visibleCategories) {
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
                    SettingsToggleRow(
                        title = "Use built-in keyboard",
                        description = "Show SSHPeaches extra keys (Esc, Ctrl, Fn, arrows) above the system keyboard, which opens automatically in terminal sessions. Tap the keyboard key to hide or show it.",
                        checked = useBuiltInKeyboard,
                        onCheckedChange = onUseBuiltInKeyboardToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BUILTIN_KEYBOARD_SWITCH)
                    )
                    SettingsToggleRow(
                        title = "Confirm before inserting password",
                        description = "Ask before Insert password types the saved password into the terminal.",
                        checked = confirmPasswordInsert,
                        onCheckedChange = onConfirmPasswordInsertToggle,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_CONFIRM_PASSWORD_INSERT_SWITCH)
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
                }
                if (SettingsCategory.SECURITY in visibleCategories) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Security", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Biometric lock")
                            Text("Unlock with fingerprint/face instead of the PIN", style = MaterialTheme.typography.bodySmall)
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
                            supportingText = { Text("Locks after SSHPeaches has been in the background this long.") },
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
                                    val clamped = parsed.coerceIn(1, 720)
                                    customMinutesState.saved(clamped.toString())
                                    onCustomLockTimeoutMinutesChange(clamped)
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
                }
                if (SettingsCategory.AUTOMATION in visibleCategories) {
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
                }
                if (SettingsCategory.DIAGNOSTICS in visibleCategories) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Diagnostics & Privacy", style = MaterialTheme.typography.titleMedium)
                    SettingsToggleRow(
                        title = "Crash reports",
                        description = "Send anonymous crash and error reports, with the screens and session steps that led to them. Host names, addresses, usernames, and file paths are removed.",
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
                        description = "Add detailed SSH and terminal diagnostics to the connection log. Logs stay on this phone unless you choose Send to developer.",
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
                }
                if (SettingsCategory.ADVANCED in visibleCategories) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_ADVANCED_SETTINGS_LINK)
                    .clickable(onClick = onOpenAdvancedSettings),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Advanced settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "SFTP transfer tuning and other expert options.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open advanced settings"
                    )
                }
            }
                }
                if (SettingsCategory.TRANSFER in visibleCategories) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Transfer Data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export hosts, identities, favorites, port forwards, snippets, terminal themes, custom keys, and app settings to a file, a QR code, or another phone on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { transferChooser.value = "export" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.SETTINGS_EXPORT_BUTTON)
                        ) {
                            Text("Export")
                        }
                        Button(
                            onClick = { transferChooser.value = "import" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.SETTINGS_IMPORT_BUTTON)
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
                }
        }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(UiTestTags.SCREEN_SETTINGS)
        ) {
            if (shellLayoutMode == ShellLayoutMode.WIDE) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsCategory.values().forEach { category ->
                                val selected = selectedCategory.value == category
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(UiTestTags.settingsCategory(category.title))
                                        .clickable { selectedCategory.value = category },
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(category.title, style = MaterialTheme.typography.titleSmall)
                                        Text(category.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    SettingsSections(
                        visibleCategories = setOf(selectedCategory.value),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            } else {
                SettingsSections(
                    visibleCategories = SettingsCategory.values().toSet(),
                    modifier = Modifier
                        .widthIn(max = 980.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                )
            }
        }
        transferChooser.value?.let { direction ->
            TransferMethodDialog(
                exporting = direction == "export",
                onDismiss = { transferChooser.value = null },
                onChoose = { method ->
                    transferChooser.value = null
                    if (direction == "export") {
                        exportToFile.value = method == TransferMethod.FILE
                        exportToWifi.value = method == TransferMethod.WIFI
                        exportToShare.value = method == TransferMethod.SHARE
                        showTransferDialog.value = true
                    } else when (method) {
                        TransferMethod.SHARE -> Unit
                        TransferMethod.FILE -> openImportFileLauncher.launch(
                            arrayOf("application/json", "text/plain", "text/*", "*/*")
                        )
                        TransferMethod.QR -> scanLauncher.launch(
                            buildQrScanOptions(shellLayoutMode, "Scan SSHPeaches export QR")
                        )
                        TransferMethod.WIFI -> {
                            wifiReceiveError.value = null
                            showWifiReceive.value = true
                        }
                    }
                }
            )
        }
        incomingImport.value?.takeIf { !isLocked }?.let { (name, contents) ->
            AlertDialog(
                onDismissRequest = { incomingImport.value = null },
                modifier = Modifier.testTag(UiTestTags.SETTINGS_INCOMING_IMPORT_DIALOG),
                title = { Text("Import this file?") },
                text = {
                    Text(
                        "\"$name\" will be imported into SSHPeaches. Hosts, keys, and settings in it are " +
                            "merged with what you have; newer local changes are kept."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            incomingImport.value = null
                            handleReceivedPayload(contents)
                        },
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_INCOMING_IMPORT_CONFIRM)
                    ) { Text("Import") }
                },
                dismissButton = { TextButton(onClick = { incomingImport.value = null }) { Text("Cancel") } }
            )
        }
        wifiSend.value?.let { sending ->
            WifiSendDialog(
                host = sending.host,
                port = sending.port,
                code = sending.code,
                qr = sending.qr,
                onCancel = {
                    val sender = wifiSender.value
                    wifiSender.value = null
                    sender?.close()
                }
            )
        }
        if (showWifiReceive.value) {
            DisposableEffect(Unit) {
                val browser = LanTransferBrowser(context) { peers -> wifiPeers.value = peers }
                browser.start()
                onDispose {
                    browser.stop()
                    wifiPeers.value = emptyList()
                }
            }
            WifiReceiveDialog(
                peers = wifiPeers.value,
                receiving = wifiReceiving.value,
                error = wifiReceiveError.value,
                onScanQr = {
                    scanLauncher.launch(buildQrScanOptions(shellLayoutMode, "Scan the QR on the other phone"))
                },
                onReceive = ::receiveOverWifi,
                onDismiss = { showWifiReceive.value = false }
            )
        }
        if (showTransferDialog.value) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showTransferDialog.value = false },
                modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_DIALOG),
                title = {
                    Text(
                        when {
                            exportToWifi.value -> "Send over Wi-Fi"
                            exportToShare.value -> "Share export"
                            exportToFile.value -> "Export to file"
                            else -> "Export data"
                        }
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            if (transferWorking.value) return@TextButton
                            transferWorking.value = true
                            scope.launch {
                            // PBKDF2 per exported secret (about 1-2 s each) and QR rendering: off main.
                            val payload = withContext(Dispatchers.Default) {
                                runCatching { onGenerateExportPayload(passphrase) }.getOrNull()
                            }
                            transferWorking.value = false
                            if (payload == null) {
                                exportPassphraseError.value = if (includeSecretsInQr) {
                                    "Unable to export protected data. Unlock the app and try again."
                                } else if (exportToFile.value) {
                                    "Unable to generate export file."
                                } else {
                                    "Unable to generate export QR."
                                }
                                return@launch
                            }
                            if (includeSecretsInQr) {
                                ExportPassphraseCache.transfer = passphrase
                            }
                            exportPassphraseState.value = ""
                            exportConfirmPassphraseState.value = ""
                            exportPassphraseRevealIndex.intValue = -1
                            exportConfirmPassphraseRevealIndex.intValue = -1
                            exportPassphraseError.value = null
                            showTransferDialog.value = false
                            if (exportToWifi.value) {
                                sendOverWifi(payload)
                                return@launch
                            }
                            if (exportToShare.value) {
                                shareExport(payload)
                                return@launch
                            }
                            if (exportToFile.value) {
                                pendingExportPayload.value = payload
                                createExportFileLauncher.launch("sshpeaches-export.json")
                                return@launch
                            }
                            exportQrBitmap.value = withContext(Dispatchers.Default) { renderQrBitmap(payload) }
                            if (exportQrBitmap.value == null) {
                                onShowMessage("Unable to generate export QR.")
                            }
                        }
                    },
                    enabled = !transferWorking.value,
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_EXPORT_GENERATE_BUTTON)
                ) {
                    Text(
                        when {
                            transferWorking.value -> "Working…"
                            exportToWifi.value -> "Start"
                            exportToShare.value -> "Share"
                            exportToFile.value -> "Save file"
                            else -> "Generate QR"
                        }
                    )
                }
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
                    runImport(payload, passphrase)
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
                pinDialogError.value = null
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
                    pinDialogError.value?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pinDialogError.value = when {
                            pinEntry.value.length < 4 -> "PIN must be at least 4 digits."
                            pinEntry.value != confirmPinEntry.value -> "PINs don't match."
                            else -> null
                        }
                        if (pinDialogError.value != null) return@TextButton
                        val pin = pinEntry.value
                        pinEntry.value = ""
                        confirmPinEntry.value = ""
                        showPinDialog.value = false
                        runCatching { onSetPin(pin) }
                    },
                    modifier = Modifier.testTag(UiTestTags.SETTINGS_PIN_SAVE_BUTTON)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog.value = false
                    pinEntry.value = ""
                    confirmPinEntry.value = ""
                    pinDialogError.value = null
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

}

/**
 * Text for a field that saves on every keystroke. Keying `remember` on the persisted value
 * recreated the state whenever a save came back from DataStore, dropping characters typed in
 * between; this keeps the typed text and only adopts a persisted value that is not an echo of one
 * of this field's own saves (e.g. a restore or import changed it).
 */
private class PersistedFieldState(initial: String) {
    var value by mutableStateOf(initial)
    private val pendingSaves = ArrayDeque<String>()

    fun saved(persistedForm: String) {
        pendingSaves.addLast(persistedForm)
    }

    fun onPersisted(persisted: String) {
        val echo = pendingSaves.indexOf(persisted)
        if (echo >= 0) {
            repeat(echo + 1) { pendingSaves.removeFirst() }
        } else {
            pendingSaves.clear()
            value = persisted
        }
    }
}

@Composable
private fun rememberPersistedField(persisted: String): PersistedFieldState {
    val state = remember { PersistedFieldState(persisted) }
    LaunchedEffect(persisted) { state.onPersisted(persisted) }
    return state
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

private data class WifiSendState(val host: String, val port: Int, val code: String, val qr: android.graphics.Bitmap?)

private const val WIFI_TRANSFER_TIMEOUT_MS = 5 * 60 * 1000
private const val MAX_IMPORT_FILE_BYTES = 32 * 1024 * 1024
