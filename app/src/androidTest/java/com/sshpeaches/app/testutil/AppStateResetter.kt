package com.majordaftapps.sshpeaches.app.testutil

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.service.SessionService
import kotlinx.coroutines.runBlocking

object AppStateResetter {
    private val securePrefsNames = listOf(
        "secure_store",
        "secure_store__androidx_security_crypto_encrypted_prefs_keyset__",
        "secure_store__androidx_security_crypto_encrypted_prefs_value_keyset__",
        "sshpeaches_widget_state"
    )

    fun reset(context: Context) {
        val appContext = context.applicationContext
        runBlocking {
            SettingsStore.init(appContext)
            SettingsStore.resetToDefaults()
            SshPeachesDatabase.get(appContext).clearAllTables()
            WorkManager.getInstance(appContext).cancelAllWork()
        }

        SecurityManager.init(appContext)
        runCatching { SecurityManager.clearPin() }
        runCatching { SecurityManager.unlock() }

        securePrefsNames.forEach { name ->
            appContext.deleteSharedPreferences(name)
        }
        appContext.deleteFile("known_hosts")
        appContext.stopService(Intent(appContext, SessionService::class.java))
    }
}
