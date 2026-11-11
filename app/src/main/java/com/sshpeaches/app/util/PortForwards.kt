package com.majordaftapps.sshpeaches.app.util

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType

private const val DEFAULT_FORWARD_DESTINATION_HOST = "127.0.0.1"

fun normalizeAssociatedHostIds(hostIds: List<String>): List<String> =
    hostIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .take(1)

fun PortForward.selectedHostId(): String? = normalizeAssociatedHostIds(associatedHosts).firstOrNull()

fun PortForward.inferredDestinationHost(): String {
    val storedHost = destinationHost.trim()
    return if (storedHost.isNotBlank()) storedHost else DEFAULT_FORWARD_DESTINATION_HOST
}

fun PortForward.legacyLoopbackDestinationHost(selectedHost: HostConnection?): String? {
    val storedHost = destinationHost.trim()
    val associatedHost = selectedHost?.host?.trim().orEmpty()
    if (storedHost.isBlank() || associatedHost.isBlank()) return null
    return if (storedHost.equals(associatedHost, ignoreCase = true)) {
        DEFAULT_FORWARD_DESTINATION_HOST
    } else {
        null
    }
}

/** Whether a forward bound to [bind] only accepts connections from this phone. */
fun isLoopbackBindAddress(bind: String): Boolean {
    val value = bind.trim().removePrefix("[").removeSuffix("]").lowercase()
    return value.isEmpty() || value == "localhost" || value == "::1" || value.startsWith("127.")
}

/** "Local", "Remote" or "Dynamic", as shown in the app. */
fun PortForwardType.displayName(): String = when (this) {
    PortForwardType.LOCAL -> "Local"
    PortForwardType.REMOTE -> "Remote"
    PortForwardType.DYNAMIC -> "Dynamic"
}

/** One line saying where the forward listens and where it leads. */
fun PortForward.summary(): String = when (type) {
    PortForwardType.LOCAL -> "$sourceHost:$sourcePort \u2192 ${inferredDestinationHost()}:$destinationPort"
    PortForwardType.REMOTE -> "Server $sourceHost:$sourcePort \u2192 ${inferredDestinationHost()}:$destinationPort"
    PortForwardType.DYNAMIC -> "SOCKS proxy on $sourceHost:$sourcePort"
}

/** The server's reply when asked to listen on a privileged port as an ordinary user, and similar refusals. */
const val REMOTE_FORWARD_REFUSED_HINT =
    "The server refused to listen there. Check AllowTcpForwarding in its sshd_config, and that the port is free and above 1023."
