package com.majordaftapps.sshpeaches.app.data.local

import androidx.room.Entity
import androidx.room.Index
import com.majordaftapps.sshpeaches.app.data.model.UnverifiedReason
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus

@Entity(
    tableName = "host_uptime_configs",
    indices = [Index(value = ["hostId"], unique = true)]
)
data class HostUptimeConfigEntity(
    @androidx.room.PrimaryKey
    val hostId: String,
    val hostName: String,
    val hostAddress: String,
    val hostPort: Int,
    val hostUsername: String,
    val method: UptimeCheckMethod,
    val port: Int,
    val intervalMinutes: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val lastCheckedAt: Long?,
    val lastStatus: UptimeStatus?,
    val lastReason: UnverifiedReason?,
    val lastTransitionAt: Long?
)

@Entity(
    tableName = "host_uptime_samples",
    primaryKeys = ["hostId", "checkedAt"],
    indices = [
        Index(value = ["hostId"]),
        Index(value = ["checkedAt"]),
        Index(value = ["hostId", "checkedAt"])
    ]
)
data class HostUptimeSampleEntity(
    val hostId: String,
    val checkedAt: Long,
    val status: UptimeStatus,
    val reason: UnverifiedReason?
)
