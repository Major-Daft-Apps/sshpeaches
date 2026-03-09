package com.majordaftapps.sshpeaches.app

import android.app.Application
import com.majordaftapps.sshpeaches.app.data.repository.AppContainer
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer

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
        TelemetryInitializer.initialize(this)
    }
}
