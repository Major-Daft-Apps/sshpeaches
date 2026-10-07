package com.majordaftapps.sshpeaches.app.diagnostics

import android.os.Build
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.telemetry.TelemetrySanitizer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A problem report the user chose to send, e.g. from a failed connection. Everything in it is
 * shown to them first, and it is scrubbed of hosts, addresses, usernames, and paths.
 */
data class ProblemReport(
    val summary: String,
    val details: Map<String, String>,
    val logLines: List<String>,
    val note: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "problem_report")
        put("id", UUID.randomUUID().toString())
        put("generatedAt", System.currentTimeMillis())
        put("app", DiagnosticsBundle.AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE).toJson())
        put(
            "device",
            DiagnosticsBundle.DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.RELEASE ?: "unknown").toJson()
        )
        put("summary", summary)
        put("details", JSONObject(details))
        put("log", JSONArray(logLines))
        if (note.isNotBlank()) put("note", note)
    }

    /** What the user sees before sending; the same text that is sent. */
    fun preview(): String = buildString {
        appendLine("App: SSHPeaches ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        appendLine(summary)
        details.forEach { (key, value) -> appendLine("$key: $value") }
        if (logLines.isNotEmpty()) {
            appendLine()
            logLines.forEach(::appendLine)
        }
    }.trimEnd()

    companion object {
        const val MAX_LOG_LINES = 300
        private const val MAX_LINE_CHARS = 400

        /** Builds a report, scrubbing every field. [secrets] are values to remove verbatim (host, user). */
        fun create(
            summary: String,
            details: Map<String, String>,
            log: List<String>,
            note: String = "",
            secrets: Collection<String?> = emptyList()
        ): ProblemReport = ProblemReport(
            summary = TelemetrySanitizer.scrub(summary, secrets),
            details = details.mapValues { (_, value) -> TelemetrySanitizer.scrub(value, secrets) },
            logLines = log.takeLast(MAX_LOG_LINES).map { TelemetrySanitizer.scrub(it, secrets).take(MAX_LINE_CHARS) },
            note = TelemetrySanitizer.scrub(note, secrets).take(2_000)
        )
    }
}

object ProblemReportSender {
    /** Sends [report] to the SSHPeaches diagnostics endpoint. */
    suspend fun send(report: ProblemReport): Result<Unit> = withContext(Dispatchers.IO) {
        val token = AppCheckTokenProvider.getToken()
        when (val result = DiagnosticsUploader(BuildConfig.DIAGNOSTICS_ENDPOINT).uploadJson(report.toJson().toString(), token)) {
            is DiagnosticsUploader.UploadResult.Success -> Result.success(Unit)
            is DiagnosticsUploader.UploadResult.Skipped -> Result.failure(IllegalStateException("Reporting isn't set up in this build."))
            is DiagnosticsUploader.UploadResult.Failed -> Result.failure(IllegalStateException("The report was rejected (${result.reason})."))
            is DiagnosticsUploader.UploadResult.Retryable -> Result.failure(IllegalStateException("Couldn't reach the server. Try again later."))
        }
    }
}
