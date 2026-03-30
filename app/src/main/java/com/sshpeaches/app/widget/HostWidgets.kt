package com.majordaftapps.sshpeaches.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

internal object HostWidgets {
    const val ACTION_WIDGET_CONNECT = "com.majordaftapps.sshpeaches.app.widget.ACTION_CONNECT"
    const val ACTION_WIDGET_OPEN = "com.majordaftapps.sshpeaches.app.widget.ACTION_OPEN"
    const val ACTION_WIDGET_DISCONNECT = "com.majordaftapps.sshpeaches.app.widget.ACTION_DISCONNECT"
    const val EXTRA_HOST_ID = "extra_widget_host_id"
    const val EXTRA_MODE = "extra_widget_mode"
    const val EXTRA_FILE_TRANSFER_ENTRY_MODE = "extra_widget_file_transfer_entry_mode"
    const val EXTRA_SESSION_ID = "extra_widget_session_id"
    private const val EXTRA_ACTION_TOKEN = "extra_widget_action_token"
    private const val PREFS_NAME = "sshpeaches_widget_security"
    private const val KEY_ACTION_TOKEN = "widget_action_token"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val quickComponent = ComponentName(context, QuickConnectWidgetProvider::class.java)
        val sessionsComponent = ComponentName(context, SessionsWidgetProvider::class.java)
        val quickIds = manager.getAppWidgetIds(quickComponent)
        val sessionsIds = manager.getAppWidgetIds(sessionsComponent)
        val hosts = loadHosts(context)
        val openSessions = WidgetSessionStore.read(context)
        if (quickIds.isNotEmpty()) {
            quickIds.forEach { id ->
                manager.updateAppWidget(id, buildQuickWidgetRemoteViews(context, hosts, openSessions))
            }
        }
        if (sessionsIds.isNotEmpty()) {
            sessionsIds.forEach { id ->
                manager.updateAppWidget(id, buildSessionsWidgetRemoteViews(context, hosts, openSessions))
            }
        }
    }

    private fun loadHosts(context: Context): List<HostConnection> {
        val app = context.applicationContext as? SSHPeachesApplication ?: return emptyList()
        return runCatching {
            runBlocking {
                app.container.repository.hosts.first()
            }
        }.getOrDefault(emptyList())
            .sortedWith(
                compareByDescending<HostConnection> {
                    it.lastUsedEpochMillis ?: it.updatedEpochMillis ?: it.createdEpochMillis ?: 0L
                }.thenBy { it.name.ifBlank { it.host }.lowercase() }
            )
    }

    private fun buildQuickWidgetRemoteViews(
        context: Context,
        hosts: List<HostConnection>,
        openSessions: List<WidgetSessionStore.WidgetOpenSession>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)
        views.removeAllViews(R.id.widget_open_sessions_container)
        views.removeAllViews(R.id.widget_hosts_container)
        val visibleSessions = openSessions.take(MAX_OPEN_SESSION_ROWS_COMPACT)
        if (visibleSessions.isEmpty()) {
            views.setViewVisibility(R.id.widget_open_sessions_section, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_open_sessions_section, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_open_sessions_empty, android.view.View.GONE)
            visibleSessions.forEach { openSession ->
                views.addView(
                    R.id.widget_open_sessions_container,
                    buildOpenSessionRow(context, openSession)
                )
            }
        }

        val visibleHosts = hosts.take(
            if (visibleSessions.isEmpty()) MAX_RECENT_HOST_ROWS_COMPACT_EMPTY else MAX_RECENT_HOST_ROWS_COMPACT
        )
        when {
            visibleHosts.isEmpty() && visibleSessions.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.VISIBLE)
            }
            visibleHosts.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.GONE)
            }
            else -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.GONE)
                visibleHosts.forEach { host ->
                    views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
                }
            }
        }
        return views
    }

    private fun buildSessionsWidgetRemoteViews(
        context: Context,
        hosts: List<HostConnection>,
        openSessions: List<WidgetSessionStore.WidgetOpenSession>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sessions)
        views.removeAllViews(R.id.widget_hosts_container)
        views.removeAllViews(R.id.widget_open_sessions_container)
        val visibleHosts = hosts.take(
            if (openSessions.isEmpty()) MAX_RECENT_HOST_ROWS_EXPANDED_EMPTY else MAX_RECENT_HOST_ROWS_EXPANDED
        )
        when {
            visibleHosts.isEmpty() && openSessions.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.VISIBLE)
            }
            visibleHosts.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.GONE)
            }
            else -> {
                views.setViewVisibility(R.id.widget_hosts_section, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.GONE)
                visibleHosts.forEach { host ->
                    views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
                }
            }
        }
        if (openSessions.isEmpty()) {
            views.setViewVisibility(R.id.widget_open_sessions_empty, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_open_sessions_empty, android.view.View.GONE)
            openSessions.take(MAX_OPEN_SESSION_ROWS_EXPANDED).forEach { openSession ->
                views.addView(
                    R.id.widget_open_sessions_container,
                    buildOpenSessionRow(context, openSession)
                )
            }
        }
        return views
    }

    private fun buildHostRow(context: Context, host: HostConnection): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_host_row)
        val title = host.name.ifBlank { "${host.username}@${host.host}:${host.port}" }
        row.setTextViewText(R.id.widget_host_title, title)
        row.setOnClickPendingIntent(
            R.id.widget_btn_ssh,
            createConnectPendingIntent(context, host.id, ConnectionMode.SSH)
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_sftp,
            createConnectPendingIntent(
                context,
                host.id,
                ConnectionMode.SCP,
                FileTransferEntryMode.UPLOAD
            )
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_scp,
            createConnectPendingIntent(
                context,
                host.id,
                ConnectionMode.SCP,
                FileTransferEntryMode.DOWNLOAD
            )
        )
        return row
    }

    private fun buildOpenSessionRow(
        context: Context,
        session: WidgetSessionStore.WidgetOpenSession
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_open_session_row)
        row.setTextViewText(R.id.widget_open_session_title, session.title)
        row.setOnClickPendingIntent(
            R.id.widget_btn_open,
            createOpenPendingIntent(context, session.sessionId)
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_disconnect,
            createDisconnectPendingIntent(context, session.sessionId)
        )
        return row
    }

    private fun createConnectPendingIntent(
        context: Context,
        hostId: String,
        mode: ConnectionMode,
        fileTransferEntryMode: FileTransferEntryMode? = null
    ): PendingIntent {
        val intent = Intent(context, QuickConnectWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CONNECT
            putExtra(EXTRA_HOST_ID, hostId)
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_FILE_TRANSFER_ENTRY_MODE, fileTransferEntryMode?.name)
            putExtra(EXTRA_ACTION_TOKEN, actionToken(context))
        }
        return PendingIntent.getBroadcast(
            context,
            "connect:$hostId:${mode.name}:${fileTransferEntryMode?.name.orEmpty()}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenPendingIntent(
        context: Context,
        sessionId: String
    ): PendingIntent {
        val intent = Intent(context, QuickConnectWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_OPEN
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_ACTION_TOKEN, actionToken(context))
        }
        return PendingIntent.getBroadcast(
            context,
            "open:$sessionId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDisconnectPendingIntent(
        context: Context,
        sessionId: String
    ): PendingIntent {
        val intent = Intent(context, QuickConnectWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_DISCONNECT
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_ACTION_TOKEN, actionToken(context))
        }
        return PendingIntent.getBroadcast(
            context,
            "disconnect:$sessionId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isTrustedWidgetActionIntent(context: Context, intent: Intent): Boolean {
        val expected = actionToken(context)
        val actual = intent.getStringExtra(EXTRA_ACTION_TOKEN)
        return !actual.isNullOrBlank() && actual == expected
    }

    private fun actionToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_ACTION_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit {
            putString(KEY_ACTION_TOKEN, generated)
        }
        return generated
    }

    private const val MAX_OPEN_SESSION_ROWS_COMPACT = 1
    private const val MAX_RECENT_HOST_ROWS_COMPACT = 1
    private const val MAX_RECENT_HOST_ROWS_COMPACT_EMPTY = 2
    private const val MAX_RECENT_HOST_ROWS_EXPANDED = 2
    private const val MAX_RECENT_HOST_ROWS_EXPANDED_EMPTY = 3
    private const val MAX_OPEN_SESSION_ROWS_EXPANDED = 3
}

