package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryUptimeRepository : UptimeRepository {
    private val configFlow = MutableStateFlow<List<HostUptimeConfig>>(emptyList())

    override val summaries: Flow<List<HostUptimeSummary>> = MutableStateFlow(emptyList())
    override val configs: Flow<List<HostUptimeConfig>> = configFlow

    override suspend fun addHost(hostId: String) {
        if (configFlow.value.any { it.hostId == hostId }) return
        configFlow.update { it + HostUptimeConfig(hostId = hostId) }
    }

    override suspend fun updateConfig(
        hostId: String,
        method: UptimeCheckMethod,
        port: Int,
        intervalMinutes: Int,
        enabled: Boolean
    ) {
        configFlow.update { list ->
            list.map {
                if (it.hostId == hostId) {
                    it.copy(
                        method = method,
                        port = port,
                        intervalMinutes = intervalMinutes,
                        enabled = enabled
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun setEnabled(hostId: String, enabled: Boolean) {
        configFlow.update { list ->
            list.map { if (it.hostId == hostId) it.copy(enabled = enabled) else it }
        }
    }

    override suspend fun removeHost(hostId: String) {
        configFlow.update { list -> list.filterNot { it.hostId == hostId } }
    }
}
