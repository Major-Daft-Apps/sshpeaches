package com.majordaftapps.sshpeaches.app.data.repository

import android.content.Context
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.uptime.UptimeMonitorRunner

class AppContainer(context: Context) {
    private val database = SshPeachesDatabase.get(context)

    val repository: AppRepository = RoomAppRepository(database)
    val uptimeRepository: UptimeRepository = RoomUptimeRepository(database)
    val uptimeMonitorRunner: UptimeMonitorRunner = UptimeMonitorRunner(context, database)
}
