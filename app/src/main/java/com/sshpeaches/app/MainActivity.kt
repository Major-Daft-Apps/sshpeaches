package com.majordaftapps.sshpeaches.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.ui.SSHPeachesRoot
import com.majordaftapps.sshpeaches.app.ui.state.AppViewModel
import com.majordaftapps.sshpeaches.app.ui.theme.SSHPeachesTheme
import com.termux.terminal.TerminalEmulator

class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        val app = application as SSHPeachesApplication
        AppViewModel.provideFactory(app.container.repository)
    }
    private val sessionServiceState = mutableStateOf<SessionService?>(null)
    private var serviceBound = false
    private var biometricPrompt: BiometricPrompt? = null
    private var biometricPromptInfo: BiometricPrompt.PromptInfo? = null
    private var biometricAvailable: Boolean = false
    private val requestedOpenSessionHostId = mutableStateOf<String?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            sessionServiceState.value = (binder as? SessionService.SessionBinder)?.getService()
            serviceBound = true
            UiDebugLog.result("SessionService.onServiceConnected", true, "bound=true")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionServiceState.value = null
            serviceBound = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiDebugLog.action("MainActivity.onCreate")
        val intent = Intent(this, SessionService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        UiDebugLog.result("MainActivity.onCreate", true, "requestedServiceStart=true")
        setupBiometricPrompt()
        handleSessionOpenIntent(intent)
        setContent {
            val viewModel = appViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionService = sessionServiceState.value
            val sessionSnapshots by sessionService?.sessionsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val hostKeyPrompts by sessionService?.hostKeyPromptsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val passwordPrompts by sessionService?.passwordPromptsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val shellOutputs by sessionService?.shellOutputFlow()?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) }
            val startSession: (HostConnection, com.majordaftapps.sshpeaches.app.data.model.ConnectionMode, String?) -> Unit =
                remember(
                    sessionService,
                    uiState.portForwards,
                    uiState.autoStartForwards,
                    uiState.autoTrustHostKey,
                    uiState.hosts,
                    uiState.terminalEmulation
                ) {
                { host: HostConnection, mode: com.majordaftapps.sshpeaches.app.data.model.ConnectionMode, password: String? ->
                    UiDebugLog.action(
                        "uiStartSession",
                        "hostId=${host.id}, mode=$mode, serviceReady=${sessionService != null}, hasPasswordOverride=${!password.isNullOrBlank()}"
                    )
                    if (sessionService == null) {
                        UiDebugLog.result("uiStartSession", false, "service-not-ready")
                    } else {
                        sessionService.startSession(
                            host = host,
                            mode = mode,
                            passwordOverride = password,
                            availableForwards = uiState.portForwards,
                            autoStartForwards = uiState.autoStartForwards,
                            autoTrustUnknownHostKey = uiState.autoTrustHostKey,
                            allowPasswordSave = uiState.hosts.any { saved -> saved.id == host.id },
                            terminalEmulation = uiState.terminalEmulation
                        )
                        UiDebugLog.result("uiStartSession", true, "hostId=${host.id}")
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
                    val saved = uiState.hosts.firstOrNull { it.id == snapshot.hostId } ?: return@forEach
                    if (saved.osMetadata != detected) {
                        viewModel.updateHostOsMetadata(snapshot.hostId, detected)
                    }
                }
            }
            SSHPeachesTheme(themeMode = uiState.themeMode) {
                SSHPeachesRoot(
                    uiState = uiState,
                    biometricAvailable = biometricAvailable,
                    onSortModeChange = viewModel::setSortMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    onBackgroundModeChange = viewModel::setBackgroundSessions,
                    onBiometricToggle = viewModel::setBiometricLock,
                    onLockTimeoutChange = viewModel::setLockTimeout,
                    onCustomLockTimeoutMinutesChange = viewModel::setCustomLockTimeoutMinutes,
                    onTerminalEmulationChange = viewModel::setTerminalEmulation,
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
                    onScpDownloadFile = scpDownloadFile,
                    onScpUploadFile = scpUploadFile,
                    resolveTerminalEmulator = resolveTerminalEmulator,
                    sessions = sessionSnapshots,
                    shellOutputs = shellOutputs,
                    hostKeyPrompts = hostKeyPrompts,
                    passwordPrompts = passwordPrompts,
                    requestedOpenSessionId = requestedOpenSessionHostId.value,
                    onOpenSessionRequestHandled = { requestedOpenSessionHostId.value = null },
                    onRespondToHostKeyPrompt = { promptId, trust ->
                        sessionService?.respondToHostKeyPrompt(promptId, trust)
                    },
                    onRespondToPasswordPrompt = { promptId, password, savePassword ->
                        sessionService?.respondToPasswordPrompt(promptId, password, savePassword)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        UiDebugLog.action("MainActivity.onDestroy", "serviceBound=$serviceBound")
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
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
        appViewModel.onUserInteraction()
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            appViewModel.onAppBackgrounded()
        }
        UiDebugLog.action("MainActivity.onStop", "allowBackground=${appViewModel.uiState.value.allowBackgroundSessions}")
        if (!appViewModel.uiState.value.allowBackgroundSessions) {
            sessionServiceState.value?.stopAllSessions()
            UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=true")
        } else {
            UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=false")
        }
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
