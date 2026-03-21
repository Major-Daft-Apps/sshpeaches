package com.majordaftapps.sshpeaches.app.uptime

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSample
import com.majordaftapps.sshpeaches.app.data.model.UptimeBarBucketStatus
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UptimeSummaryCalculatorTest {

    @Test
    fun percentageForWindow_excludesUnverifiedChecks() {
        val config = HostUptimeConfig(
            hostId = "host-1",
            createdAt = 0L,
            intervalMinutes = 15
        )
        val samples = listOf(
            HostUptimeSample("host-1", 15L * 60L * 1000L, UptimeStatus.UP),
            HostUptimeSample("host-1", 30L * 60L * 1000L, UptimeStatus.UNVERIFIED),
            HostUptimeSample("host-1", 45L * 60L * 1000L, UptimeStatus.DOWN),
            HostUptimeSample("host-1", 60L * 60L * 1000L, UptimeStatus.UP)
        )

        val percent = UptimeSummaryCalculator.percentageForWindow(
            config = config,
            samples = samples,
            windowStart = 0L,
            windowEnd = 60L * 60L * 1000L
        )

        assertEquals(66.666, percent ?: 0.0, 0.5)
    }

    @Test
    fun synthesizeGapSamples_marksMissedChecksUnverified() {
        val config = HostUptimeConfig(
            hostId = "host-1",
            createdAt = 0L,
            intervalMinutes = 15
        )
        val samples = listOf(
            HostUptimeSample("host-1", 15L * 60L * 1000L, UptimeStatus.UP)
        )

        val synthesized = UptimeSummaryCalculator.synthesizeGapSamples(
            config = config,
            samples = samples,
            now = 60L * 60L * 1000L
        )

        assertTrue(synthesized.count { it.status == UptimeStatus.UNVERIFIED } >= 2)
    }

    @Test
    fun buildSummary_usesNoDataBeforeCreation() {
        val host = HostConnection(
            id = "host-1",
            name = "Prod",
            host = "prod.example.com",
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
        val config = HostUptimeConfig(
            hostId = host.id,
            createdAt = 12L * 60L * 60L * 1000L,
            intervalMinutes = 15
        )

        val summary = UptimeSummaryCalculator.buildSummary(
            host = host,
            config = config,
            samples = emptyList(),
            now = 24L * 60L * 60L * 1000L
        )

        assertEquals(UptimeBarBucketStatus.NO_DATA, summary.statusBars24h.first())
        assertNull(summary.uptime24hPercent)
    }
}
