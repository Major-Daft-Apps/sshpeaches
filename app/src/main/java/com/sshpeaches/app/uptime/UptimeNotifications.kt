package com.majordaftapps.sshpeaches.app.uptime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes

object UptimeNotifications {
    const val CHANNEL_ID = "uptime_alerts"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Uptime alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when tracked hosts go down or recover."
        }
        manager.createNotificationChannel(channel)
    }

    fun shouldNotify(previous: UptimeStatus?, current: UptimeStatus): Boolean {
        if (previous == null) return false
        if (previous == current) return false
        return previous != UptimeStatus.UNVERIFIED && current != UptimeStatus.UNVERIFIED
    }

    fun notifyTransition(
        context: Context,
        host: HostConnection,
        status: UptimeStatus
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, Routes.UPTIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            host.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = if (status == UptimeStatus.DOWN) {
            "${host.name.ifBlank { host.host }} is down"
        } else {
            "${host.name.ifBlank { host.host }} is back up"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("SSHPeaches Uptime")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(host.id.hashCode(), notification)
        }
    }
}
