package com.majordaftapps.sshpeaches.app.service

import kotlin.math.abs

/**
 * A session's round trips to its server, for Advanced settings → Show connection latency. [lastMs] is null while
 * the last probe got no answer ([lost]); the rest cover the samples of the last minute.
 */
data class ConnectionLatency(
    val lastMs: Long?,
    val minMs: Long,
    val avgMs: Long,
    val maxMs: Long,
    /** Average change between consecutive round trips: how steady the connection is. */
    val jitterMs: Long,
    /** What was timed: "SSH" (a keepalive request) or "Boost UDP" / "Boost TCP" (the terminal's pings). */
    val via: String,
    val samples: Int,
    val lost: Boolean
) {
    /** Good under 100 ms, usable under 300 ms, poor above (or no answer). */
    val quality: Quality
        get() = when {
            lost || lastMs == null -> Quality.POOR
            lastMs < 100 -> Quality.GOOD
            lastMs < 300 -> Quality.FAIR
            else -> Quality.POOR
        }

    enum class Quality { GOOD, FAIR, POOR }
}

/** The last [capacity] round trips of one session (null for a probe that got no answer). */
internal class LatencySamples(private val capacity: Int = 12) {
    private val samples = ArrayDeque<Long?>()

    @Synchronized
    fun add(sampleMs: Long?) {
        samples.addLast(sampleMs)
        while (samples.size > capacity) samples.removeFirst()
    }

    @Synchronized
    fun summary(via: String): ConnectionLatency? {
        val answered = samples.filterNotNull()
        if (answered.isEmpty() && samples.isEmpty()) return null
        val last = samples.lastOrNull()
        val jitter = answered.zipWithNext { a, b -> abs(b - a) }.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
        return ConnectionLatency(
            lastMs = last,
            minMs = answered.minOrNull() ?: 0L,
            avgMs = if (answered.isEmpty()) 0L else answered.average().toLong(),
            maxMs = answered.maxOrNull() ?: 0L,
            jitterMs = jitter,
            via = via,
            samples = answered.size,
            lost = last == null
        )
    }
}
