package com.majordaftapps.sshpeaches.app

import android.app.Application
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.majordaftapps.sshpeaches.app.appcheck.AppCheckInitializer
import com.majordaftapps.sshpeaches.app.data.repository.AppContainer
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.diagnostics.DiagnosticsScheduler
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
        appScope.launch {
            // Switching launcher aliases closes the screen opened through the old one, so the new icon waits
            // until the app is in the background.
            SettingsStore.appIcon
                .combine(ProcessLifecycleOwner.get().lifecycle.currentStateFlow) { option, state -> option to state }
                .filter { (_, state) -> !state.isAtLeast(Lifecycle.State.STARTED) }
                .map { (option, _) -> option }
                .distinctUntilChanged()
                .collect { option ->
                    AppIconManager.apply(this@SSHPeachesApplication, option)
                }
        }
        AppCheckInitializer.initialize(this)
        TelemetryInitializer.initialize(this)
        runCatching {
            WorkManager.getInstance(this).cancelUniqueWork("sshpeaches_uptime_checks")
        }
        // The Uptime feature was removed; drop its notification channel so it no longer shows in
        // the app's notification settings.
        runCatching {
            getSystemService(NotificationManager::class.java)?.deleteNotificationChannel("uptime_alerts")
        }
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
