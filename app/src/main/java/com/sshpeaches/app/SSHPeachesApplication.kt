package com.majordaftapps.sshpeaches.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.majordaftapps.sshpeaches.app.appcheck.AppCheckInitializer
import com.majordaftapps.sshpeaches.app.data.repository.AppContainer
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.diagnostics.DiagnosticsScheduler
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Placeholder application class. Ready for DI wiring in future iterations.
 */
class SSHPeachesApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        SecurityManager.init(this)
        SettingsStore.init(this)
        applyConfiguredNightMode()
        AppCheckInitializer.initialize(this)
        TelemetryInitializer.initialize(this)
        appScope.launch {
            SettingsStore.usageReportsEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    DiagnosticsScheduler.update(this@SSHPeachesApplication, enabled)
                }
        }
    }

    private fun applyConfiguredNightMode() {
        val themeMode = SettingsStore.getStartupThemeMode()

        val mode = when (themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
