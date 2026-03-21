package com.majordaftapps.sshpeaches.app.uptime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UptimeMonitorRunnerTest {
    @Test
    fun skipsRemoteHostChecksWhenNoActiveNetworkExists() {
        assertTrue(shouldSkipCheckForNoNetwork(host = "192.168.1.10", hasActiveNetwork = false))
        assertTrue(shouldSkipCheckForNoNetwork(host = "example.internal", hasActiveNetwork = false))
    }

    @Test
    fun allowsChecksWhenAnyActiveNetworkExists() {
        assertFalse(shouldSkipCheckForNoNetwork(host = "192.168.1.10", hasActiveNetwork = true))
    }

    @Test
    fun allowsLoopbackChecksWithoutActiveNetwork() {
        assertFalse(shouldSkipCheckForNoNetwork(host = "127.0.0.1", hasActiveNetwork = false))
        assertFalse(shouldSkipCheckForNoNetwork(host = "localhost", hasActiveNetwork = false))
        assertFalse(shouldSkipCheckForNoNetwork(host = "[::1]", hasActiveNetwork = false))
    }
}
