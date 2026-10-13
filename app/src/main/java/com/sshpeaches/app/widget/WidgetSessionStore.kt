package com.majordaftapps.sshpeaches.app.widget

import android.content.Context
import androidx.core.content.edit
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.service.SessionService
import org.json.JSONArray
import org.json.JSONObject

object WidgetSessionStore {
    data class WidgetOpenSession(
        val sessionId: String,
        val title: String,
        val subtitle: String,
        /** Picks the row's icon (terminal or file transfer), which tells two sessions on one host apart. */
        val mode: ConnectionMode = ConnectionMode.SSH
    )

    fun write(context: Context, snapshots: List<SessionService.SessionSnapshot>) {
        val payload = JSONArray()
        snapshots
            .forEach { snapshot ->
                // Just who and where; the row's icon shows whether it's a terminal or a file session.
                val endpoint = "${snapshot.host.username}@${snapshot.host.host}"
                val title = snapshot.host.name.ifBlank { endpoint }
                val subtitle = if (snapshot.host.name.isBlank()) "" else endpoint
                payload.put(
                    JSONObject()
                        .put("sessionId", snapshot.hostId)
                        .put("title", title)
                        .put("subtitle", subtitle)
                        .put("mode", snapshot.mode.name)
                )
            }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_OPEN_SESSIONS, payload.toString())
            }
    }

    fun read(context: Context): List<WidgetOpenSession> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_OPEN_SESSIONS, "[]")
            .orEmpty()
        return runCatching {
            val parsed = JSONArray(raw)
            buildList(parsed.length()) {
                for (index in 0 until parsed.length()) {
                    val item = parsed.optJSONObject(index) ?: continue
                    val sessionId = item.optString("sessionId")
                    if (sessionId.isBlank()) continue
                    add(
                        WidgetOpenSession(
                            sessionId = sessionId,
                            title = item.optString("title"),
                            subtitle = item.optString("subtitle"),
                            mode = runCatching { ConnectionMode.valueOf(item.optString("mode")) }
                                .getOrDefault(ConnectionMode.SSH)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private const val PREFS_NAME = "sshpeaches_widget_state"
    private const val KEY_OPEN_SESSIONS = "open_sessions"
}
