package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import java.util.UUID

data class HostQrImportData(
    val host: HostConnection,
    val targetId: String,
    val encryptedPasswordPayload: String?
)

sealed interface HostQrImportResult {
    data class Ready(val data: HostQrImportData) : HostQrImportResult
    data class Error(val message: String) : HostQrImportResult
}

fun processHostQrImport(
    contents: String,
    existingHosts: List<HostConnection>,
    idProvider: () -> String = { UUID.randomUUID().toString() }
): HostQrImportResult {
    val payload = decodeHostFromQr(contents)
    val imported = payload?.host
    if (imported == null || imported.host.isBlank() || imported.username.isBlank()) {
        return HostQrImportResult.Error("Invalid host QR")
    }
    if (existingHosts.any { it.name.equals(imported.name, ignoreCase = true) }) {
        return HostQrImportResult.Error("Host already exists")
    }
    val targetId = imported.id.takeIf { it.isNotBlank() } ?: idProvider()
    return HostQrImportResult.Ready(
        HostQrImportData(
            host = imported,
            targetId = targetId,
            encryptedPasswordPayload = payload.encryptedPasswordPayload
        )
    )
}

data class IdentityQrOverwriteData(
    val targetId: String,
    val label: String,
    val fingerprint: String,
    val username: String?,
    val group: String?,
    val encryptedKeyPayload: String?
)

data class IdentityQrImportData(
    val identity: Identity,
    val encryptedKeyPayload: String?
)

sealed interface IdentityQrImportResult {
    data class Ready(val data: IdentityQrImportData) : IdentityQrImportResult
    data class NeedsOverwrite(val overwrite: IdentityQrOverwriteData) : IdentityQrImportResult
    data class Error(val message: String) : IdentityQrImportResult
}

fun processIdentityQrImport(
    contents: String,
    existingIdentities: List<Identity>,
    nowProvider: () -> Long = { System.currentTimeMillis() },
    idProvider: () -> String = { UUID.randomUUID().toString() }
): IdentityQrImportResult {
    val payload = decodeIdentityFromQr(contents)
    val imported = payload?.identity
    if (imported == null) {
        return IdentityQrImportResult.Error("Invalid identity QR")
    }
    val existing = existingIdentities.find { it.fingerprint.equals(imported.fingerprint, ignoreCase = true) }
    if (existing != null) {
        return IdentityQrImportResult.NeedsOverwrite(
            IdentityQrOverwriteData(
                targetId = existing.id,
                label = imported.label.ifBlank { existing.label },
                fingerprint = imported.fingerprint,
                username = imported.username ?: existing.username,
                group = imported.group ?: existing.group,
                encryptedKeyPayload = payload.encryptedKeyPayload
            )
        )
    }
    val targetId = imported.id.takeIf { it.isNotBlank() } ?: idProvider()
    return IdentityQrImportResult.Ready(
        IdentityQrImportData(
            identity = imported.copy(
                id = targetId,
                label = imported.label.ifBlank { "Imported Identity" },
                createdEpochMillis = nowProvider()
            ),
            encryptedKeyPayload = payload.encryptedKeyPayload
        )
    )
}

sealed interface PortForwardQrImportResult {
    data class Ready(val forward: PortForward) : PortForwardQrImportResult
    data class Error(val message: String) : PortForwardQrImportResult
}

fun processPortForwardQrImport(contents: String): PortForwardQrImportResult {
    val incomingType = decodeForwardTypeFromQr(contents) ?: return PortForwardQrImportResult.Error("Invalid port forward QR")
    if (incomingType.isNotBlank() && incomingType != PortForwardType.LOCAL.name) {
        return PortForwardQrImportResult.Error("Only Local forwarding is supported.")
    }
    val imported = decodeForwardFromQr(contents)
    return if (imported == null) {
        PortForwardQrImportResult.Error("Invalid port forward QR")
    } else {
        PortForwardQrImportResult.Ready(imported)
    }
}

data class SnippetQrImportData(
    val title: String,
    val group: String?,
    val description: String,
    val command: String
)

sealed interface SnippetQrImportResult {
    data class Ready(val data: SnippetQrImportData) : SnippetQrImportResult
    data class Error(val message: String) : SnippetQrImportResult
}

fun processSnippetQrImport(
    contents: String,
    idProvider: () -> String = { UUID.randomUUID().toString() }
): SnippetQrImportResult {
    val imported = decodeSnippetFromQr(contents)
    if (imported == null || imported.command.isBlank()) {
        return SnippetQrImportResult.Error("Invalid snippet QR")
    }
    return SnippetQrImportResult.Ready(
        SnippetQrImportData(
            title = imported.title.ifBlank { "Snippet ${idProvider()}" },
            group = imported.group,
            description = imported.description,
            command = imported.command
        )
    )
}
