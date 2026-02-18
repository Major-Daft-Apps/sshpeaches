package com.sshpeaches.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.HostConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps SSH/Mosh sessions alive.
 * Currently a skeleton – actual session wiring to SshClientProvider will be added later.
 */
class SessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val binder = SessionBinder()
    private val activeSessions = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Idle", "No active sessions")
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        activeSessions.values.forEach { it.cancel() }
        activeSessions.clear()
    }

    fun startSession(host: HostConnection) {
        val existing = activeSessions[host.id]
        if (existing != null) return
        val job = serviceScope.launch {
            // TODO hook into SshClientProvider, connect and stream session
        }
        activeSessions[host.id] = job
        updateNotification(host.name)
    }

    fun stopSession(hostId: String) {
        activeSessions.remove(hostId)?.cancel()
        updateNotification()
    }

    private fun updateNotification(activeHostName: String? = null) {
        val text = when {
            activeHostName != null -> "Connected to $activeHostName"
            activeSessions.isNotEmpty() -> "Connected to ${activeSessions.size} hosts"
            else -> "No active sessions"
        }
        val notif = buildNotification("SSHPeaches", text)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSHPeaches Sessions",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    inner class SessionBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIFICATION_ID = 42
    }
}
