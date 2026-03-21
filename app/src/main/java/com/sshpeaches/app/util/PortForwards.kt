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

fun PortForward.inferredDestinationHost(selectedHost: HostConnection? = null): String {
    val inferredHost = selectedHost?.host?.trim().orEmpty()
    if (inferredHost.isNotBlank()) {
        return inferredHost
    }
    val storedHost = destinationHost.trim()
    return if (storedHost.isNotBlank()) storedHost else DEFAULT_FORWARD_DESTINATION_HOST
}
