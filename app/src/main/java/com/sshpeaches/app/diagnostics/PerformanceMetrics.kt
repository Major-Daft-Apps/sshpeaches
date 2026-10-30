package com.majordaftapps.sshpeaches.app.diagnostics

import android.content.Context
import org.json.JSONObject

/**
 * Connection performance for the weekly usage report, collected only while Settings → Send usage reports is on:
 * how long connecting takes, whether Boost worked (and why not), transfer speeds, listing times and round trips.
 * Counts and quantiles only: no host names, addresses, users, paths or file names. Reset after each report, and
 * dropped when usage reports are turned off.
 */
internal class PerformanceMetrics(private val store: Store) {
    /** Where the figures wait for the next report (SharedPreferences in the app, memory in tests). */
    interface Store {
        fun load(): String?
        fun save(json: String?)
    }

    @Volatile
    var enabled = false
        set(value) {
            field = value
            if (!value) reset()
        }

    private val counts = HashMap<String, Long>()
    private val samples = HashMap<String, ArrayDeque<Long>>()

    init {
        runCatching { store.load()?.let(::restore) }
    }

    /** A session became ready: [kind] is terminal, mosh, browser or console; [reconnect] for a reconnect. */
    fun recordConnect(kind: String, millis: Long, reconnect: Boolean) =
        record { sample("connect.${kind}${if (reconnect) ".reconnect" else ""}.ms", millis) }

    /** Boost for [part] (files or terminal) came up over [outcome] (udp, tcp) or didn't, for a [boostReason] category. */
    fun recordBoost(part: String, outcome: String) = record { count("boost.$part.$outcome") }

    fun recordTransfer(direction: String, route: String, status: String, bytes: Long, millis: Long) = record {
        count("transfer.$direction.$route.$status")
        if (status == "succeeded" && bytes >= MIN_SPEED_BYTES && millis > 0) {
            sample("transfer.$direction.$route.kBps", bytes * 1000 / 1024 / millis)
            count("transfer.$direction.$route.MB", bytes shr 20)
        }
    }

    fun recordListing(entries: Int, millis: Long) = record {
        val size = when {
            entries < 100 -> "small"
            entries < 2_000 -> "medium"
            else -> "large"
        }
        sample("listing.$size.ms", millis)
    }

    /** A round trip timed for the latency badge, [via] "SSH" or "Boost UDP"/"Boost TCP". */
    fun recordLatency(via: String, millis: Long) =
        record { sample("latency.${via.lowercase().replace(' ', '_')}.ms", millis) }

    /** The report's "performance" section: counts as they are, samples as n / p50 / p90 / max. */
    @Synchronized
    fun snapshot(): JSONObject = JSONObject().apply {
        put("counts", JSONObject(counts.toSortedMap() as Map<*, *>))
        val summaries = JSONObject()
        samples.toSortedMap().forEach { (key, values) ->
            val sorted = values.sorted()
            if (sorted.isEmpty()) return@forEach
            summaries.put(key, JSONObject().apply {
                put("n", sorted.size)
                put("p50", sorted[sorted.size / 2])
                put("p90", sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)])
                put("max", sorted.last())
            })
        }
        put("samples", summaries)
    }

    @Synchronized
    fun reset() {
        counts.clear()
        samples.clear()
        runCatching { store.save(null) }
    }

    private inline fun record(block: () -> Unit) {
        if (!enabled) return
        synchronized(this) {
            block()
            runCatching { store.save(persisted().toString()) }
        }
    }

    private fun count(key: String, by: Long = 1) {
        counts[key] = (counts[key] ?: 0L) + by
    }

    private fun sample(key: String, value: Long) {
        val list = samples.getOrPut(key) { ArrayDeque() }
        list.addLast(value)
        while (list.size > MAX_SAMPLES) list.removeFirst()
    }

    private fun persisted() = JSONObject().apply {
        put("counts", JSONObject(counts as Map<*, *>))
        put("samples", JSONObject().apply { samples.forEach { (k, v) -> put(k, org.json.JSONArray(v.toList())) } })
    }

    private fun restore(json: String) {
        val root = JSONObject(json)
        root.optJSONObject("counts")?.let { c -> c.keys().forEach { counts[it] = c.getLong(it) } }
        root.optJSONObject("samples")?.let { s ->
            s.keys().forEach { key ->
                val array = s.getJSONArray(key)
                samples[key] = ArrayDeque((0 until array.length()).map { array.getLong(it) })
            }
        }
    }

    companion object {
        /** Per figure: enough for quantiles, little to keep. */
        const val MAX_SAMPLES = 200
        /** Transfers smaller than this say more about latency than speed. */
        const val MIN_SPEED_BYTES = 1L shl 20

        @Volatile
        private var instance: PerformanceMetrics? = null

        fun get(context: Context): PerformanceMetrics = instance ?: synchronized(this) {
            instance ?: PerformanceMetrics(PreferencesStore(context.applicationContext)).also { instance = it }
        }

        /** A Boost failure's reason (from the session log line) as a category: no free text leaves the phone. */
        fun boostReason(reason: String): String = when {
            "neither UDP nor TCP" in reason -> "blocked_udp_tcp"
            "doesn't let UDP" in reason -> "blocked_udp"
            "isn't Linux" in reason -> "not_linux"
            "processor isn't supported" in reason -> "unsupported_cpu"
            "no free UDP port" in reason -> "no_free_port"
            "still being set up" in reason -> "setup_slow"
            "damaged" in reason -> "helper_damaged"
            "didn't start" in reason || "helper failed" in reason -> "helper_failed"
            else -> "other"
        }
    }

    private class PreferencesStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences("performance_metrics", Context.MODE_PRIVATE)
        override fun load(): String? = prefs.getString(KEY, null)
        override fun save(json: String?) {
            prefs.edit().apply { if (json == null) remove(KEY) else putString(KEY, json) }.apply()
        }

        companion object {
            const val KEY = "metrics"
        }
    }
}
