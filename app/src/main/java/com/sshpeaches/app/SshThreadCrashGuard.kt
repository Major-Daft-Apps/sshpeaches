package com.majordaftapps.sshpeaches.app

import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer

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
                previous?.uncaughtException(thread, error)
            }
        }
    }

    internal fun isSshReaderThread(name: String?): Boolean =
        name != null && (name.startsWith("sshj-PacketReader") || name.startsWith("sshj-Reader"))
}
