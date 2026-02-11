package com.sshpeaches.app

import android.app.Application
import android.util.Log
import com.sshpeaches.app.logging.CrashLogger

private const val TAG = "CW/SSHPeachesApplication"

/**
 * Application bootstrap.
 */
class SSHPeachesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "APP lifecycle_start version=0.1.0")
        CrashLogger.install(this)
    }
}
