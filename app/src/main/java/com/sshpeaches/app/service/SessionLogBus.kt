@file:Suppress("DEPRECATION")

package com.majordaftapps.sshpeaches.app.service

import android.util.Log
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.service.SessionLogBus.LogLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.common.Message
import org.slf4j.Logger
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter

/**
 * Broadcasts verbose session logs (similar to `ssh -v`) so the UI can show them.
 */
object SessionLogBus {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val hostId: String,
        val level: LogLevel,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        /** A packet dump from the connection transcript. Shown on screen but left out of problem reports: its hex
         * would carry host names and keys past the scrubber. */
        val packetDump: Boolean = false,
        /** Strictly increasing across all entries; lets the UI track "entries after X" even after
         * old entries are trimmed from a bounded buffer. */
        val sequence: Long = nextSequence()
    )

    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong()

    fun nextSequence(): Long = sequenceCounter.incrementAndGet()

    private val _entries = MutableSharedFlow<Entry>(extraBufferCapacity = 200)
    val entries: SharedFlow<Entry> = _entries.asSharedFlow()

    fun emit(entry: Entry) {
        _entries.tryEmit(entry)
    }
}

class SessionLoggerFactory(
    private val hostId: String,
    private val connectionTranscriptEnabled: () -> Boolean = { true },
    private val diagnosticsEnabled: () -> Boolean = { false },
    private val delegate: LoggerFactory = LoggerFactory.DEFAULT,
    private val logcatEnabled: Boolean = BuildConfig.DEBUG
) : LoggerFactory {
    private val transportLogSampler = SshTransportLogSampler(
        intervalNanos = TRANSPORT_LOG_SAMPLE_INTERVAL_NANOS
    )

    override fun getLogger(name: String): Logger =
        SessionLogger(
            hostId = hostId,
            connectionTranscriptEnabled = connectionTranscriptEnabled,
            diagnosticsEnabled = diagnosticsEnabled,
            delegate = delegate.getLogger(name),
            transportLogSampler = transportLogSampler,
            logcatEnabled = logcatEnabled
        )

    override fun getLogger(clazz: Class<*>?): Logger =
        getLogger(clazz?.name ?: "unknown")

    private companion object {
        private const val TRANSPORT_LOG_SAMPLE_INTERVAL_NANOS = 1_000_000_000L
    }
}

