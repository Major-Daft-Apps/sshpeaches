package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.local.asEntity
import com.majordaftapps.sshpeaches.app.data.local.asModel
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.uptime.UptimeSummaryCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomUptimeRepository(
    private val database: SshPeachesDatabase
) : UptimeRepository {
    private val hostDao = database.hostDao()
    private val configDao = database.hostUptimeConfigDao()
    private val sampleDao = database.hostUptimeSampleDao()

    override val configs: Flow<List<HostUptimeConfig>> =
        configDao.observeAll().combine(hostDao.observeAll()) { configs, hosts ->
            val hostIds = hosts.map { it.id }.toSet()
            configs.filter { hostIds.contains(it.hostId) }.map { it.asModel() }
        }

    override val summaries: Flow<List<HostUptimeSummary>> = combine(
        hostDao.observeAll(),
        configDao.observeAll(),
        sampleDao.observeSince(0L)
    ) { hosts, configEntities, sampleEntities ->
        val hostsById = hosts.associateBy { it.id }
        val samplesByHost = sampleEntities.map { it.asModel() }.groupBy { it.hostId }
        val now = System.currentTimeMillis()
        configEntities
            .map { it.asModel() }
            .mapNotNull { config ->
                val host = hostsById[config.hostId]?.asModel() ?: return@mapNotNull null
                UptimeSummaryCalculator.buildSummary(
                    host = host,
                    config = config,
                    samples = samplesByHost[config.hostId].orEmpty(),
                    now = now
                )
            }
            .sortedBy { it.host.name.lowercase() }
    }

    override suspend fun addHost(hostId: String) {
        if (configDao.getByHostId(hostId) != null) {
            return
        }
        configDao.upsert(HostUptimeConfig(hostId = hostId).asEntity())
    }

    override suspend fun updateConfig(
        hostId: String,
        method: UptimeCheckMethod,
        port: Int,
        intervalMinutes: Int,
        enabled: Boolean
    ) {
        val existing = configDao.getByHostId(hostId) ?: HostUptimeConfig(hostId = hostId).asEntity()
        configDao.upsert(
            existing.copy(
                method = method,
                port = port.coerceIn(1, 65_535),
                intervalMinutes = intervalMinutes.coerceIn(1, 60),
                enabled = enabled
            )
        )
    }

    override suspend fun setEnabled(hostId: String, enabled: Boolean) {
        val existing = configDao.getByHostId(hostId) ?: return
        configDao.upsert(existing.copy(enabled = enabled))
    }

    override suspend fun removeHost(hostId: String) {
        sampleDao.deleteByHostId(hostId)
        configDao.deleteByHostId(hostId)
    }
}
