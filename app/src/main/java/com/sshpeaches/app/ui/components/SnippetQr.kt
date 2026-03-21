package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.Snippet
import org.json.JSONObject
import java.util.Base64

fun encodeSnippetPayload(snippet: Snippet): String {
    val json = JSONObject().apply {
        put("id", snippet.id)
        put("title", snippet.title)
        snippet.group?.let { put("group", it) }
        put("createdEpochMillis", snippet.createdEpochMillis ?: JSONObject.NULL)
        put("updatedEpochMillis", snippet.updatedEpochMillis ?: JSONObject.NULL)
        put("lastUsedEpochMillis", snippet.lastUsedEpochMillis ?: JSONObject.NULL)
        put("description", snippet.description)
        put("command", snippet.command)
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}
