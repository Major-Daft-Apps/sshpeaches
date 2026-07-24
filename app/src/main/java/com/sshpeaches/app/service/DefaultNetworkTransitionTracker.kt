package com.majordaftapps.sshpeaches.app.service

internal enum class DefaultNetworkTransition {
    LOST,
    CHANGED,
    VPN_STATE_CHANGED
}

/**
 * Tracks the process default network without depending on Android framework types.
 *
 * A new default network means existing sockets are still bound to the old route, so callers
 * should disconnect them instead of allowing a stale SSH session to linger.
 */
internal class DefaultNetworkTransitionTracker<T> {

    private var currentNetwork: T? = null
    private var currentNetworkIsVpn: Boolean? = null

    @Synchronized
    fun seed(network: T?, isVpn: Boolean?) {
        currentNetwork = network
        currentNetworkIsVpn = isVpn
    }

    @Synchronized
    fun onAvailable(network: T, isVpn: Boolean?): DefaultNetworkTransition? {
        val previousNetwork = currentNetwork
        val previousNetworkIsVpn = currentNetworkIsVpn
        currentNetwork = network
        currentNetworkIsVpn = isVpn

        return when {
            previousNetwork == null -> null
            previousNetwork != network -> DefaultNetworkTransition.CHANGED
            previousNetworkIsVpn != null &&
                isVpn != null &&
                previousNetworkIsVpn != isVpn -> DefaultNetworkTransition.VPN_STATE_CHANGED
            else -> null
        }
    }

    @Synchronized
    fun onCapabilitiesChanged(network: T, isVpn: Boolean): DefaultNetworkTransition? {
        val previousNetwork = currentNetwork
        if (previousNetwork == null || previousNetwork != network) return null
        val previousNetworkIsVpn = currentNetworkIsVpn
        currentNetworkIsVpn = isVpn

        return when {
            previousNetworkIsVpn != null && previousNetworkIsVpn != isVpn ->
                DefaultNetworkTransition.VPN_STATE_CHANGED
            else -> null
        }
    }

    @Synchronized
    fun onLost(network: T): DefaultNetworkTransition? {
        if (currentNetwork != network) return null
        currentNetwork = null
        currentNetworkIsVpn = null
        return DefaultNetworkTransition.LOST
    }
}
