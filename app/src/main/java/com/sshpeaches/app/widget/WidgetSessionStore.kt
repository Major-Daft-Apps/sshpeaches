package com.majordaftapps.sshpeaches.app.widget

import android.content.Context
import androidx.core.content.edit
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.state.userFacingLabel
import org.json.JSONArray
import org.json.JSONObject

object WidgetSessionStore {
    data class WidgetOpenSession(
        val sessionId: String,
        val title: String,
        val subtitle: String
    )

    fun write(context: Context, snapshots: List<SessionService.SessionSnapshot>) {
        val payload = JSONArray()
        snapshots
            .forEach { snapshot ->
            val title = snapshot.host.name.ifBlank {
                "${snapshot.host.username}@${snapshot.host.host}:${snapshot.host.port}"
            }
            val subtitle =
                "${snapshot.mode.userFacingLabel()} - ${snapshot.status.name.lowercase().replaceFirstChar { it.uppercase() }}"
            payload.put(
                JSONObject()
                    .put("sessionId", snapshot.hostId)
                    .put("title", title)
                    .put("subtitle", subtitle)
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
                            subtitle = item.optString("subtitle")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private const val PREFS_NAME = "sshpeaches_widget_state"
    private const val KEY_OPEN_SESSIONS = "open_sessions"
}
