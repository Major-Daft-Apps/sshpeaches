package com.sshpeaches.app

import android.app.Application
import com.sshpeaches.app.data.repository.AppContainer
import com.sshpeaches.app.data.settings.SettingsStore
import com.sshpeaches.app.security.SecurityManager

/**
 * Placeholder application class. Ready for DI wiring in future iterations.
 */
class SSHPeachesApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SecurityManager.init(this)
        SettingsStore.init(this)
        container = AppContainer(this)
    }
}
