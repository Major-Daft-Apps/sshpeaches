package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencySamplesTest {
    @Test
    fun summarizesTheLastMinute() {
        val samples = LatencySamples(capacity = 4)
        listOf(500L, 40L, 50L, 30L, 60L).forEach(samples::add)
        val s = samples.summary("SSH")!!
        assertEquals(60L, s.lastMs)
        assertEquals("the oldest sample dropped", 30L, s.minMs)
        assertEquals(45L, s.avgMs)
        assertEquals(60L, s.maxMs)
        assertEquals(20L, s.jitterMs) // |50-40| + |30-50| + |60-30| = 60 over 3
        assertEquals(ConnectionLatency.Quality.GOOD, s.quality)
    }

    @Test
    fun anUnansweredProbeIsLost() {
        val samples = LatencySamples()
        samples.add(120L)
        samples.add(null)
        val s = samples.summary("Boost UDP")!!
        assertTrue(s.lost)
        assertNull(s.lastMs)
        assertEquals(120L, s.avgMs)
        assertEquals(ConnectionLatency.Quality.POOR, s.quality)
    }

    @Test
    fun quality() {
        fun q(ms: Long) = LatencySamples().apply { add(ms) }.summary("SSH")!!.quality
        assertEquals(ConnectionLatency.Quality.GOOD, q(99))
        assertEquals(ConnectionLatency.Quality.FAIR, q(150))
        assertEquals(ConnectionLatency.Quality.POOR, q(300))
        assertNull(LatencySamples().summary("SSH"))
    }
}
