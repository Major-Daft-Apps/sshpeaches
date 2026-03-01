package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import org.json.JSONObject
import java.util.Base64

fun encodeHostPayload(
    host: HostConnection,
    encryptedPasswordPayload: String?,
    legacyPassword: String? = null
): String {
    val json = JSONObject().apply {
        put("id", host.id)
        put("name", host.name)
        put("host", host.host)
        put("port", host.port)
        put("user", host.username)
        put("prefAuth", host.preferredAuth.name)
        put("mode", host.defaultMode.name)
        put("group", host.group ?: "")
        put("notes", host.notes)
        put("hasPassword", host.hasPassword)
        put("useMosh", host.useMosh)
        put("preferredIdentityId", host.preferredIdentityId ?: "")
        put("preferredForwardId", host.preferredForwardId ?: "")
        put("startupScript", host.startupScript)
        put("backgroundBehavior", host.backgroundBehavior.name)
        put("terminalProfileId", host.terminalProfileId ?: "")
        encryptedPasswordPayload?.let { put("pwdPayload", it) }
        legacyPassword?.let {
            put("pwd", Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)))
        }
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}
