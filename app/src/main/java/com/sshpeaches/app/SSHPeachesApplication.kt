package com.majordaftapps.sshpeaches.app

import android.app.Application
import android.util.Log
import com.majordaftapps.sshpeaches.app.data.repository.AppContainer
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Placeholder application class. Ready for DI wiring in future iterations.
 */
class SSHPeachesApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installFullBouncyCastleProvider()
        SecurityManager.init(this)
        SettingsStore.init(this)
        TelemetryInitializer.initialize(this)
        container = AppContainer(this)
    }

    private fun installFullBouncyCastleProvider() {
        val provider = runCatching {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
            Security.getProvider("BC")
        }.onFailure { error ->
            Log.w("SSHPeaches", "Unable to install full BouncyCastle provider", error)
        }.getOrNull()
        if (provider != null) {
            Log.i("SSHPeaches", "Active BC provider: ${provider.javaClass.name} (${provider.info})")
        }
    }
}
