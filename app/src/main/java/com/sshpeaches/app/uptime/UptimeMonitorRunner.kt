package com.majordaftapps.sshpeaches.app.uptime

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import com.majordaftapps.sshpeaches.app.data.local.HostUptimeSampleEntity
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.local.asModel
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.UnverifiedReason
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UptimeMonitorRunner(
    private val context: Context,
    private val database: SshPeachesDatabase
) : UptimeMonitorRunnerDelegate {
    override suspend fun runDueChecks(
        now: Long,
        hostId: String?
    ) = withContext(Dispatchers.IO) {
        val configs = database.hostUptimeConfigDao().getEnabled()
            .filter { hostId == null || it.hostId == hostId }
        if (configs.isEmpty()) return@withContext

        val dueConfigs = configs.filter { config ->
            hostId != null || config.lastCheckedAt == null ||
                now - config.lastCheckedAt >= config.intervalMinutes.coerceIn(1, 60) * 60_000L
        }
        if (dueConfigs.isEmpty()) return@withContext

        val hostsById = database.hostDao().getAll().associateBy { it.id }
        val hasActiveNetwork = hasActiveNetworkTransport()

        dueConfigs.forEach { config ->
            val host = hostsById[config.hostId]?.asModel() ?: return@forEach
            val outcome = if (shouldSkipCheckForNoNetwork(host.host, hasActiveNetwork)) {
                CheckOutcome(
                    status = UptimeStatus.UNVERIFIED,
                    reason = UnverifiedReason.NO_INTERNET
                )
            } else {
                probeHost(host, config.method, config.port)
            }
            persistOutcome(host = host, configHostId = config.hostId, previousStatus = config.lastStatus, outcome = outcome, now = now)
        }
        database.hostUptimeSampleDao().pruneOlderThan(now - (7L * 24L * 60L * 60L * 1000L))
    }

    private suspend fun persistOutcome(
        host: HostConnection,
        configHostId: String,
        previousStatus: UptimeStatus?,
        outcome: CheckOutcome,
        now: Long
    ) {
        val configDao = database.hostUptimeConfigDao()
        val existing = configDao.getByHostId(configHostId) ?: return
        database.hostUptimeSampleDao().insert(
            HostUptimeSampleEntity(
                hostId = configHostId,
                checkedAt = now,
                status = outcome.status,
                reason = outcome.reason
            )
        )
        val transitionAt = if (existing.lastStatus != outcome.status) now else existing.lastTransitionAt
        configDao.upsert(
            existing.copy(
                lastCheckedAt = now,
                lastStatus = outcome.status,
                lastReason = outcome.reason,
                lastTransitionAt = transitionAt
            )
        )
        if (UptimeNotifications.shouldNotify(previousStatus, outcome.status)) {
            UptimeNotifications.notifyTransition(context, host, outcome.status)
        }
    }

    private suspend fun probeHost(
        host: HostConnection,
        method: UptimeCheckMethod,
        port: Int
    ): CheckOutcome = withContext(Dispatchers.IO) {
        val reachable = runCatching {
            when (method) {
                UptimeCheckMethod.TCP -> tcpReachable(host.host, port)
                UptimeCheckMethod.ICMP -> InetAddress.getByName(host.host).isReachable(2_000)
            }
        }.getOrDefault(false)
        if (reachable) {
            CheckOutcome(UptimeStatus.UP, null)
        } else {
            CheckOutcome(UptimeStatus.DOWN, null)
        }
    }

    private fun tcpReachable(host: String, port: Int): Boolean {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2_000)
            return true
        }
    }

    private fun hasActiveNetworkTransport(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
    }

    private data class CheckOutcome(
        val status: UptimeStatus,
        val reason: UnverifiedReason?
    )
}

internal fun shouldSkipCheckForNoNetwork(host: String, hasActiveNetwork: Boolean): Boolean =
    !hasActiveNetwork && !isLoopbackHost(host)

internal fun isLoopbackHost(host: String): Boolean {
    val normalized = host.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .lowercase()
    return normalized == "localhost" ||
        normalized == "127.0.0.1" ||
        normalized == "::1" ||
        normalized == "0:0:0:0:0:0:0:1"
}
