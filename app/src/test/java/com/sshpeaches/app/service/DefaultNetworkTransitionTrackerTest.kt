package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultNetworkTransitionTrackerTest {

    @Test
    fun initialDefaultNetworkCallbackDoesNotDisconnect() {
        val tracker = DefaultNetworkTransitionTracker<String>()
        tracker.seed(network = "wifi", isVpn = false)

        assertNull(tracker.onAvailable(network = "wifi", isVpn = false))
        assertNull(tracker.onCapabilitiesChanged(network = "wifi", isVpn = false))
    }

    @Test
    fun losingTheCurrentDefaultNetworkDisconnectsOnce() {
        val tracker = DefaultNetworkTransitionTracker<String>()
        tracker.seed(network = "wifi", isVpn = false)

        assertEquals(DefaultNetworkTransition.LOST, tracker.onLost("wifi"))
        assertNull(tracker.onLost("wifi"))
        assertNull(tracker.onAvailable(network = "cellular", isVpn = false))
    }

    @Test
    fun changingTheDefaultNetworkDisconnects() {
        val tracker = DefaultNetworkTransitionTracker<String>()
        tracker.seed(network = "wifi", isVpn = false)

        assertEquals(
            DefaultNetworkTransition.CHANGED,
            tracker.onAvailable(network = "cellular", isVpn = false)
        )
        assertNull(tracker.onLost("wifi"))
    }

    @Test
    fun changingVpnStateOnTheCurrentNetworkDisconnects() {
        val tracker = DefaultNetworkTransitionTracker<String>()
        tracker.seed(network = "default", isVpn = false)

        assertEquals(
            DefaultNetworkTransition.VPN_STATE_CHANGED,
            tracker.onCapabilitiesChanged(network = "default", isVpn = true)
        )
        assertNull(tracker.onCapabilitiesChanged(network = "default", isVpn = true))
    }

    @Test
    fun staleCapabilitiesFromAnOldNetworkDoNotReplaceTheCurrentDefault() {
        val tracker = DefaultNetworkTransitionTracker<String>()
        tracker.seed(network = "wifi", isVpn = false)
        assertEquals(
            DefaultNetworkTransition.CHANGED,
            tracker.onAvailable(network = "vpn", isVpn = true)
        )

        assertNull(tracker.onCapabilitiesChanged(network = "wifi", isVpn = false))
        assertEquals(DefaultNetworkTransition.LOST, tracker.onLost("vpn"))
    }
}
