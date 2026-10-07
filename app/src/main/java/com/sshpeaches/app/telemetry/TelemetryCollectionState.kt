package com.majordaftapps.sshpeaches.app.telemetry

/** Whether crash reporting and analytics are collecting right now (null: SDK not initialized). */
data class TelemetryCollectionState(val crashReports: Boolean?, val analytics: Boolean?)
