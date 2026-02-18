package com.sshpeaches.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.ssh.SshClientProvider
import kotlinx.coroutines.CancellationException
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
            buildNotification("SSHPeaches Sessions", "No active sessions")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> intent.getStringExtra(EXTRA_HOST_ID)?.let { stopSession(it) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        activeSessions.values.forEach { it.cancel() }
        activeSessions.clear()
    }

    fun startSession(host: HostConnection, mode: ConnectionMode) {
        if (activeSessions.containsKey(host.id)) return
        val job = serviceScope.launch {
            runCatching {
                val client = SshClientProvider.createClient(
                    this@SessionService,
                    host,
                    SessionLoggerFactory(host.id)
                )
                client.connect(host.host, host.port)
                when (host.preferredAuth) {
                    AuthMethod.PASSWORD -> client.authPassword(host.username, "")
                    AuthMethod.IDENTITY -> client.authPublickey(host.username)
                    AuthMethod.PASSWORD_AND_IDENTITY -> {
                        runCatching { client.authPublickey(host.username) }
                        client.authPassword(host.username, "")
                    }
                }
                // TODO: keep shell/channel open, stream data, manage port forwards based on mode
                client.disconnect()
            }.onFailure { e ->
                if (e !is CancellationException) {
                    // log/notify failure
                }
            }
        }
        activeSessions[host.id] = job
        showHostNotification(host)
        updateSummaryNotification()
    }

    fun stopSession(hostId: String) {
        activeSessions.remove(hostId)?.cancel()
        cancelHostNotification(hostId)
        updateSummaryNotification()
    }

    fun stopAllSessions() {
        val ids = activeSessions.keys.toList()
        ids.forEach { stopSession(it) }
    }

    private fun updateSummaryNotification() {
        val summary = when {
            activeSessions.isEmpty() -> "No active sessions"
            activeSessions.size == 1 -> "Connected to 1 host"
            else -> "Connected to ${activeSessions.size} hosts"
        }
        val notif = buildNotification("SSHPeaches Sessions", summary)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun showHostNotification(host: HostConnection) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val stopIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_HOST_ID, host.id)
        }
        val pendingStop = PendingIntent.getService(
            this,
            host.id.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(host.name)
            .setContentText("Connected to ${host.host}:${host.port}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Disconnect", pendingStop)
            .build()
        nm.notify(host.id.hashCode(), notif)
    }

    private fun cancelHostNotification(hostId: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(hostId.hashCode())
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
        private const val ACTION_STOP = "com.sshpeaches.app.service.ACTION_STOP"
        private const val EXTRA_HOST_ID = "extra_host_id"
    }
}
