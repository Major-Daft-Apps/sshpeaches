package com.sshpeaches.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshpeaches.app.service.SessionService
import com.sshpeaches.app.ui.SSHPeachesRoot
import com.sshpeaches.app.ui.state.AppViewModel
import com.sshpeaches.app.ui.theme.SSHPeachesTheme

class MainActivity : ComponentActivity() {

    private var sessionService: SessionService? = null
    private var serviceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            sessionService = (binder as? SessionService.SessionBinder)?.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intent = Intent(this, SessionService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SSHPeachesTheme(themeMode = uiState.themeMode) {
                SSHPeachesRoot(
                    uiState = uiState,
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
                    onHostAdd = viewModel::addHost,
                    onHostUpdate = viewModel::updateHost,
                    onHostDelete = viewModel::deleteHost,
                    onPortForwardAdd = viewModel::addPortForward,
                    onPortForwardUpdate = viewModel::updatePortForward,
                    onPortForwardDelete = viewModel::deletePortForward,
                    onIdentityAdd = viewModel::addIdentity,
                    onIdentityUpdate = viewModel::updateIdentity,
                    onIdentityDelete = viewModel::deleteIdentity
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
}
