package com.majordaftapps.sshpeaches.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for org.json, which the plain unit-test android.jar only stubs.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceMetricsTest {
    private class MemoryStore : PerformanceMetrics.Store {
        var json: String? = null
        override fun load() = json
        override fun save(json: String?) { this.json = json }
    }

    @Test
    fun nothingIsCollectedWhileUsageReportsAreOff() {
        val store = MemoryStore()
        val metrics = PerformanceMetrics(store)
        metrics.recordConnect("terminal", 900, reconnect = false)
        assertNull(store.json)
        assertEquals(0, metrics.snapshot().getJSONObject("counts").length())
    }

    @Test
    fun summarizesWithoutAnythingIdentifying() {
        val metrics = PerformanceMetrics(MemoryStore()).apply { enabled = true }
        (1..10).forEach { metrics.recordConnect("terminal", it * 100L, reconnect = false) }
        metrics.recordBoost("terminal", "udp")
        metrics.recordBoost("files", PerformanceMetrics.boostReason("the server's firewall doesn't let UDP through"))
        metrics.recordTransfer("download", "boost", "succeeded", 64L shl 20, 2_000)
        metrics.recordTransfer("download", "sftp", "succeeded", 10_000, 50) // too small to time a speed
        metrics.recordListing(20_000, 900)
        metrics.recordLatency("Boost UDP", 42)
        val report = metrics.snapshot()
        val counts = report.getJSONObject("counts")
        assertEquals(1, counts.getInt("boost.terminal.udp"))
        assertEquals(1, counts.getInt("boost.files.blocked_udp"))
        assertEquals(64, counts.getInt("transfer.download.boost.MB"))
        val samples = report.getJSONObject("samples")
        val connect = samples.getJSONObject("connect.terminal.ms")
        assertEquals(10, connect.getInt("n"))
        assertEquals(600, connect.getInt("p50"))
        assertEquals(1000, connect.getInt("p90"))
        assertEquals(32768, samples.getJSONObject("transfer.download.boost.kBps").getInt("p50"))
        assertFalse(samples.has("transfer.download.sftp.kBps"))
        assertTrue(samples.has("listing.large.ms"))
        assertTrue(samples.has("latency.boost_udp.ms"))
    }

    @Test
    fun survivesARestartAndClearsAfterAReport() {
        val store = MemoryStore()
        PerformanceMetrics(store).apply { enabled = true }.recordConnect("browser", 700, reconnect = true)
        val again = PerformanceMetrics(store).apply { enabled = true }
        assertEquals(1, again.snapshot().getJSONObject("samples").getJSONObject("connect.browser.reconnect.ms").getInt("n"))
        again.reset()
        assertNull(store.json)
        assertEquals(0, again.snapshot().getJSONObject("samples").length())
    }

    @Test
    fun turningUsageReportsOffDropsWhatWasCollected() {
        val store = MemoryStore()
        val metrics = PerformanceMetrics(store).apply { enabled = true }
        metrics.recordListing(5, 30)
        metrics.enabled = false
        assertNull(store.json)
        assertEquals(0, metrics.snapshot().getJSONObject("samples").length())
    }

    @Test
    fun boostReasonsAreCategories() {
        assertEquals("blocked_udp_tcp", PerformanceMetrics.boostReason("the server's firewall lets neither UDP nor TCP through to the helper"))
        assertEquals("not_linux", PerformanceMetrics.boostReason("the server isn't Linux (Darwin arm64)"))
        assertEquals("other", PerformanceMetrics.boostReason("something /home/me/secret happened"))
    }
}
