package com.majordaftapps.sshpeaches.app.appcheck

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckInitializer {
    private const val TAG = "SSHPeachesAppCheck"

    @Suppress("UNUSED_PARAMETERS")
    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            // Firebase is not configured in this process (e.g. JVM unit tests);
            // skip App Check instead of crashing startup.
            return
        }
        runCatching {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }.onFailure {
            Log.w(TAG, "App Check initialization skipped: ${it.message}")
        }
    }
}
