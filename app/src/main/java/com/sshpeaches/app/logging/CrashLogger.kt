package com.sshpeaches.app.logging

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val MAX_REPORT_FILES = 20

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneId.systemDefault())
    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installed = false

    @Volatile
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val writeLock = Any()

    fun install(context: Context) {
        if (installed) return
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val file = writeReport(
                type = "FATAL",
                thread = thread,
                throwable = throwable,
                label = "Uncaught exception"
            )
            if (file != null) {
                Log.e(TAG, "Fatal crash report saved: ${file.absolutePath}", throwable)
            } else {
                Log.e(TAG, "Fatal crash report could not be saved", throwable)
            }

            val delegate = previousHandler
            if (delegate != null) {
                delegate.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
        installed = true
        Log.i(TAG, "Crash logging installed")
    }

    fun logNonFatal(label: String, throwable: Throwable) {
        val file = writeReport(
            type = "NON_FATAL",
            thread = Thread.currentThread(),
            throwable = throwable,
            label = label
        )
        if (file != null) {
            Log.e(TAG, "Non-fatal report saved: ${file.absolutePath}", throwable)
        } else {
            Log.e(TAG, "Non-fatal report could not be saved", throwable)
        }
    }

    private fun writeReport(
        type: String,
        thread: Thread,
        throwable: Throwable,
        label: String
    ): File? {
        val context = appContext ?: return null
        val now = Instant.now()
        val reportDir = File(context.filesDir, "logs")
        val reportFile = File(reportDir, "crash-${fileNameFormatter.format(now)}-$type.log")

        val reportBody = buildString {
            appendLine("type=$type")
            appendLine("label=$label")
            appendLine("timestamp=${timestampFormatter.format(now)}")
            appendLine("thread=${thread.name} (${thread.id})")
            appendLine("appPackage=${context.packageName}")
            appendLine("appVersion=${readAppVersion(context)}")
            appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("brand=${Build.BRAND}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
            appendLine()
            appendLine(stackTraceToString(throwable))
        }

        return runCatching {
            synchronized(writeLock) {
                reportDir.mkdirs()
                reportFile.writeText(reportBody)
                pruneOldReports(reportDir)
            }
            reportFile
        }.getOrNull()
    }

    private fun pruneOldReports(reportDir: File) {
        val files = reportDir.listFiles { file -> file.isFile && file.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (files.size <= MAX_REPORT_FILES) return
        files.drop(MAX_REPORT_FILES).forEach { it.delete() }
    }

    private fun readAppVersion(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = info.versionName ?: "unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(info)
            "$versionName ($versionCode)"
        }.getOrElse { "unknown" }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
