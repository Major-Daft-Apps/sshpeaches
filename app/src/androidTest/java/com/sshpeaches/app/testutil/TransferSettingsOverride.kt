package com.majordaftapps.sshpeaches.app.testutil

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.service.BoostHelper
import kotlinx.coroutines.runBlocking

/**
 * Applies `-e sftpConnections N` and `-e sftpMaxRequests N` from the instrumentation arguments to the app's transfer
 * settings. The managed test emulator's virtual Wi-Fi dies when the guest's uploads back up (fixed only by restarting
 * it), so its runners pass 1 and 2, about what single-connection transfers kept in flight. `-e boostStreams N` does
 * the same for file boost's requests in flight. Without the arguments nothing changes.
 */
object TransferSettingsOverride {
    fun apply(context: Context) {
        val args = InstrumentationRegistry.getArguments()
        val connections = args.getString("sftpConnections")?.toIntOrNull()
        val maxRequests = args.getString("sftpMaxRequests")?.toIntOrNull()
        args.getString("boostStreams")?.toIntOrNull()?.let { BoostHelper.streamsForTests = it }
        if (connections == null && maxRequests == null) return
        runBlocking {
            SettingsStore.init(context.applicationContext)
            connections?.let { SettingsStore.setParallelDownloads(it) }
            maxRequests?.let { SettingsStore.setSftpMaxRequests(it) }
        }
    }

    /**
     * Turns Boost off (it's on by default): tests of SSH and SFTP behaviour against a real Linux server would
     * otherwise go through the Boost helper, around the network impairment they set up.
     */
    fun withoutBoost(context: Context) {
        runBlocking {
            SettingsStore.init(context.applicationContext)
            SettingsStore.setBoost(false)
        }
    }
}
