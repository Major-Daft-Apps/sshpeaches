package com.majordaftapps.sshpeaches.app.uptime

class NoOpUptimeMonitorRunner : UptimeMonitorRunnerDelegate {
    override suspend fun runDueChecks(now: Long, hostId: String?) = Unit
}
