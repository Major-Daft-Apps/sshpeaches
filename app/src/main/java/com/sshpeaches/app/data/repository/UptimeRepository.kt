package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import kotlinx.coroutines.flow.Flow

interface UptimeRepository {
    val summaries: Flow<List<HostUptimeSummary>>
    val configs: Flow<List<HostUptimeConfig>>

    suspend fun addHost(hostId: String)
    suspend fun updateConfig(
        hostId: String,
        method: UptimeCheckMethod,
        port: Int,
        intervalMinutes: Int,
        enabled: Boolean
    )
    suspend fun setEnabled(hostId: String, enabled: Boolean)
    suspend fun removeHost(hostId: String)
}
