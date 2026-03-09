package com.majordaftapps.sshpeaches.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.ssh.IdentityKeyInstaller
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.ui.SSHPeachesRoot
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.state.BackgroundSessionTimeout
import com.majordaftapps.sshpeaches.app.ui.state.AppViewModel
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import com.termux.terminal.TerminalEmulator
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        val app = application as SSHPeachesApplication
        AppViewModel.provideFactory(app.container.repository)
    }
    private val sessionServiceState = mutableStateOf<SessionService?>(null)
    private var serviceBound = false
    private var serviceConnectionRequested = false
    private var biometricPrompt: BiometricPrompt? = null
    private var biometricPromptInfo: BiometricPrompt.PromptInfo? = null
    private var biometricAvailable: Boolean = false
    private var appUiBootstrapped = false
    private val requestedOpenSessionHostId = mutableStateOf<String?>(null)
    private val corePermissionsRefreshTick = mutableStateOf(0)
    private var latestAllowBackgroundSessions: Boolean = true
    private var latestBackgroundSessionTimeout: BackgroundSessionTimeout = BackgroundSessionTimeout.FOREVER
    private var backgroundSessionTimeoutJob: Job? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            UiDebugLog.result("notificationPermissionRequest", granted)
            refreshCorePermissionStatuses()
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            sessionServiceState.value = (binder as? SessionService.SessionBinder)?.getService()
            serviceBound = true
            serviceConnectionRequested = true
            UiDebugLog.result("SessionService.onServiceConnected", true, "bound=true")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionServiceState.value = null
            serviceBound = false
            serviceConnectionRequested = false
            UiDebugLog.result("SessionService.onServiceDisconnected", true, "bound=false")
        }
    }

    private fun setupBiometricPrompt() {
        UiDebugLog.action("setupBiometricPrompt")
        val manager = BiometricManager.from(this)
        biometricAvailable = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!biometricAvailable) {
            biometricPrompt = null
            biometricPromptInfo = null
            UiDebugLog.result("setupBiometricPrompt", false, "biometricUnavailable")
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                UiDebugLog.result("biometricAuthentication", true)
                appViewModel.unlockWithBiometric()
            }
        })
        biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SSHPeaches")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Use PIN")
            .build()
        UiDebugLog.result("setupBiometricPrompt", true, "biometricAvailable=true")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            UiDebugLog.result("notificationPermissionCheck", true, "already-granted")
            return
        }
        UiDebugLog.action("notificationPermissionRequest", "requesting=true")
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun refreshCorePermissionStatuses() {
        corePermissionsRefreshTick.value = corePermissionsRefreshTick.value + 1
    }

    private fun openAppPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    private fun buildCorePermissionStatuses(): List<CorePermissionStatus> {
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationsGranted = postNotificationsGranted && notificationsEnabled

        val foregroundServiceGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.FOREGROUND_SERVICE
        ) == PackageManager.PERMISSION_GRANTED

        val foregroundServiceTypeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val dataSyncGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
            ) == PackageManager.PERMISSION_GRANTED
            val connectedDeviceGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
            ) == PackageManager.PERMISSION_GRANTED
            dataSyncGranted && connectedDeviceGranted
        } else {
            true
        }

        return listOf(
            CorePermissionStatus(
                id = "notifications",
                title = "Notifications",
                description = "Required for active SSH session foreground service controls and status.",
                granted = notificationsGranted
            ),
            CorePermissionStatus(
                id = "foreground_service",
                title = "Foreground Service",
                description = "Required to keep SSH sessions alive in background mode.",
                granted = foregroundServiceGranted && foregroundServiceTypeGranted
            )
        )
    }

    private fun ensureSessionServiceConnection() {
        if (serviceBound || serviceConnectionRequested) return
        val serviceIntent = Intent(this, SessionService::class.java)
        serviceConnectionRequested = true
        val requestedServiceStart = runCatching {
            startService(serviceIntent)
            true
        }.onFailure { error ->
            UiDebugLog.error("MainActivity.startService", error)
        }.getOrDefault(false)
        val bindRequested = runCatching {
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure { error ->
            UiDebugLog.error("MainActivity.bindService", error)
        }.getOrDefault(false)
        if (!bindRequested) {
            serviceConnectionRequested = false
        }
        UiDebugLog.result(
            "MainActivity.ensureSessionServiceConnection",
            requestedServiceStart || bindRequested,
            "requestedServiceStart=$requestedServiceStart, bindRequested=$bindRequested"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiDebugLog.action("MainActivity.onCreate")
        UiDebugLog.result("MainActivity.onCreate", true)
        setupBiometricPrompt()
        lifecycleScope.launch {
            appViewModel.uiState.collect { state ->
                latestAllowBackgroundSessions = state.allowBackgroundSessions
                latestBackgroundSessionTimeout = state.backgroundSessionTimeout
            }
        }
        handleSessionOpenIntent(this.intent)
        setContent {
            var deferHeavyUi by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                deferHeavyUi = false
            }
            if (deferHeavyUi) {
                SSHPeachesTheme(themeMode = ThemeMode.SYSTEM) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val splashLogo = if (isSystemInDarkTheme()) {
                            com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches_splash_white
                        } else {
                            com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches_splash_black
                        }
                        Image(
                            painter = painterResource(id = splashLogo),
                            contentDescription = "SSHPeaches logo",
                            modifier = Modifier.size(220.dp)
                        )
                        Text(
                            text = "SSHPeaches",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                return@setContent
            }
            val viewModel = appViewModel
            appUiBootstrapped = true
            LaunchedEffect(Unit) {
                viewModel.onAppForegrounded()
            }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionService = sessionServiceState.value
            val sessionSnapshots by sessionService?.sessionsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val hostKeyPrompts by sessionService?.hostKeyPromptsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val passwordPrompts by sessionService?.passwordPromptsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val shellOutputs by sessionService?.shellOutputFlow()?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) }
            val remoteDirectories by sessionService?.remoteDirectoryFlow()?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) }
            val startSession: (String, HostConnection, com.majordaftapps.sshpeaches.app.data.model.ConnectionMode, String?) -> Unit =
                remember(
                    sessionService,
                    uiState.portForwards,
                    uiState.snippets,
                    uiState.autoStartForwards,
                    uiState.autoTrustHostKey,
                    uiState.hosts,
                    uiState.terminalEmulation
                ) {
                { sessionId: String, host: HostConnection, mode: com.majordaftapps.sshpeaches.app.data.model.ConnectionMode, password: String? ->
                    UiDebugLog.action(
                        "uiStartSession",
                        "sessionId=$sessionId, hostId=${host.id}, mode=$mode, serviceReady=${sessionService != null}, hasPasswordOverride=${!password.isNullOrBlank()}"
                    )
                    if (sessionService == null) {
                        ensureSessionServiceConnection()
                        UiDebugLog.result("uiStartSession", false, "service-not-ready")
                    } else {
                        requestNotificationPermissionIfNeeded()
                        sessionService.startSession(
                            requestedSessionId = sessionId,
                            host = host,
                            mode = mode,
                            passwordOverride = password,
                            availableForwards = uiState.portForwards,
                            availableSnippets = uiState.snippets,
                            autoStartForwards = uiState.autoStartForwards,
                            autoTrustUnknownHostKey = uiState.autoTrustHostKey,
                            allowPasswordSave = uiState.hosts.any { saved -> saved.id == host.id },
                            terminalEmulation = uiState.terminalEmulation
                        )
                        UiDebugLog.result("uiStartSession", true, "sessionId=$sessionId, hostId=${host.id}")
                    }
                }
            }
            val stopSession: (String) -> Unit = remember(sessionService) {
                { id: String ->
                    UiDebugLog.action("uiStopSession", "hostId=$id, serviceReady=${sessionService != null}")
                    if (sessionService == null) {
                        UiDebugLog.result("uiStopSession", false, "service-not-ready")
                    } else {
                        sessionService.stopSession(id)
                        UiDebugLog.result("uiStopSession", true, "hostId=$id")
                    }
                }
            }
            val sendSessionShortcut: (String, String) -> Unit = remember(sessionService) {
                { hostId: String, value: String ->
                    UiDebugLog.action(
                        "uiSendSessionShortcut",
                        "hostId=$hostId, valueBlank=${value.isBlank()}, serviceReady=${sessionService != null}"
                    )
                    if (value.isNotBlank()) {
                        if (sessionService == null) {
                            UiDebugLog.result("uiSendSessionShortcut", false, "service-not-ready")
                        } else {
                            sessionService.sendKeyboardShortcut(hostId, value)
                            UiDebugLog.result("uiSendSessionShortcut", true, "hostId=$hostId")
                        }
                    } else {
                        UiDebugLog.result("uiSendSessionShortcut", false, "blank-shortcut")
                    }
                }
            }
            val sendShellBytes: (String, ByteArray) -> Unit = remember(sessionService) {
                { hostId: String, value: ByteArray ->
                    if (value.isNotEmpty()) {
                        sessionService?.sendShellBytes(hostId, value)
                    }
                }
            }
            val resizeShell: (String, Int, Int) -> Unit = remember(sessionService) {
                { hostId: String, columns: Int, rows: Int ->
                    sessionService?.resizeShell(hostId, columns, rows)
                }
            }
            val listSftpDirectory: (String, String) -> Unit = remember(sessionService) {
                { hostId: String, path: String ->
                    sessionService?.listSftpDirectory(hostId, path)
                }
            }
            val sftpDownloadFile: (String, String, String?) -> Unit = remember(sessionService) {
                { hostId: String, remotePath: String, localPath: String? ->
                    sessionService?.sftpDownloadFile(hostId, remotePath, localPath)
                }
            }
            val sftpUploadFile: (String, String, String) -> Unit = remember(sessionService) {
                { hostId: String, localPath: String, remotePath: String ->
                    sessionService?.sftpUploadFile(hostId, localPath, remotePath)
                }
            }
            val manageRemotePath: (String, String, String, String?) -> Unit = remember(sessionService) {
                { hostId: String, operation: String, sourcePath: String, destinationPath: String? ->
                    sessionService?.manageRemotePath(hostId, operation, sourcePath, destinationPath)
                }
            }
            val scpDownloadFile: (String, String, String?) -> Unit = remember(sessionService) {
                { hostId: String, remotePath: String, localPath: String? ->
                    sessionService?.scpDownloadFile(hostId, remotePath, localPath)
                }
            }
            val scpUploadFile: (String, String, String) -> Unit = remember(sessionService) {
                { hostId: String, localPath: String, remotePath: String ->
                    sessionService?.scpUploadFile(hostId, localPath, remotePath)
                }
            }
            val resolveTerminalEmulator: (String) -> TerminalEmulator? = remember(sessionService) {
                { hostId: String ->
                    sessionService?.resolveTerminalEmulator(hostId)
                }
            }
            LaunchedEffect(sessionSnapshots, uiState.hosts) {
                sessionSnapshots.forEach { snapshot ->
                    val detected = snapshot.host.osMetadata
                    if (detected == OsMetadata.Undetected) return@forEach
                    val saved = uiState.hosts.firstOrNull { it.id == snapshot.host.id } ?: return@forEach
                    if (saved.osMetadata != detected) {
                        viewModel.updateHostOsMetadata(saved.id, detected)
                    }
                }
            }
            val permissionsTick = corePermissionsRefreshTick.value
            val corePermissions = remember(permissionsTick) { buildCorePermissionStatuses() }
            SSHPeachesTheme(themeMode = uiState.themeMode) {
                SSHPeachesRoot(
                    uiState = uiState,
                    biometricAvailable = biometricAvailable,
                    onSortModeChange = viewModel::setSortMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    onBackgroundModeChange = viewModel::setBackgroundSessions,
                    onBackgroundSessionTimeoutChange = viewModel::setBackgroundSessionTimeout,
                    onBiometricToggle = viewModel::setBiometricLock,
                    onLockTimeoutChange = viewModel::setLockTimeout,
                    onCustomLockTimeoutMinutesChange = viewModel::setCustomLockTimeoutMinutes,
                    onSnippetRunTimeoutSecondsChange = viewModel::setSnippetRunTimeoutSeconds,
                    onTerminalEmulationChange = viewModel::setTerminalEmulation,
                    onTerminalSelectionModeChange = viewModel::setTerminalSelectionMode,
                    onCrashReportsToggle = viewModel::setCrashReports,
                    onAnalyticsToggle = viewModel::setAnalytics,
                    onDiagnosticsToggle = viewModel::setDiagnosticsLogging,
                    onIncludeIdentitiesToggle = viewModel::setIncludeIdentities,
                    onIncludeSettingsToggle = viewModel::setIncludeSettings,
                    onAutoStartForwardsToggle = viewModel::setAutoStartForwards,
                    onHostKeyPromptToggle = viewModel::setHostKeyPrompt,
                    onAutoTrustHostKeyToggle = viewModel::setAutoTrustHostKey,
                    onUsageReportsToggle = viewModel::setUsageReports,
                    onDefaultTerminalProfileChange = viewModel::setDefaultTerminalProfile,
                    onSaveTerminalProfile = viewModel::saveTerminalProfile,
                    onDeleteTerminalProfile = viewModel::deleteTerminalProfile,
                    onRestoreDefaultSettings = viewModel::restoreDefaultSettings,
                    onSetPin = viewModel::setPin,
                    onClearPin = viewModel::clearPin,
                    onLockApp = viewModel::lockApp,
                    onUnlockWithPin = viewModel::unlockWithPin,
                    onBiometricUnlock = {
                        UiDebugLog.action("uiBiometricUnlock", "promptReady=${biometricPrompt != null && biometricPromptInfo != null}")
                        val prompt = biometricPrompt
                        val info = biometricPromptInfo
                        if (prompt != null && info != null) {
                            prompt.authenticate(info)
                            UiDebugLog.result("uiBiometricUnlock", true)
                        } else {
                            UiDebugLog.result("uiBiometricUnlock", false, "prompt-not-ready")
                        }
                    },
                    onHostAdd = { name, host, port, user, auth, group, notes, mode, useMosh, preferredIdentityId, forwardId, script, backgroundBehavior, terminalProfileId, password, suppliedId ->
                        viewModel.addHost(
                            name,
                            host,
                            port,
                            user,
                            auth,
                            group,
                            notes,
                            mode,
                            useMosh,
                            preferredIdentityId,
                            forwardId,
                            script,
                            backgroundBehavior,
                            terminalProfileId,
                            password,
                            suppliedId
                        )
                    },
                    onHostUpdate = { id, name, host, port, user, auth, group, notes, mode, useMosh, preferredIdentityId, forwardId, script, backgroundBehavior, terminalProfileId, password ->
                        viewModel.updateHost(
                            id,
                            name,
                            host,
                            port,
                            user,
                            auth,
                            group,
                            notes,
                            mode,
                            useMosh,
                            preferredIdentityId,
                            forwardId,
                            script,
                            backgroundBehavior,
                            terminalProfileId,
                            password
                        )
                    },
                    onHostDelete = viewModel::deleteHost,
                    onHostInfoCommandsChange = viewModel::updateHostInfoCommands,
                    onPortForwardAdd = viewModel::addPortForward,
                    onPortForwardUpdate = viewModel::updatePortForward,
                    onPortForwardDelete = viewModel::deletePortForward,
                    onStartSession = startSession,
                    onStopSession = stopSession,
                    onIdentityAdd = viewModel::addIdentity,
                    onIdentityUpdate = viewModel::updateIdentity,
                    onIdentityDelete = viewModel::deleteIdentity,
                    onImportIdentityKey = viewModel::importIdentityKeyFromPayload,
                    onImportIdentityKeyPlain = viewModel::importIdentityKeyPlain,
                    onStoreIdentityPublicKey = viewModel::storeIdentityPublicKey,
                    onStoreIdentityKeyPassphrase = viewModel::storeIdentityKeyPassphrase,
                    onCopyIdentityKeyToHost = { identityId, hostId, hostPassword, identityPassphrase ->
                        val host = uiState.hosts.firstOrNull { it.id == hostId }
                        if (host == null) {
                            false
                        } else {
                            IdentityKeyInstaller.install(
                                context = this@MainActivity,
                                host = host,
                                identityId = identityId,
                                hostPasswordOverride = hostPassword,
                                identityPassphraseOverride = identityPassphrase
                            ).success
                        }
                    },
                    onRemoveIdentityKey = viewModel::removeIdentityKey,
                    onKeyboardSlotChange = viewModel::updateKeyboardSlot,
                    onKeyboardReset = viewModel::resetKeyboardLayout,
                    onSnippetAdd = viewModel::addSnippet,
                    onSnippetUpdate = viewModel::updateSnippet,
                    onSnippetDelete = viewModel::deleteSnippet,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSendSessionShortcut = sendSessionShortcut,
                    onSendShellBytes = sendShellBytes,
                    onResizeShell = resizeShell,
                    onListSftpDirectory = listSftpDirectory,
                    onSftpDownloadFile = sftpDownloadFile,
                    onSftpUploadFile = sftpUploadFile,
                    onManageRemotePath = manageRemotePath,
                    onScpDownloadFile = scpDownloadFile,
                    onScpUploadFile = scpUploadFile,
                    resolveTerminalEmulator = resolveTerminalEmulator,
                    sessions = sessionSnapshots,
                    shellOutputs = shellOutputs,
                    remoteDirectories = remoteDirectories,
                    hostKeyPrompts = hostKeyPrompts,
                    passwordPrompts = passwordPrompts,
                    requestedOpenSessionId = requestedOpenSessionHostId.value,
                    onOpenSessionRequestHandled = { requestedOpenSessionHostId.value = null },
                    onRespondToHostKeyPrompt = { promptId, trust ->
                        sessionService?.respondToHostKeyPrompt(promptId, trust)
                    },
                    onRespondToPasswordPrompt = { promptId, password, savePassword ->
                        sessionService?.respondToPasswordPrompt(promptId, password, savePassword)
                    },
                    corePermissions = corePermissions,
                    onRequestCorePermissions = {
                        requestNotificationPermissionIfNeeded()
                        refreshCorePermissionStatuses()
                    },
                    onOpenAppPermissionSettings = {
                        openAppPermissionSettings()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        UiDebugLog.action("MainActivity.onDestroy", "serviceBound=$serviceBound")
        backgroundSessionTimeoutJob?.cancel()
        backgroundSessionTimeoutJob = null
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        serviceConnectionRequested = false
        super.onDestroy()
        UiDebugLog.result("MainActivity.onDestroy", true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSessionOpenIntent(intent)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (appUiBootstrapped) {
            appViewModel.onUserInteraction()
        }
    }

    override fun onStart() {
        super.onStart()
        backgroundSessionTimeoutJob?.cancel()
        backgroundSessionTimeoutJob = null
        if (appUiBootstrapped) {
            appViewModel.onAppForegrounded()
        }
        window.decorView.post {
            ensureSessionServiceConnection()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCorePermissionStatuses()
    }

    override fun onStop() {
        super.onStop()
        backgroundSessionTimeoutJob?.cancel()
        backgroundSessionTimeoutJob = null
        if (isChangingConfigurations || !appUiBootstrapped) {
            UiDebugLog.action("MainActivity.onStop", "persistSessions=true (config-change-or-not-ready)")
            UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=false")
            return
        }
        appViewModel.onAppBackgrounded()
        when {
            !latestAllowBackgroundSessions -> {
                sessionServiceState.value?.stopAllSessions()
                UiDebugLog.result("MainActivity.backgroundSessionTimeout", true, "stopNow=background-disabled")
            }
            latestBackgroundSessionTimeout.durationMillis != null -> {
                val timeoutMs = latestBackgroundSessionTimeout.durationMillis ?: 0L
                backgroundSessionTimeoutJob = lifecycleScope.launch {
                    delay(timeoutMs)
                    sessionServiceState.value?.stopAllSessions()
                    UiDebugLog.result(
                        "MainActivity.backgroundSessionTimeout",
                        true,
                        "stopNow=timer elapsedMs=$timeoutMs"
                    )
                }
            }
            else -> {
                UiDebugLog.result("MainActivity.backgroundSessionTimeout", true, "stopNow=never")
            }
        }
        UiDebugLog.action("MainActivity.onStop", "persistSessions=true")
        UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=false")
    }

    private fun handleSessionOpenIntent(intent: Intent?) {
        if (intent?.action != SessionService.ACTION_OPEN_SESSION) return
        val hostId = intent.getStringExtra(SessionService.EXTRA_HOST_ID).orEmpty()
        if (hostId.isBlank()) {
            UiDebugLog.result("handleSessionOpenIntent", false, "missing-host-id")
            return
        }
        requestedOpenSessionHostId.value = hostId
        UiDebugLog.result("handleSessionOpenIntent", true, "hostId=$hostId")
    }
}
