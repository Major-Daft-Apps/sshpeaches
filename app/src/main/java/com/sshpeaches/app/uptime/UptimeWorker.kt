package com.majordaftapps.sshpeaches.app.uptime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication

class UptimeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext as SSHPeachesApplication
        app.container.uptimeMonitorRunner.runDueChecks()
        Result.success()
    }.getOrElse { Result.retry() }
}
