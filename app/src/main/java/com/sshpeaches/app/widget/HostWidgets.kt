package com.majordaftapps.sshpeaches.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal object HostWidgets {
    const val ACTION_WIDGET_CONNECT = "com.majordaftapps.sshpeaches.app.widget.ACTION_CONNECT"
    const val ACTION_WIDGET_OPEN = "com.majordaftapps.sshpeaches.app.widget.ACTION_OPEN"
    const val ACTION_WIDGET_DISCONNECT = "com.majordaftapps.sshpeaches.app.widget.ACTION_DISCONNECT"
    const val EXTRA_HOST_ID = "extra_widget_host_id"
    const val EXTRA_MODE = "extra_widget_mode"
    const val EXTRA_SESSION_ID = "extra_widget_session_id"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val quickComponent = ComponentName(context, QuickConnectWidgetProvider::class.java)
        val sessionsComponent = ComponentName(context, SessionsWidgetProvider::class.java)
        val quickIds = manager.getAppWidgetIds(quickComponent)
        val sessionsIds = manager.getAppWidgetIds(sessionsComponent)
        if (quickIds.isNotEmpty()) {
            updateWidgets(
                context = context,
                manager = manager,
                appWidgetIds = quickIds,
                includeOpenSessions = false
            )
        }
        if (sessionsIds.isNotEmpty()) {
            updateWidgets(
                context = context,
                manager = manager,
                appWidgetIds = sessionsIds,
                includeOpenSessions = true
            )
        }
    }

    private fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        includeOpenSessions: Boolean
    ) {
        val hosts = loadHosts(context)
        val openSessions = if (includeOpenSessions) WidgetSessionStore.read(context) else emptyList()
        appWidgetIds.forEach { id ->
            val views = if (includeOpenSessions) {
                buildSessionsWidgetRemoteViews(context, hosts, openSessions)
            } else {
                buildQuickWidgetRemoteViews(context, hosts)
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun loadHosts(context: Context): List<HostConnection> {
        val app = context.applicationContext as? SSHPeachesApplication ?: return emptyList()
        return runCatching {
            runBlocking {
                app.container.repository.hosts.first()
            }
        }.getOrDefault(emptyList())
            .sortedBy { it.name.ifBlank { it.host }.lowercase() }
    }

    private fun buildQuickWidgetRemoteViews(
        context: Context,
        hosts: List<HostConnection>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)
        views.removeAllViews(R.id.widget_hosts_container)
        if (hosts.isEmpty()) {
            views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_hosts_more, android.view.View.GONE)
            return views
        }
        views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.GONE)
        val visibleHosts = hosts.take(MAX_HOST_ROWS_COMPACT)
        visibleHosts.forEach { host ->
            views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
        }
        val remaining = hosts.size - visibleHosts.size
        if (remaining > 0) {
            views.setViewVisibility(R.id.widget_hosts_more, android.view.View.VISIBLE)
            views.setTextViewText(
                R.id.widget_hosts_more,
                context.getString(R.string.widget_more_hosts, remaining)
            )
        } else {
            views.setViewVisibility(R.id.widget_hosts_more, android.view.View.GONE)
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

        if (hosts.isEmpty()) {
            views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_hosts_more, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_hosts_empty, android.view.View.GONE)
            val visibleHosts = hosts.take(MAX_HOST_ROWS_EXPANDED)
            visibleHosts.forEach { host ->
                views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
            }
            val remaining = hosts.size - visibleHosts.size
            if (remaining > 0) {
                views.setViewVisibility(R.id.widget_hosts_more, android.view.View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_hosts_more,
                    context.getString(R.string.widget_more_hosts, remaining)
                )
            } else {
                views.setViewVisibility(R.id.widget_hosts_more, android.view.View.GONE)
            }
        }

        if (openSessions.isEmpty()) {
            views.setViewVisibility(R.id.widget_open_sessions_empty, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_open_sessions_empty, android.view.View.GONE)
            openSessions.take(MAX_OPEN_SESSION_ROWS).forEach { openSession ->
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
            createConnectPendingIntent(context, host.id, ConnectionMode.SFTP)
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_scp,
            createConnectPendingIntent(context, host.id, ConnectionMode.SCP)
        )
        return row
    }

    private fun buildOpenSessionRow(
        context: Context,
        session: WidgetSessionStore.WidgetOpenSession
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_open_session_row)
        row.setTextViewText(R.id.widget_open_session_title, session.title)
        row.setTextViewText(R.id.widget_open_session_subtitle, session.subtitle)
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
        mode: ConnectionMode
    ): PendingIntent {
        val intent = Intent(context, QuickConnectWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CONNECT
            putExtra(EXTRA_HOST_ID, hostId)
            putExtra(EXTRA_MODE, mode.name)
        }
        return PendingIntent.getBroadcast(
            context,
            "connect:$hostId:${mode.name}".hashCode(),
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
        }
        return PendingIntent.getBroadcast(
            context,
            "disconnect:$sessionId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val MAX_HOST_ROWS_COMPACT = 4
    private const val MAX_HOST_ROWS_EXPANDED = 6
    private const val MAX_OPEN_SESSION_ROWS = 6
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
