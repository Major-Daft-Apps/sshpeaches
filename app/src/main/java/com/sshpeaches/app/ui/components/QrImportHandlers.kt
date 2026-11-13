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
    if (
        imported == null ||
        imported.host.isBlank() ||
        imported.username.isBlank() ||
        imported.port !in 1..65_535
    ) {
        return HostQrImportResult.Error("Invalid host QR")
    }
    // Names are saved trimmed, so "Prod " would otherwise become a second "Prod" pointing somewhere else.
    val importedName = normalizedHostName(imported.name)
    if (existingHosts.any { normalizedHostName(it.name).equals(importedName, ignoreCase = true) }) {
        return HostQrImportResult.Error("Host already exists")
    }
    // Saving replaces a host with the same id, so a QR whose id is already taken (e.g. by a host renamed since it
    // was shared) gets a new one instead of overwriting that host.
    val targetId = imported.id
        .takeIf { id -> id.isNotBlank() && existingHosts.none { it.id == id } }
        ?: idProvider()
    return HostQrImportResult.Ready(
        HostQrImportData(
            // A scanned QR code can come from anyone: a startup command would run on the server at every connect
            // without being seen, identity/forward ids would attach this host to your own keys and forwards, and
            // the legacy-algorithms switch would quietly allow SHA-1 and CBC. Turn that on yourself if the host needs it.
            host = imported.copy(
                startupScript = "",
                preferredIdentityId = null,
                preferredForwardId = null,
                allowLegacyAlgorithms = false
            ),
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
    if (imported == null || imported.fingerprint.isBlank()) {
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
    val targetId = imported.id
        .takeIf { id -> id.isNotBlank() && existingIdentities.none { it.id == id } }
        ?: idProvider()
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

/**
 * Whether a forward of this [type] may come in from a scanned QR code, be it a single forward's code or a whole
 * export's. A remote forward opens something this phone can reach to the server: too much to take from a scan.
 */
fun scannedCodeMayBringForward(type: PortForwardType): Boolean = type != PortForwardType.REMOTE

fun processPortForwardQrImport(contents: String): PortForwardQrImportResult {
    val incomingType = decodeForwardTypeFromQr(contents) ?: return PortForwardQrImportResult.Error("Invalid port forward QR")
    val type = runCatching { PortForwardType.valueOf(incomingType.ifBlank { PortForwardType.LOCAL.name }) }.getOrNull()
        ?: return PortForwardQrImportResult.Error("This kind of port forward isn't supported.")
    if (!scannedCodeMayBringForward(type)) {
        return PortForwardQrImportResult.Error("Remote forwards can't be imported from a QR code. Add it by hand.")
    }
    val imported = decodeForwardFromQr(contents)
    return if (imported == null) {
        PortForwardQrImportResult.Error("Invalid port forward QR")
    } else {
        // Like every import, a scanned forward only listens on this phone; the user can widen it when saving.
        val dynamic = type == PortForwardType.DYNAMIC
        PortForwardQrImportResult.Ready(
            imported.copy(
                type = type,
                sourceHost = "127.0.0.1",
                destinationHost = if (dynamic) "" else imported.destinationHost,
                destinationPort = if (dynamic) 0 else imported.destinationPort
            )
        )
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

/** A host name as it's compared for duplicates: trimmed, runs of whitespace as one space. */
internal fun normalizedHostName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
