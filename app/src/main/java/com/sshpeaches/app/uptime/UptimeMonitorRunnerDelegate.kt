package com.majordaftapps.sshpeaches.app.uptime

interface UptimeMonitorRunnerDelegate {
    suspend fun runDueChecks(now: Long = System.currentTimeMillis(), hostId: String? = null)
}
