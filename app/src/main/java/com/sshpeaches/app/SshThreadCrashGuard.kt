package com.majordaftapps.sshpeaches.app

import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.telemetry.TelemetrySanitizer

/**
 * Keeps an error on one of sshj's reader threads from crashing the whole app. Those threads parse what the server
 * sends and only catch IOException, so a hostile server could kill the process (and every other session) with, for
 * example, an SFTP packet claiming to be 1 GiB long. The thread still ends, which ends that connection: its pending
 * requests fail and the session's own error handling and reconnect take over.
 */
internal object SshThreadCrashGuard {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isSshReaderThread(thread.name)) {
                runCatching { TelemetryInitializer.recordNonFatal("ssh_reader_thread", error) }
            } else {
                // Crashlytics' handler reports what it's given: a crash's message (a path, user@host) must be scrubbed
                // like every other report. Debug builds keep the original for logcat.
                previous?.uncaughtException(thread, if (BuildConfig.DEBUG) error else TelemetrySanitizer.sanitize(error))
            }
        }
    }

    internal fun isSshReaderThread(name: String?): Boolean =
        name != null && (name.startsWith("sshj-PacketReader") || name.startsWith("sshj-Reader"))
}
