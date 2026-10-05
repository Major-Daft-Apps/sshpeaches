package com.majordaftapps.sshpeaches.app.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** A phone on this network that is offering an SSHPeaches export. */
data class LanTransferPeer(val name: String, val host: String, val port: Int)

/** Announces a running [LanTransfer.Sender] on the local network so receivers can find it. */
class LanTransferAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(port: Int) {
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "SSHPeaches on ${Build.MODEL}"
            serviceType = LanTransfer.SERVICE_TYPE
            this.port = port
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            .onSuccess { listener = registration }
    }

    fun stop() {
        val registration = listener ?: return
        listener = null
        runCatching { nsd?.unregisterService(registration) }
    }
}

/** Lists senders on the local network. Discovery is best effort; scanning the QR always works. */
class LanTransferBrowser(context: Context, private val onPeersChanged: (List<LanTransferPeer>) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val peers = linkedMapOf<String, LanTransferPeer>()
    private val pendingResolves = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private var discovery: NsdManager.DiscoveryListener? = null

    fun start() {
        val manager = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pendingResolves += serviceInfo
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(peers) { peers.remove(serviceInfo.serviceName) }
                publish()
            }
        }
        runCatching { manager.discoverServices(LanTransfer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { discovery = listener }
    }

    fun stop() {
        val listener = discovery ?: return
        discovery = null
        runCatching { nsd?.stopServiceDiscovery(listener) }
    }

    // NsdManager resolves one service at a time.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val manager = nsd ?: return
        if (!resolving.compareAndSet(false, true)) return
        val next = pendingResolves.poll()
        if (next == null) {
            resolving.set(false)
            return
        }
        manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.set(false)
                resolveNext()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val address = serviceInfo.host
                if (address != null && LanTransfer.isLocalNetworkAddress(address)) {
                    synchronized(peers) {
                        peers[serviceInfo.serviceName] = LanTransferPeer(
                            name = serviceInfo.serviceName,
                            host = address.hostAddress.orEmpty(),
                            port = serviceInfo.port
                        )
                    }
                    publish()
                }
                resolving.set(false)
                resolveNext()
            }
        })
    }

    private fun publish() {
        onPeersChanged(synchronized(peers) { peers.values.toList() })
    }
}
