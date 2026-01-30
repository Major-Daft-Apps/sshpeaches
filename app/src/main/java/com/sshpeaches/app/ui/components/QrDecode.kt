package com.sshpeaches.app.ui.components

import android.util.Base64
import org.json.JSONObject
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.model.Identity

fun decodeHostFromQr(contents: String): HostConnection? = runCatching {
    val json = JSONObject(String(Base64.decode(contents, Base64.NO_WRAP)))
    HostConnection(
        id = json.optString("id"),
        name = json.optString("name", json.optString("host")),
        host = json.optString("host"),
        port = json.optInt("port", 22),
        username = json.optString("user"),
        preferredAuth = AuthMethod.PASSWORD,
        defaultMode = ConnectionMode.SSH
    )
}.getOrNull()

fun decodeIdentityFromQr(contents: String): Identity? = runCatching {
    val json = JSONObject(String(Base64.decode(contents, Base64.NO_WRAP)))
    Identity(
        id = json.optString("id"),
        label = json.optString("label"),
        fingerprint = json.optString("fingerprint"),
        username = json.optString("user", null),
        createdEpochMillis = System.currentTimeMillis(),
        lastUsedEpochMillis = null
    )
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
