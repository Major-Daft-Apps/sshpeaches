package com.majordaftapps.sshpeaches.app.data.model

enum class UptimeCheckMethod {
    TCP,
    ICMP
}

enum class UptimeStatus {
    UP,
    DOWN,
    UNVERIFIED
}

enum class UnverifiedReason {
    NO_INTERNET,
    DEVICE_INACTIVE,
    SCHEDULER_INACTIVE,
    UNKNOWN
}

enum class UptimeBarBucketStatus {
    NO_DATA,
    UP,
    DOWN,
    UNVERIFIED
}

data class HostUptimeConfig(
    val hostId: String,
    val method: UptimeCheckMethod = UptimeCheckMethod.TCP,
    val port: Int = 22,
    val intervalMinutes: Int = 15,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val lastStatus: UptimeStatus? = null,
    val lastReason: UnverifiedReason? = null,
    val lastTransitionAt: Long? = null
)

data class HostUptimeSample(
    val hostId: String,
    val checkedAt: Long,
    val status: UptimeStatus,
    val reason: UnverifiedReason? = null
)

data class HostUptimeSummary(
    val host: HostConnection,
    val config: HostUptimeConfig,
    val currentStatus: UptimeStatus,
    val currentReason: UnverifiedReason? = null,
    val uptime24hPercent: Double? = null,
    val uptime7dPercent: Double? = null,
    val lastCheckedAt: Long? = null,
    val statusBars24h: List<UptimeBarBucketStatus> = List(48) { UptimeBarBucketStatus.NO_DATA }
)
