package com.sshpeaches.app.ui.components

import org.json.JSONObject
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import android.util.Base64
import com.sshpeaches.app.data.model.Identity

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
    val raw = String(Base64.decode(contents, Base64.NO_WRAP))
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
        hasPassword = json.optBoolean("hasPassword", false)
    )
    val encrypted = json.optString("pwdPayload").takeIf { it.isNotBlank() }
    val legacy = json.optString("pwd").takeIf { it.isNotBlank() }?.let { encoded ->
        String(Base64.decode(encoded, Base64.NO_WRAP))
    }
    HostQrPayload(host, encrypted, legacy)
}.getOrNull()

fun decodeIdentityFromQr(contents: String): IdentityQrPayload? = runCatching {
    val json = JSONObject(String(Base64.decode(contents, Base64.NO_WRAP)))
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
    val json = JSONObject(String(Base64.decode(contents, Base64.NO_WRAP)))
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
