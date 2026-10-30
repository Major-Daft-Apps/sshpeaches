package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkIssueLimiterTest {
    @Test
    fun oneOfAKindPerWindowAndACapPerRun() {
        var now = 0L
        val limiter = NetworkIssueLimiter(perKeyMillis = 1_000, perRun = 3, clock = { now })
        assertTrue(limiter.allow("connection_lost|SocketException"))
        assertFalse("the same issue again at once", limiter.allow("connection_lost|SocketException"))
        assertTrue("another kind", limiter.allow("transfer_recovered|"))
        now += 1_000
        assertTrue("the first kind after its window", limiter.allow("connection_lost|SocketException"))
        now += 5_000
        assertFalse("the run's cap", limiter.allow("boost_download|IOException"))
    }
}
