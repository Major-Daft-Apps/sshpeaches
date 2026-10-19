package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import org.json.JSONArray
import org.json.JSONObject

/** What a full export holds, shown so the user can review it before anything is imported. */
data class TransferImportSummary(
    val hosts: Int,
    val identities: Int,
    val snippets: Int,
    val portForwards: Int,
    val terminalProfiles: Int,
    val hasSettings: Boolean,
    val hasKeyboardLayout: Boolean,
    val hasProtectedSecrets: Boolean
)

fun summarizeTransferPayload(root: JSONObject): TransferImportSummary {
    fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    val hosts = root.optJSONArray("hosts").objects()
    val identities = root.optJSONArray("identities").objects()
    val settings = root.optJSONObject("settings")
    return TransferImportSummary(
        hosts = hosts.size,
        identities = identities.size,
        snippets = root.optJSONArray("snippets").objects().size,
        portForwards = root.optJSONArray("portForwards").objects().size,
        terminalProfiles = settings?.optJSONArray("terminalProfiles").objects().size,
        hasSettings = settings != null,
        hasKeyboardLayout = settings?.optJSONArray("keyboardLayout") != null,
        hasProtectedSecrets = hosts.any { it.optString("pwdPayload").isNotBlank() } ||
            identities.any { it.optString("keyPayload").isNotBlank() || it.optString("keyPassphrasePayload").isNotBlank() }
    )
}

/**
 * The id an imported item is saved under. Saving replaces whatever has the same id, so an exported id that already
 * belongs to something here gets a new one: an import can add items but never overwrite one you have.
 */
fun importItemId(exportedId: String, localIds: Set<String>, newId: () -> String): String =
    exportedId.trim().takeIf { it.isNotEmpty() && it !in localIds } ?: newId()

/**
 * The id an imported host is saved under. Re-importing your own export updates the host (same id, same server); a
 * host that reuses an id but connects somewhere else is added as a new host instead, so an import can never point a
 * saved host, and the password saved for it, at a different server.
 */
fun importHostId(
    exportedId: String,
    host: String,
    port: Int,
    username: String,
    localHosts: List<HostConnection>,
    newId: () -> String
): String {
    val id = exportedId.trim()
    if (id.isEmpty()) return newId()
    val existing = localHosts.firstOrNull { it.id == id } ?: return id
    val sameServer = existing.host.trim().equals(host.trim(), ignoreCase = true) &&
        existing.port == port &&
        existing.username.trim() == username.trim()
    return if (sameServer) id else newId()
}

/** A name that doesn't pass for one already in [existingNames]: an added copy is marked "(imported)". */
fun importedItemName(name: String, existingNames: Collection<String>): String {
    val taken = existingNames.map { it.trim().lowercase() }.toSet()
    if (name.trim().lowercase() !in taken) return name
    val base = "$name (imported)"
    if (base.lowercase() !in taken) return base
    var index = 2
    while ("$name (imported $index)".lowercase() in taken) index++
    return "$name (imported $index)"
}
