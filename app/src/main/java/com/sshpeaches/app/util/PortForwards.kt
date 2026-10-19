package com.majordaftapps.sshpeaches.app.util

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward

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
