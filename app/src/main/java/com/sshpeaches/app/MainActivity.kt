package com.sshpeaches.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.sshpeaches.app.SSHPeachesApplication
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.service.SessionService
import com.sshpeaches.app.ui.logging.UiDebugLog
import com.sshpeaches.app.ui.SSHPeachesRoot
import com.sshpeaches.app.ui.state.AppViewModel
import com.sshpeaches.app.ui.theme.SSHPeachesTheme

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
        enableEdgeToEdge()
        val intent = Intent(this, SessionService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        UiDebugLog.result("MainActivity.onCreate", true, "requestedServiceStart=true")
        setupBiometricPrompt()
        setContent {
            val viewModel = appViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionService = sessionServiceState.value
            val sessionSnapshots by sessionService?.sessionsFlow()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
            val startSession: (HostConnection, com.sshpeaches.app.data.model.ConnectionMode, String?) -> Unit = remember(sessionService) {
                { host: HostConnection, mode: com.sshpeaches.app.data.model.ConnectionMode, password: String? ->
                    UiDebugLog.action(
                        "uiStartSession",
                        "hostId=${host.id}, mode=$mode, serviceReady=${sessionService != null}, hasPasswordOverride=${!password.isNullOrBlank()}"
                    )
                    if (sessionService == null) {
                        UiDebugLog.result("uiStartSession", false, "service-not-ready")
                    } else {
                        sessionService.startSession(host, mode, password)
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
            SSHPeachesTheme(themeMode = uiState.themeMode) {
                SSHPeachesRoot(
                    uiState = uiState,
                    biometricAvailable = biometricAvailable,
                    onSortModeChange = viewModel::setSortMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    onBackgroundModeChange = viewModel::setBackgroundSessions,
                    onBiometricToggle = viewModel::setBiometricLock,
                    onLockTimeoutChange = viewModel::setLockTimeout,
                    onCrashReportsToggle = viewModel::setCrashReports,
                    onAnalyticsToggle = viewModel::setAnalytics,
                    onDiagnosticsToggle = viewModel::setDiagnosticsLogging,
                    onIncludeIdentitiesToggle = viewModel::setIncludeIdentities,
                    onIncludeSettingsToggle = viewModel::setIncludeSettings,
                    onAutoStartForwardsToggle = viewModel::setAutoStartForwards,
                    onHostKeyPromptToggle = viewModel::setHostKeyPrompt,
                    onUsageReportsToggle = viewModel::setUsageReports,
                    onSetPin = viewModel::setPin,
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
                    onHostAdd = { name, host, port, user, auth, group, notes, mode, password, suppliedId ->
                        viewModel.addHost(name, host, port, user, auth, group, notes, mode, password, suppliedId)
                    },
                    onHostUpdate = { id, name, host, port, user, auth, group, notes, mode, password ->
                        viewModel.updateHost(id, name, host, port, user, auth, group, notes, mode, password)
                    },
                    onHostDelete = viewModel::deleteHost,
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
                    onSendSessionShortcut = sendSessionShortcut,
                    sessions = sessionSnapshots
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

    override fun onUserInteraction() {
        super.onUserInteraction()
        appViewModel.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        UiDebugLog.action("MainActivity.onStop", "allowBackground=${appViewModel.uiState.value.allowBackgroundSessions}")
        if (!appViewModel.uiState.value.allowBackgroundSessions) {
            sessionServiceState.value?.stopAllSessions()
            UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=true")
        } else {
            UiDebugLog.result("MainActivity.onStop", true, "stoppedAllSessions=false")
        }
    }
}
