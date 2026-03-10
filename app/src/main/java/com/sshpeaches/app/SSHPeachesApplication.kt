package com.majordaftapps.sshpeaches.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.majordaftapps.sshpeaches.app.data.repository.AppContainer
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode

/**
 * Placeholder application class. Ready for DI wiring in future iterations.
 */
class SSHPeachesApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        SecurityManager.init(this)
        SettingsStore.init(this)
        applyConfiguredNightMode()
        TelemetryInitializer.initialize(this)
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
