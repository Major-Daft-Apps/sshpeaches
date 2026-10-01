package com.majordaftapps.sshpeaches.app.data.repository

import android.content.Context
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase

class AppContainer(context: Context) {
    private val database = SshPeachesDatabase.get(context)

    val repository: AppRepository = RoomAppRepository(database)
}
