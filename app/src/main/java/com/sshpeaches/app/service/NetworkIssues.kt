package com.majordaftapps.sshpeaches.app.service

/**
 * A network failure reported for investigation the way a crash is (only with Settings → Crash reports on, scrubbed
 * like every report): a dropped connection, a reconnect that gave up, a transfer that had to retry or failed, Boost
 * failing. Crashlytics groups them by [kind]; the failure that caused one is its cause, stack and all.
 */
internal class NetworkIssue(kind: String, cause: Throwable?) : Exception("Network issue: $kind", cause)

/**
 * Keeps network issue reports to what helps investigate: one per kind and cause every [perKeyMillis], and at most
 * [perRun] while the app runs, so a bad evening on a bad network can't flood the reports.
 */
internal class NetworkIssueLimiter(
    private val perKeyMillis: Long = 10 * 60_000L,
    private val perRun: Int = 40,
    private val clock: () -> Long
) {
    private val lastSent = HashMap<String, Long>()
    private var sent = 0

    @Synchronized
    fun allow(key: String): Boolean {
        if (sent >= perRun) return false
        val now = clock()
        val previous = lastSent[key]
        if (previous != null && now - previous < perKeyMillis) return false
        lastSent[key] = now
        sent += 1
        return true
    }
}
