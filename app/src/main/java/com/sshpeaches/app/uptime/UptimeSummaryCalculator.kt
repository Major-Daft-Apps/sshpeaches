package com.majordaftapps.sshpeaches.app.uptime

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSample
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.UnverifiedReason
import com.majordaftapps.sshpeaches.app.data.model.UptimeBarBucketStatus
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import kotlin.math.max

object UptimeSummaryCalculator {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val WEEK_MILLIS = 7L * DAY_MILLIS
    private const val HALF_HOUR_MILLIS = 30L * 60L * 1000L

    fun buildSummary(
        host: HostConnection,
        config: HostUptimeConfig,
        samples: List<HostUptimeSample>,
        now: Long
    ): HostUptimeSummary {
        val completedSamples = synthesizeGapSamples(config, samples, now)
        val currentState = statusAt(config, completedSamples, now)
        return HostUptimeSummary(
            host = host,
            config = config,
            currentStatus = currentState.first,
            currentReason = currentState.second,
            uptime24hPercent = percentageForWindow(config, completedSamples, now - DAY_MILLIS, now),
            uptime7dPercent = percentageForWindow(config, completedSamples, now - WEEK_MILLIS, now),
            lastCheckedAt = config.lastCheckedAt,
            statusBars24h = build24hBars(config, completedSamples, now)
        )
    }

    fun synthesizeGapSamples(
        config: HostUptimeConfig,
        samples: List<HostUptimeSample>,
        now: Long
    ): List<HostUptimeSample> {
        val intervalMillis = intervalMillis(config)
        val graceMillis = graceMillis(config)
        val sorted = samples.sortedBy { it.checkedAt }
        val out = mutableListOf<HostUptimeSample>()

        if (sorted.isEmpty()) {
            appendGapSamples(
                target = out,
                hostId = config.hostId,
                startExclusive = config.createdAt,
                endExclusive = now,
                intervalMillis = intervalMillis,
                graceMillis = graceMillis
            )
            return out
        }

        sorted.forEachIndexed { index, sample ->
            out += sample
            val nextTime = sorted.getOrNull(index + 1)?.checkedAt ?: now
            appendGapSamples(
                target = out,
                hostId = config.hostId,
                startExclusive = sample.checkedAt,
                endExclusive = nextTime,
                intervalMillis = intervalMillis,
                graceMillis = graceMillis
            )
        }
        return out.sortedBy { it.checkedAt }
    }

    fun statusAt(
        config: HostUptimeConfig,
        samples: List<HostUptimeSample>,
        at: Long
    ): Pair<UptimeStatus, UnverifiedReason?> {
        if (at < config.createdAt) {
            return UptimeStatus.UNVERIFIED to UnverifiedReason.UNKNOWN
        }
        val latest = samples.filter { it.checkedAt <= at }.maxByOrNull { it.checkedAt }
        if (latest != null) {
            return latest.status to latest.reason
        }
        val firstDueAt = config.createdAt + intervalMillis(config)
        return if (at >= firstDueAt + graceMillis(config)) {
            UptimeStatus.UNVERIFIED to UnverifiedReason.SCHEDULER_INACTIVE
        } else {
            UptimeStatus.UNVERIFIED to UnverifiedReason.UNKNOWN
        }
    }

    fun percentageForWindow(
        config: HostUptimeConfig,
        samples: List<HostUptimeSample>,
        windowStart: Long,
        windowEnd: Long
    ): Double? {
        val intervalMillis = intervalMillis(config)
        val start = max(windowStart, config.createdAt)
        if (start >= windowEnd) return null
        var verifiedChecks = 0
        var upChecks = 0
        var tick = start
        while (tick <= windowEnd) {
            when (statusAt(config, samples, tick).first) {
                UptimeStatus.UP -> {
                    verifiedChecks += 1
                    upChecks += 1
                }
                UptimeStatus.DOWN -> verifiedChecks += 1
                UptimeStatus.UNVERIFIED -> Unit
            }
            tick += intervalMillis
        }
        if (verifiedChecks == 0) return null
        return (upChecks.toDouble() / verifiedChecks.toDouble()) * 100.0
    }

    fun build24hBars(
        config: HostUptimeConfig,
        samples: List<HostUptimeSample>,
        now: Long
    ): List<UptimeBarBucketStatus> {
        val windowStart = now - DAY_MILLIS
        return List(48) { index ->
            val bucketEnd = windowStart + ((index + 1) * HALF_HOUR_MILLIS)
            when {
                bucketEnd < config.createdAt -> UptimeBarBucketStatus.NO_DATA
                else -> when (statusAt(config, samples, bucketEnd).first) {
                    UptimeStatus.UP -> UptimeBarBucketStatus.UP
                    UptimeStatus.DOWN -> UptimeBarBucketStatus.DOWN
                    UptimeStatus.UNVERIFIED -> {
                        if (bucketEnd < config.createdAt + intervalMillis(config)) {
                            UptimeBarBucketStatus.NO_DATA
                        } else {
                            UptimeBarBucketStatus.UNVERIFIED
                        }
                    }
                }
            }
        }
    }

    private fun appendGapSamples(
        target: MutableList<HostUptimeSample>,
        hostId: String,
        startExclusive: Long,
        endExclusive: Long,
        intervalMillis: Long,
        graceMillis: Long
    ) {
        var nextExpected = startExclusive + intervalMillis
        while (nextExpected + graceMillis < endExclusive) {
            target += HostUptimeSample(
                hostId = hostId,
                checkedAt = nextExpected,
                status = UptimeStatus.UNVERIFIED,
                reason = UnverifiedReason.SCHEDULER_INACTIVE
            )
            nextExpected += intervalMillis
        }
    }

    private fun intervalMillis(config: HostUptimeConfig): Long =
        config.intervalMinutes.coerceIn(1, 60) * 60_000L

    private fun graceMillis(config: HostUptimeConfig): Long =
        minOf(intervalMillis(config) / 2L, 5L * 60L * 1000L).coerceAtLeast(60_000L)
}
