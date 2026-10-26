package com.majordaftapps.sshpeaches.app.testutil

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Applies `-e sftpConnections N` and `-e sftpMaxRequests N` from the instrumentation arguments to the app's transfer
 * settings. The managed test emulator's virtual Wi-Fi dies when the guest's uploads back up (fixed only by restarting
 * it), so its runners pass 1 and 2, about what single-connection transfers kept in flight. Without the arguments
 * nothing changes.
 */
object TransferSettingsOverride {
    fun apply(context: Context) {
        val args = InstrumentationRegistry.getArguments()
        val connections = args.getString("sftpConnections")?.toIntOrNull()
        val maxRequests = args.getString("sftpMaxRequests")?.toIntOrNull()
        if (connections == null && maxRequests == null) return
        runBlocking {
            SettingsStore.init(context.applicationContext)
            connections?.let { SettingsStore.setParallelDownloads(it) }
            maxRequests?.let { SettingsStore.setSftpMaxRequests(it) }
        }
    }
}
