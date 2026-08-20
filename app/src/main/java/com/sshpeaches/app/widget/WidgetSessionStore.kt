package com.majordaftapps.sshpeaches.app.widget

import android.content.Context
import androidx.core.content.edit
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.service.SessionService
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
                val endpoint =
                    "${snapshot.host.username}@${snapshot.host.host}:${snapshot.host.port}"
                val title = snapshot.host.name.ifBlank { endpoint }
                val mode = when (snapshot.mode) {
                    ConnectionMode.SSH -> context.getString(R.string.widget_mode_terminal)
                    ConnectionMode.SFTP -> context.getString(R.string.widget_mode_file_browser)
                    ConnectionMode.SCP -> context.getString(R.string.widget_mode_file_transfer)
                }
                val status = when (snapshot.status) {
                    SessionService.SessionStatus.CONNECTING ->
                        context.getString(R.string.widget_status_connecting)
                    SessionService.SessionStatus.ACTIVE ->
                        context.getString(R.string.widget_status_active)
                    SessionService.SessionStatus.ERROR ->
                        context.getString(R.string.widget_status_error)
                }
                val subtitle = if (snapshot.host.name.isBlank()) {
                    context.getString(R.string.widget_session_subtitle, mode, status)
                } else {
                    context.getString(
                        R.string.widget_session_subtitle_with_endpoint,
                        mode,
                        status,
                        endpoint
                    )
                }
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
