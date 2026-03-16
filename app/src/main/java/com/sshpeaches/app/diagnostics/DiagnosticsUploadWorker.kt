package com.majordaftapps.sshpeaches.app.diagnostics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

class DiagnosticsUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        SettingsStore.init(applicationContext)
        val usageEnabled = SettingsStore.usageReportsEnabled.first()
        if (!usageEnabled) {
            return Result.success()
        }
        val settings = DiagnosticsBundle.SettingsSnapshot(
            analyticsEnabled = SettingsStore.analyticsEnabled.first(),
            crashReportsEnabled = SettingsStore.crashReportsEnabled.first(),
            diagnosticsEnabled = SettingsStore.diagnosticsEnabled.first(),
            usageReportsEnabled = usageEnabled,
            allowBackgroundSessions = SettingsStore.allowBackgroundSessions.first()
        )
        val db = SshPeachesDatabase.get(applicationContext)
        val counts = DiagnosticsBundle.DataCounts(
            hosts = db.hostDao().countAll(),
            identities = db.identityDao().countAll(),
            portForwards = db.portForwardDao().countAll(),
            snippets = db.snippetDao().countAll()
        )
        val bundle = DiagnosticsBundle.basic(settings, counts)
        val appCheckToken = AppCheckTokenProvider.getToken()
        val uploader = DiagnosticsUploader(BuildConfig.DIAGNOSTICS_ENDPOINT)
        return when (uploader.upload(bundle, appCheckToken)) {
            is DiagnosticsUploader.UploadResult.Success -> Result.success()
            is DiagnosticsUploader.UploadResult.Skipped -> Result.success()
            is DiagnosticsUploader.UploadResult.Failed -> Result.failure()
            is DiagnosticsUploader.UploadResult.Retryable -> Result.retry()
        }
    }
}
