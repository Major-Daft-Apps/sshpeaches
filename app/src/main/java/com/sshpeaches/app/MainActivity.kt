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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.sshpeaches.app.SSHPeachesApplication
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.service.SessionService
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
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionServiceState.value = null
            serviceBound = false
        }
    }

    private fun setupBiometricPrompt() {
        val manager = BiometricManager.from(this)
        biometricAvailable = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!biometricAvailable) {
            biometricPrompt = null
            biometricPromptInfo = null
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                appViewModel.unlockWithBiometric()
            }
        })
        biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SSHPeaches")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intent = Intent(this, SessionService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        setupBiometricPrompt()
        setContent {
            val viewModel = appViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionService = sessionServiceState.value
            val startSession: (HostConnection, com.sshpeaches.app.data.model.ConnectionMode) -> Unit = remember(sessionService) {
                { host: HostConnection, mode: com.sshpeaches.app.data.model.ConnectionMode ->
                    sessionService?.startSession(host, mode)
                }
            }
            val stopSession: (String) -> Unit = remember(sessionService) {
                { id: String -> sessionService?.stopSession(id) }
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
                        val prompt = biometricPrompt
                        val info = biometricPromptInfo
                        if (prompt != null && info != null) {
                            prompt.authenticate(info)
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
                    onKeyboardSlotChange = viewModel::updateKeyboardSlot,
                    onKeyboardReset = viewModel::resetKeyboardLayout
                )
            }
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        appViewModel.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        if (!appViewModel.uiState.value.allowBackgroundSessions) {
            sessionServiceState.value?.stopAllSessions()
        }
    }
}