abstract class BaseHostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, javaClass)
        val ids = manager.getAppWidgetIds(componentName)
        if (ids.isNotEmpty()) {
            HostWidgets.updateAll(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == HostWidgets.ACTION_WIDGET_CONNECT ||
            intent.action == HostWidgets.ACTION_WIDGET_OPEN ||
            intent.action == HostWidgets.ACTION_WIDGET_DISCONNECT
        ) {
            if (!HostWidgets.isTrustedWidgetActionIntent(context, intent)) {
                return
            }
        }
        when (intent.action) {
            HostWidgets.ACTION_WIDGET_CONNECT -> {
                val hostId = intent.getStringExtra(HostWidgets.EXTRA_HOST_ID).orEmpty()
                val mode = intent.getStringExtra(HostWidgets.EXTRA_MODE).orEmpty()
                if (hostId.isNotBlank()) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_WIDGET_CONNECT
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_WIDGET_HOST_ID, hostId)
                        putExtra(MainActivity.EXTRA_WIDGET_MODE, mode)
                        putExtra(
                            MainActivity.EXTRA_WIDGET_FILE_TRANSFER_ENTRY_MODE,
                            intent.getStringExtra(HostWidgets.EXTRA_FILE_TRANSFER_ENTRY_MODE)
                        )
                    }
                    context.startActivity(launchIntent)
                }
            }

            HostWidgets.ACTION_WIDGET_OPEN -> {
                val sessionId = intent.getStringExtra(HostWidgets.EXTRA_SESSION_ID).orEmpty()
                if (sessionId.isNotBlank()) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        action = SessionService.ACTION_OPEN_SESSION
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(SessionService.EXTRA_HOST_ID, sessionId)
                    }
                    context.startActivity(launchIntent)
                }
            }

            HostWidgets.ACTION_WIDGET_DISCONNECT -> {
                val sessionId = intent.getStringExtra(HostWidgets.EXTRA_SESSION_ID).orEmpty()
                if (sessionId.isNotBlank()) {
                    val stopIntent = Intent(context, SessionService::class.java).apply {
                        action = SessionService.ACTION_STOP_SESSION
                        putExtra(SessionService.EXTRA_HOST_ID, sessionId)
                    }
                    context.startService(stopIntent)
                }
            }
        }
        super.onReceive(context, intent)
        HostWidgets.updateAll(context)
    }
}

class QuickConnectWidgetProvider : BaseHostWidgetProvider()

class SessionsWidgetProvider : BaseHostWidgetProvider()