private class SessionLogger(
    private val hostId: String,
    private val connectionTranscriptEnabled: () -> Boolean,
    private val diagnosticsEnabled: () -> Boolean,
    private val delegate: Logger,
    private val transportLogSampler: SshTransportLogSampler,
    private val logcatEnabled: Boolean
) : MarkerIgnoringBase() {
    companion object {
        private const val LOGCAT_TAG = "SSHPeachesSSH"
    }

    override fun getName(): String = delegate.name

    // SSHJ's semantic connection events are emitted unguarded. These checks only guard its packet and key dumps, so
    // they are on while the connection transcript is (packetDumpText hides the packets that carry secrets), and
    // never for an open session, where every keystroke would be dumped.
    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled || connectionTranscriptEnabled()
    override fun trace(msg: String?) {
        delegate.trace(msg)
        emit(LogLevel.DEBUG, msg)
    }

    override fun trace(format: String?, arg: Any?) {
        delegate.trace(format, arg)
        emit(LogLevel.DEBUG, format, arrayOf(arg))
    }

    override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        delegate.trace(format, arg1, arg2)
        emit(LogLevel.DEBUG, format, arrayOf(arg1, arg2))
    }

    override fun trace(format: String?, arguments: Array<out Any?>?) {
        delegate.trace(format, arguments)
        emit(LogLevel.DEBUG, format, arguments)
    }

    override fun trace(msg: String?, t: Throwable?) {
        delegate.trace(msg, t)
        emit(LogLevel.DEBUG, msg, throwable = t)
    }

    override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled || connectionTranscriptEnabled()
    override fun debug(msg: String?) {
        delegate.debug(msg)
        emit(LogLevel.DEBUG, msg)
    }

    override fun debug(format: String?, arg: Any?) {
        delegate.debug(format, arg)
        emit(LogLevel.DEBUG, format, arrayOf(arg))
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        delegate.debug(format, arg1, arg2)
        emit(LogLevel.DEBUG, format, arrayOf(arg1, arg2))
    }

    override fun debug(format: String?, arguments: Array<out Any?>?) {
        delegate.debug(format, arguments)
        emit(LogLevel.DEBUG, format, arguments)
    }

    override fun debug(msg: String?, t: Throwable?) {
        delegate.debug(msg, t)
        emit(LogLevel.DEBUG, msg, throwable = t)
    }

    override fun isInfoEnabled(): Boolean = delegate.isInfoEnabled
    override fun info(msg: String?) {
        delegate.info(msg)
        emit(LogLevel.INFO, msg)
    }

    override fun info(format: String?, arg: Any?) {
        delegate.info(format, arg)
        emit(LogLevel.INFO, format, arrayOf(arg))
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        delegate.info(format, arg1, arg2)
        emit(LogLevel.INFO, format, arrayOf(arg1, arg2))
    }

    override fun info(format: String?, arguments: Array<out Any?>?) {
        delegate.info(format, arguments)
        emit(LogLevel.INFO, format, arguments)
    }

    override fun info(msg: String?, t: Throwable?) {
        delegate.info(msg, t)
        emit(LogLevel.INFO, msg, throwable = t)
    }

    override fun isWarnEnabled(): Boolean = delegate.isWarnEnabled
    override fun warn(msg: String?) {
        delegate.warn(msg)
        emit(LogLevel.WARN, msg)
    }

    override fun warn(format: String?, arg: Any?) {
        delegate.warn(format, arg)
        emit(LogLevel.WARN, format, arrayOf(arg))
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        delegate.warn(format, arg1, arg2)
        emit(LogLevel.WARN, format, arrayOf(arg1, arg2))
    }

    override fun warn(format: String?, arguments: Array<out Any?>?) {
        delegate.warn(format, arguments)
        emit(LogLevel.WARN, format, arguments)
    }

    override fun warn(msg: String?, t: Throwable?) {
        delegate.warn(msg, t)
        emit(LogLevel.WARN, msg, throwable = t)
    }

    override fun isErrorEnabled(): Boolean = delegate.isErrorEnabled
    override fun error(msg: String?) {
        delegate.error(msg)
        emit(LogLevel.ERROR, msg)
    }

    override fun error(format: String?, arg: Any?) {
        delegate.error(format, arg)
        emit(LogLevel.ERROR, format, arrayOf(arg))
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        delegate.error(format, arg1, arg2)
        emit(LogLevel.ERROR, format, arrayOf(arg1, arg2))
    }

    override fun error(format: String?, arguments: Array<out Any?>?) {
        delegate.error(format, arguments)
        emit(LogLevel.ERROR, format, arguments)
    }

    override fun error(msg: String?, t: Throwable?) {
        delegate.error(msg, t)
        emit(LogLevel.ERROR, msg, throwable = t)
    }

    private fun emit(
        level: LogLevel,
        message: String?,
        args: Array<out Any?>? = null,
        throwable: Throwable? = null
    ) {
        val connectionTranscriptActive = connectionTranscriptEnabled()
        if (!shouldPublishSshLog(level, connectionTranscriptActive, diagnosticsEnabled())) return
        val dump = packetDumpText(message, args)
        if (dump == SKIP_PACKET_DUMP) return
        val text = when {
            dump != null -> dump
            message == null -> throwable?.message ?: return
            args == null -> message
            else -> MessageFormatter.arrayFormat(message, args).message
        }
        if (text.isNullOrBlank()) return
        if (!transportLogSampler.shouldPublish(level, text, connectionTranscriptActive)) return
        if (logcatEnabled) {
            val logcatMessage = "[$hostId] $text"
            when (level) {
                LogLevel.DEBUG -> Log.d(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.INFO -> Log.i(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.WARN -> Log.w(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.ERROR -> Log.e(LOGCAT_TAG, logcatMessage, throwable)
            }
        }
        SessionLogBus.emit(SessionLogBus.Entry(hostId, level, text, packetDump = dump != null))
    }
}

internal const val SKIP_PACKET_DUMP = ""

/**
 * Turns SSHJ's one-line packet hex dumps into a header plus hexdump rows, so the connection transcript streams like
 * JuiceSSH's. Packets that can carry secrets or session data (passwords, keyboard-interactive answers, channel
 * requests, terminal data) keep only the header. Returns null for anything that isn't a packet dump, and
 * [SKIP_PACKET_DUMP] for SSHJ's per-channel data dumps, which repeat the CHANNEL_DATA packets.
 */
internal fun packetDumpText(format: String?, args: Array<out Any?>?): String? {
    val outgoing = when (format) {
        "Encoding packet #{}: {}" -> true
        "Received packet #{}: {}" -> false
        "IN #{}: {}" -> return SKIP_PACKET_DUMP
        else -> return null
    }
    if (args == null || args.size < 2) return null
    val hex = args[1]?.toString() ?: return null
    val size = (hex.length + 1) / 3
    if (size == 0) return null
    val id = hex.substring(0, 2).toIntOrNull(16) ?: return null
    val name = "SSH_MSG_" + Message.fromByte(id.toByte()).name
    val header = "${if (outgoing) "send" else "recv"} #${args[0]} $name, $size bytes"
    if (if (outgoing) id !in OUTGOING_DUMPABLE_MESSAGES else id in INCOMING_HIDDEN_MESSAGES) {
        return "$header (contents hidden)"
    }
    val shown = minOf(size, MAX_PACKET_DUMP_BYTES)
    val bytes = ByteArray(shown) { hex.substring(it * 3, it * 3 + 2).toInt(16).toByte() }
    return buildString {
        append(header)
        for (offset in 0 until shown step PACKET_DUMP_ROW_BYTES) {
            val end = minOf(offset + PACKET_DUMP_ROW_BYTES, shown)
            append('\n').append("  ").append(offset.toString(16).padStart(4, '0')).append("  ")
            for (i in offset until offset + PACKET_DUMP_ROW_BYTES) {
                append(if (i < end) (bytes[i].toInt() and 0xff).toString(16).padStart(2, '0') + " " else "   ")
            }
            append(' ')
            for (i in offset until end) {
                val c = bytes[i].toInt() and 0xff
                append(if (c in 0x20..0x7e) c.toChar() else '.')
            }
        }
        if (size > shown) append('\n').append("  … ${size - shown} more bytes")
    }
}

// Transport, key exchange and channel bookkeeping: nothing the user typed or the session sent.
private val OUTGOING_DUMPABLE_MESSAGES = setOf(1, 2, 3, 4, 5, 7, 20, 21) + (30..49) + setOf(80, 81, 82, 90, 91, 92, 93, 96, 97, 99, 100)
// Terminal output.
private val INCOMING_HIDDEN_MESSAGES = setOf(94, 95)
private const val PACKET_DUMP_ROW_BYTES = 8
private const val MAX_PACKET_DUMP_BYTES = 1024

internal fun shouldPublishSshLog(
    level: LogLevel,
    connectionTranscriptEnabled: Boolean,
    diagnosticsEnabled: Boolean
): Boolean = level != LogLevel.DEBUG || connectionTranscriptEnabled || diagnosticsEnabled

internal class SshTransportLogSampler(
    private val intervalNanos: Long,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var lastTransportLogNanos: Long? = null

    @Synchronized
    fun shouldPublish(
        level: LogLevel,
        message: String,
        connectionTranscriptEnabled: Boolean = false
    ): Boolean {
        if (connectionTranscriptEnabled) return true
        if (level != LogLevel.DEBUG || !isHighFrequencyTransportLog(message)) return true
        val now = nanoTime()
        val previous = lastTransportLogNanos
        if (previous == null || now - previous >= intervalNanos) {
            lastTransportLogNanos = now
            return true
        }
        return false
    }
}

internal fun isHighFrequencyTransportLog(message: String): Boolean =
    message.contains("Received packet") ||
        message.contains("Consuming by") ||
        message.contains("Increasing by") ||
        message.contains("Sending after interval")
