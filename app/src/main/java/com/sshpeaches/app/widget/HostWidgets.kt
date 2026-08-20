package com.majordaftapps.sshpeaches.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
                val capacity = WidgetLayoutCapacity.calculate(
                    variant = WidgetVariant.QUICK_CONNECT,
                    heightDp = widgetHeightDp(manager, id, QUICK_CONNECT_MIN_HEIGHT_DP),
                    hostCount = hosts.size,
                    sessionCount = openSessions.size
                )
                manager.updateAppWidget(
                    id,
                    buildQuickWidgetRemoteViews(context, hosts, openSessions, capacity)
                )
            }
        }
        if (sessionsIds.isNotEmpty()) {
            sessionsIds.forEach { id ->
                val capacity = WidgetLayoutCapacity.calculate(
                    variant = WidgetVariant.SESSIONS,
                    heightDp = widgetHeightDp(manager, id, SESSIONS_MIN_HEIGHT_DP),
                    hostCount = hosts.size,
                    sessionCount = openSessions.size
                )
                manager.updateAppWidget(
                    id,
                    buildSessionsWidgetRemoteViews(context, hosts, openSessions, capacity)
                )
            }
        }
    }

    private fun widgetHeightDp(
        manager: AppWidgetManager,
        appWidgetId: Int,
        fallbackHeightDp: Int
    ): Int = manager.getAppWidgetOptions(appWidgetId)
        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHeightDp)
        .takeIf { it > 0 }
        ?: fallbackHeightDp

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

    internal fun buildQuickWidgetRemoteViews(
        context: Context,
        hosts: List<HostConnection>,
        openSessions: List<WidgetSessionStore.WidgetOpenSession>,
        capacity: WidgetCapacity
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)
        views.removeAllViews(R.id.widget_open_sessions_container)
        views.removeAllViews(R.id.widget_hosts_container)
        configureOpenAppActions(context, views)
        val visibleSessions = openSessions.take(capacity.sessionRows)
        if (visibleSessions.isEmpty()) {
            views.setViewVisibility(R.id.widget_open_sessions_section, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_open_sessions_section, View.VISIBLE)
            views.setViewVisibility(R.id.widget_open_sessions_empty, View.GONE)
            visibleSessions.forEach { openSession ->
                views.addView(
                    R.id.widget_open_sessions_container,
                    buildOpenSessionRow(context, openSession)
                )
            }
        }

        val visibleHosts = hosts.take(capacity.hostRows)
        when {
            hosts.isEmpty() && openSessions.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, View.VISIBLE)
            }
            visibleHosts.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.GONE)
            }
            else -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, View.GONE)
                visibleHosts.forEach { host ->
                    views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
                }
            }
        }
        configureOverflow(context, views, capacity.hiddenCount)
        return views
    }

    internal fun buildSessionsWidgetRemoteViews(
        context: Context,
        hosts: List<HostConnection>,
        openSessions: List<WidgetSessionStore.WidgetOpenSession>,
        capacity: WidgetCapacity
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sessions)
        views.removeAllViews(R.id.widget_hosts_container)
        views.removeAllViews(R.id.widget_open_sessions_container)
        configureOpenAppActions(context, views)
        val visibleHosts = hosts.take(capacity.hostRows)
        when {
            hosts.isEmpty() && openSessions.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, View.VISIBLE)
            }
            visibleHosts.isEmpty() -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.GONE)
            }
            else -> {
                views.setViewVisibility(R.id.widget_hosts_section, View.VISIBLE)
                views.setViewVisibility(R.id.widget_hosts_empty, View.GONE)
                visibleHosts.forEach { host ->
                    views.addView(R.id.widget_hosts_container, buildHostRow(context, host))
                }
            }
        }
        if (openSessions.isEmpty()) {
            views.setViewVisibility(R.id.widget_open_sessions_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_open_sessions_empty, View.GONE)
            openSessions.take(capacity.sessionRows).forEach { openSession ->
                views.addView(
                    R.id.widget_open_sessions_container,
                    buildOpenSessionRow(context, openSession)
                )
            }
        }
        configureOverflow(context, views, capacity.hiddenCount)
        return views
    }

    internal fun buildHostRow(context: Context, host: HostConnection): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_host_row)
        val title = host.name.ifBlank { "${host.username}@${host.host}:${host.port}" }
        val endpoint = "${host.username}@${host.host}:${host.port}"
        row.setTextViewText(R.id.widget_host_title, title)
        row.setTextViewText(R.id.widget_host_subtitle, endpoint)
        row.setContentDescription(
            R.id.widget_btn_ssh,
            context.getString(R.string.widget_action_ssh_for_host, title, endpoint)
        )
        row.setContentDescription(
            R.id.widget_btn_upload,
            context.getString(R.string.widget_action_upload_for_host, title, endpoint)
        )
        row.setContentDescription(
            R.id.widget_btn_download,
            context.getString(R.string.widget_action_download_for_host, title, endpoint)
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_ssh,
            createConnectPendingIntent(context, host.id, ConnectionMode.SSH)
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_upload,
            createConnectPendingIntent(
                context,
                host.id,
                ConnectionMode.SCP,
                FileTransferEntryMode.UPLOAD
            )
        )
        row.setOnClickPendingIntent(
            R.id.widget_btn_download,
            createConnectPendingIntent(
                context,
                host.id,
                ConnectionMode.SCP,
                FileTransferEntryMode.DOWNLOAD
            )
        )
        return row
    }

    internal fun buildOpenSessionRow(
        context: Context,
        session: WidgetSessionStore.WidgetOpenSession
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_open_session_row)
        row.setTextViewText(R.id.widget_open_session_title, session.title)
        row.setTextViewText(R.id.widget_open_session_subtitle, session.subtitle)
        val openIntent = createOpenPendingIntent(context, session.sessionId)
        val openDescription = context.getString(
            R.string.widget_action_open_session_details,
            session.title,
            session.subtitle
        )
        row.setContentDescription(R.id.widget_open_session_identity, openDescription)
        row.setContentDescription(R.id.widget_btn_open, openDescription)
        row.setContentDescription(
            R.id.widget_btn_disconnect,
            context.getString(
                R.string.widget_action_disconnect_session_details,
                session.title,
                session.subtitle
            )
        )
        row.setOnClickPendingIntent(R.id.widget_open_session_identity, openIntent)
        row.setOnClickPendingIntent(R.id.widget_btn_open, openIntent)
        row.setOnClickPendingIntent(
            R.id.widget_btn_disconnect,
            createDisconnectPendingIntent(context, session.sessionId)
        )
        return row
    }

    private fun configureOpenAppActions(context: Context, views: RemoteViews) {
        val openApp = createOpenAppPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_hosts_empty, openApp)
        views.setOnClickPendingIntent(R.id.widget_open_sessions_empty, openApp)
    }

    private fun configureOverflow(context: Context, views: RemoteViews, hiddenCount: Int) {
        if (hiddenCount <= 0) {
            views.setViewVisibility(R.id.widget_overflow, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_overflow, View.VISIBLE)
        views.setTextViewText(
            R.id.widget_overflow,
            context.resources.getQuantityString(
                R.plurals.widget_more_items,
                hiddenCount,
                hiddenCount
            )
        )
        views.setContentDescription(
            R.id.widget_overflow,
            context.resources.getQuantityString(
                R.plurals.widget_more_items_description,
                hiddenCount,
                hiddenCount
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_overflow,
            createOpenAppPendingIntent(context)
        )
    }

    internal fun createConnectPendingIntent(
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

    private fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isTrustedWidgetActionIntent(context: Context, intent: Intent): Boolean {
        val expected = actionToken(context)
        val actual = intent.getStringExtra(EXTRA_ACTION_TOKEN)
        return !actual.isNullOrBlank() && actual == expected
    }

    fun putActionToken(intent: Intent, context: Context) {
        intent.putExtra(EXTRA_ACTION_TOKEN, actionToken(context))
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

    private const val OPEN_APP_REQUEST_CODE = 0x535348
    internal const val QUICK_CONNECT_MIN_HEIGHT_DP = 260
    internal const val SESSIONS_MIN_HEIGHT_DP = 320
}

internal enum class WidgetVariant {
    QUICK_CONNECT,
    SESSIONS
}

internal data class WidgetCapacity(
    val hostRows: Int,
    val sessionRows: Int,
    val hiddenCount: Int
)

internal object WidgetLayoutCapacity {
    private const val ROOT_VERTICAL_PADDING_DP = 16
    private const val SECTION_FIXED_HEIGHT_DP = 32
    private const val SECTION_GAP_DP = 6
    private const val ROW_HEIGHT_DP = 60
    private const val OVERFLOW_HEIGHT_DP = 54
    private const val MAX_ITEM_ROWS = 8

    fun calculate(
        variant: WidgetVariant,
        heightDp: Int,
        hostCount: Int,
        sessionCount: Int
    ): WidgetCapacity {
        val safeHostCount = hostCount.coerceAtLeast(0)
        val safeSessionCount = sessionCount.coerceAtLeast(0)
        val hostSectionVisible = when (variant) {
            WidgetVariant.QUICK_CONNECT ->
                safeHostCount > 0 || safeSessionCount == 0
            WidgetVariant.SESSIONS ->
                safeHostCount > 0 || safeSessionCount == 0
        }
        val sessionSectionVisible = when (variant) {
            WidgetVariant.QUICK_CONNECT -> safeSessionCount > 0
            WidgetVariant.SESSIONS -> true
        }
        val sectionCount = listOf(hostSectionVisible, sessionSectionVisible).count { it }
        val placeholderRows =
            (if (hostSectionVisible && safeHostCount == 0) 1 else 0) +
                (if (sessionSectionVisible && safeSessionCount == 0) 1 else 0)
        val fixedHeight = ROOT_VERTICAL_PADDING_DP +
            (sectionCount * SECTION_FIXED_HEIGHT_DP) +
            ((sectionCount - 1).coerceAtLeast(0) * SECTION_GAP_DP)
        val availableWithoutOverflow = (heightDp - fixedHeight).coerceAtLeast(0)
        val rowUnitsWithoutOverflow = availableWithoutOverflow / ROW_HEIGHT_DP
        val itemSlotsWithoutOverflow = (rowUnitsWithoutOverflow - placeholderRows)
            .coerceAtLeast(0)
            .coerceAtMost(MAX_ITEM_ROWS)
        val totalItems = safeHostCount + safeSessionCount
        val needsOverflow = totalItems > itemSlotsWithoutOverflow
        val availableWithOverflow = (
            heightDp -
                fixedHeight -
                if (needsOverflow) OVERFLOW_HEIGHT_DP else 0
            ).coerceAtLeast(0)
        val itemSlots = ((availableWithOverflow / ROW_HEIGHT_DP) - placeholderRows)
            .coerceAtLeast(0)
            .coerceAtMost(MAX_ITEM_ROWS)
            .coerceAtMost(totalItems)
        val (hostRows, sessionRows) = allocateRows(
            variant = variant,
            slots = itemSlots,
            hostCount = safeHostCount,
            sessionCount = safeSessionCount
        )
        return WidgetCapacity(
            hostRows = hostRows,
            sessionRows = sessionRows,
            hiddenCount = totalItems - hostRows - sessionRows
        )
    }

    private fun allocateRows(
        variant: WidgetVariant,
        slots: Int,
        hostCount: Int,
        sessionCount: Int
    ): Pair<Int, Int> {
        var hostRows = 0
        var sessionRows = 0
        var remaining = slots
        val allocationOrder = when (variant) {
            WidgetVariant.QUICK_CONNECT -> listOf(WidgetRowType.HOST, WidgetRowType.SESSION)
            WidgetVariant.SESSIONS -> listOf(WidgetRowType.SESSION, WidgetRowType.HOST)
        }
        while (remaining > 0) {
            var allocatedInPass = false
            allocationOrder.forEach { rowType ->
                if (remaining <= 0) return@forEach
                when (rowType) {
                    WidgetRowType.HOST -> if (hostRows < hostCount) {
                        hostRows += 1
                        remaining -= 1
                        allocatedInPass = true
                    }
                    WidgetRowType.SESSION -> if (sessionRows < sessionCount) {
                        sessionRows += 1
                        remaining -= 1
                        allocatedInPass = true
                    }
                }
            }
            if (!allocatedInPass) break
        }
        return hostRows to sessionRows
    }

    private enum class WidgetRowType {
        HOST,
        SESSION
    }
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        HostWidgets.updateAll(context)
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
                        HostWidgets.putActionToken(this, context)
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
