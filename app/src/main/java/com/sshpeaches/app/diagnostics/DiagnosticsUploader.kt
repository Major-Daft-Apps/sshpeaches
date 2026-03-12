package com.majordaftapps.sshpeaches.app.diagnostics

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class DiagnosticsUploader(
    private val endpoint: String
) {
    fun upload(bundle: DiagnosticsBundle, appCheckToken: String?): UploadResult {
        if (endpoint.isBlank()) {
            return UploadResult.Skipped("Diagnostics endpoint not configured")
        }
        val url = runCatching { URL(endpoint) }.getOrNull()
            ?: return UploadResult.Failed("Invalid diagnostics endpoint URL")
        val payload = bundle.toJson().toString()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!appCheckToken.isNullOrBlank()) {
                setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            }
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload)
            }
            val code = connection.responseCode
            if (code in 200..299) {
                UploadResult.Success(code)
            } else if (code in 500..599) {
                UploadResult.Retryable(code, "Server error")
            } else {
                UploadResult.Failed("HTTP $code")
            }
        }.getOrElse { err ->
            Log.w("SSHPeachesDiag", "Diagnostics upload failed", err)
            UploadResult.Retryable(null, err.message ?: "upload failure")
        }.also {
            connection.disconnect()
        }
    }

    sealed class UploadResult {
        data class Success(val code: Int) : UploadResult()
        data class Retryable(val code: Int?, val reason: String) : UploadResult()
        data class Failed(val reason: String) : UploadResult()
        data class Skipped(val reason: String) : UploadResult()
    }
}
