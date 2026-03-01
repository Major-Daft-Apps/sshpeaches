package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.Snippet
import org.json.JSONObject
import java.util.Base64

fun encodeSnippetPayload(snippet: Snippet): String {
    val json = JSONObject().apply {
        put("id", snippet.id)
        put("title", snippet.title)
        put("description", snippet.description)
        put("command", snippet.command)
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}
