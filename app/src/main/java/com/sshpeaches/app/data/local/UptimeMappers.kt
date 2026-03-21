package com.majordaftapps.sshpeaches.app.data.local

import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSample

fun HostUptimeConfigEntity.asModel(): HostUptimeConfig = HostUptimeConfig(
    hostId = hostId,
    method = method,
    port = port,
    intervalMinutes = intervalMinutes,
    enabled = enabled,
    createdAt = createdAt,
    lastCheckedAt = lastCheckedAt,
    lastStatus = lastStatus,
    lastReason = lastReason,
    lastTransitionAt = lastTransitionAt
)

fun HostUptimeConfig.asEntity(): HostUptimeConfigEntity = HostUptimeConfigEntity(
    hostId = hostId,
    method = method,
    port = port,
    intervalMinutes = intervalMinutes,
    enabled = enabled,
    createdAt = createdAt,
    lastCheckedAt = lastCheckedAt,
    lastStatus = lastStatus,
    lastReason = lastReason,
    lastTransitionAt = lastTransitionAt
)

fun HostUptimeSampleEntity.asModel(): HostUptimeSample = HostUptimeSample(
    hostId = hostId,
    checkedAt = checkedAt,
    status = status,
    reason = reason
)

fun HostUptimeSample.asEntity(): HostUptimeSampleEntity = HostUptimeSampleEntity(
    hostId = hostId,
    checkedAt = checkedAt,
    status = status,
    reason = reason
)
