package com.majordaftapps.sshpeaches.app.ui.components

import org.json.JSONObject
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import java.util.Base64

data class HostQrPayload(
    val host: HostConnection,
    val encryptedPasswordPayload: String?,
    val legacyPassword: String?
)

data class IdentityQrPayload(
    val identity: Identity,
    val encryptedKeyPayload: String?
)

fun decodeHostFromQr(contents: String): HostQrPayload? = runCatching {
    val raw = String(Base64.getDecoder().decode(contents), Charsets.UTF_8)
    val json = JSONObject(raw)
    val authName = json.optString("prefAuth", AuthMethod.PASSWORD.name)
    val modeName = json.optString("mode", ConnectionMode.SSH.name)
    val host = HostConnection(
        id = json.optString("id"),
        name = json.optString("name", json.optString("host")),
        host = json.optString("host"),
        port = json.optInt("port", 22),
        username = json.optString("user"),
        preferredAuth = runCatching { AuthMethod.valueOf(authName) }.getOrDefault(AuthMethod.PASSWORD),
        defaultMode = runCatching { ConnectionMode.valueOf(modeName) }.getOrDefault(ConnectionMode.SSH),
        group = json.optString("group").takeIf { it.isNotBlank() },
        notes = json.optString("notes", ""),
        hasPassword = json.optBoolean("hasPassword", false),
        useMosh = json.optBoolean("useMosh", false),
        preferredIdentityId = json.optString("preferredIdentityId").takeIf { it.isNotBlank() },
        preferredForwardId = json.optString("preferredForwardId").takeIf { it.isNotBlank() },
        startupScript = json.optString("startupScript", ""),
        backgroundBehavior = runCatching {
            BackgroundBehavior.valueOf(json.optString("backgroundBehavior", BackgroundBehavior.INHERIT.name))
        }.getOrDefault(BackgroundBehavior.INHERIT),
        terminalProfileId = json.optString("terminalProfileId").takeIf { it.isNotBlank() }
    )
    val encrypted = json.optString("pwdPayload").takeIf { it.isNotBlank() }
    val legacy = json.optString("pwd").takeIf { it.isNotBlank() }?.let { encoded ->
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
    HostQrPayload(host, encrypted, legacy)
}.getOrNull()

fun decodeIdentityFromQr(contents: String): IdentityQrPayload? = runCatching {
    val json = JSONObject(String(Base64.getDecoder().decode(contents), Charsets.UTF_8))
    val identity = Identity(
        id = json.optString("id"),
        label = json.optString("label"),
        fingerprint = json.optString("fingerprint"),
        username = json.optString("user").takeIf { it.isNotBlank() },
        createdEpochMillis = System.currentTimeMillis(),
        lastUsedEpochMillis = null,
        hasPrivateKey = json.optBoolean("hasKey", false)
    )
    val keyPayload = json.optString("keyPayload").takeIf { it.isNotBlank() }
    IdentityQrPayload(identity, keyPayload)
}.getOrNull()

fun decodeForwardFromQr(contents: String): PortForward? = runCatching {
    val json = JSONObject(String(Base64.getDecoder().decode(contents), Charsets.UTF_8))
    val type = PortForwardType.valueOf(json.optString("type", "LOCAL"))
    PortForward(
        id = json.optString("id"),
        label = json.optString("label", "QR Forward"),
        type = type,
        sourceHost = json.optString("bind", "127.0.0.1"),
        sourcePort = json.optInt("srcPort", 0),
        destinationHost = json.optString("dstHost", ""),
        destinationPort = json.optInt("dstPort", 0),
        associatedHosts = emptyList(),
        enabled = false
    )
}.getOrNull()

fun decodeSnippetFromQr(contents: String): Snippet? = runCatching {
    val json = JSONObject(String(Base64.getDecoder().decode(contents), Charsets.UTF_8))
    Snippet(
        id = json.optString("id"),
        title = json.optString("title", "Snippet"),
        description = json.optString("description", ""),
        command = json.optString("command", "")
    )
}.getOrNull()
