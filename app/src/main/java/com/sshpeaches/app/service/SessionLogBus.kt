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
    // they are on while the connection transcript is (packetLogText decodes the packets and drops the ones that carry
    // secrets), and never for an open session, where every keystroke would be dumped.
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
        val packetLog = packetLogText(message, args)
        if (packetLog == SKIP_PACKET_LOG) return
        val text = when {
            packetLog != null -> packetLog
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
        SessionLogBus.emit(SessionLogBus.Entry(hostId, level, text))
    }
}

internal const val SKIP_PACKET_LOG = ""

/**
 * Turns SSHJ's one-line packet hex dumps into one readable line per packet, like `ssh -vv`: the message type plus
 * what it negotiates (algorithm lists, service, auth methods that can continue, channel and request types).
 * Packets that carry anything typed or sent (auth requests, keyboard-interactive answers, terminal data, and any
 * message type not known to be safe) are not logged at all. Returns null for anything that isn't a packet dump and
 * [SKIP_PACKET_LOG] for a packet that isn't logged.
 */
internal fun packetLogText(format: String?, args: Array<out Any?>?): String? {
    val outgoing = when (format) {
        "Encoding packet #{}: {}" -> true
        "Received packet #{}: {}" -> false
        "IN #{}: {}" -> return SKIP_PACKET_LOG
        // Fail closed: a dump this filter doesn't recognise (say sshj renames a log line) could hold a password.
        else -> return if (args.orEmpty().any(::looksLikeHexDump)) SKIP_PACKET_LOG else null
    }
    val hex = args?.getOrNull(1)?.toString() ?: return null
    val id = hex.take(2).toIntOrNull(16) ?: return null
    if (id !in LOGGED_MESSAGES || (outgoing && id in INCOMING_ONLY_LOGGED_MESSAGES)) return SKIP_PACKET_LOG
    val size = (hex.length + 1) / 3
    val header = "${if (outgoing) "send" else "recv"} SSH_MSG_${Message.fromByte(id.toByte()).name} ($size bytes)"
    val details = runCatching {
        val packet = PacketReader(ByteArray(minOf(size, MAX_DECODED_PACKET_BYTES)) {
            hex.substring(it * 3, it * 3 + 2).toInt(16).toByte()
        })
        packet.skip(1)
        packetDetails(id, outgoing, packet)
    }.getOrNull().orEmpty()
    return if (details.isEmpty()) header else header + details.joinToString("") { "\n  $it" }
}

/** sshj's packet dumps: space-separated hex byte pairs ("05 00 00 00 0c 73 73 68 ..."). */
private fun looksLikeHexDump(arg: Any?): Boolean {
    val text = (arg as? CharSequence ?: return false).take(HEX_DUMP_PROBE_CHARS)
    return HEX_DUMP_PREFIX.containsMatchIn(text)
}

private val HEX_DUMP_PREFIX = Regex("^\\s*[0-9a-fA-F]{2}(\\s[0-9a-fA-F]{2}){7}")
private const val HEX_DUMP_PROBE_CHARS = 64

private fun packetDetails(id: Int, outgoing: Boolean, packet: PacketReader): List<String> = when (id) {
    1 -> listOf("reason ${packet.uint32()}: ${packet.text()}")
    5, 6 -> listOf("service ${packet.text()}")
    7 -> List(packet.uint32().toInt().coerceAtMost(32)) {
        packet.text().also { packet.string() }
    }.let { listOf("extensions ${it.joinToString(",")}") }
    20 -> {
        packet.skip(16)
        val side = if (outgoing) "client" else "server"
        val names = listOf(
            "KEX algorithms", "host key algorithms", "ciphers ctos", "ciphers stoc", "MACs ctos", "MACs stoc",
            "compression ctos", "compression stoc"
        )
        listOf("$side proposal:") + names.map { "  $it: ${packet.text()}" }
    }
    // The ECDH/DH reply (31) and the group-exchange reply (33) start with the host key, whose first field is its
    // type. 31 is also the group-exchange GROUP message, which holds no key: then nothing is shown.
    31, 33 -> PacketReader(packet.string()).text()
        .takeIf { it.matches(KEY_TYPE_PATTERN) }
        ?.let { listOf("host key type $it") }
        .orEmpty()
    51 -> {
        val methods = packet.text()
        val partial = packet.boolean()
        listOf("can continue: $methods" + if (partial) " (partial success)" else "")
    }
    80 -> listOf("request ${packet.text()}, want reply ${packet.boolean()}")
    90 -> listOf("type ${packet.text()}, channel ${packet.uint32()}")
    91 -> listOf(
        "channel ${packet.uint32()} -> remote ${packet.uint32()}, window ${packet.uint32()}, max packet ${packet.uint32()}"
    )
    92 -> {
        packet.uint32()
        listOf("reason ${packet.uint32()}: ${packet.text()}")
    }
    98 -> {
        val channel = packet.uint32()
        listOf("channel $channel, request ${packet.text()}, want reply ${packet.boolean()}")
    }
    else -> emptyList()
}

/** Reads SSH wire types (RFC 4251) from a packet. Strings are bounded to what the packet holds. */
private class PacketReader(private val bytes: ByteArray) {
    private var position = 0

    fun skip(count: Int) {
        require(position + count <= bytes.size)
        position += count
    }

    fun boolean(): Boolean {
        require(position < bytes.size)
        return bytes[position++].toInt() != 0
    }

    fun uint32(): Long {
        require(position + 4 <= bytes.size)
        var value = 0L
        repeat(4) { value = (value shl 8) or (bytes[position++].toLong() and 0xff) }
        return value
    }

    fun string(): ByteArray {
        val length = uint32()
        require(length <= bytes.size - position)
        return bytes.copyOfRange(position, position + length.toInt()).also { position += length.toInt() }
    }

    fun text(): String = String(string(), Charsets.UTF_8)
        .map { if (it.isISOControl()) '?' else it }
        .joinToString("")
        .let { if (it.length > MAX_LOGGED_TEXT_CHARS) it.take(MAX_LOGGED_TEXT_CHARS) + "…" else it }
}

// Transport, key exchange and channel bookkeeping: nothing the user typed or the session sent. USERAUTH_REQUEST
// (50), keyboard-interactive (60/61), CHANNEL_DATA (94/95) and anything unknown are never logged.
private val LOGGED_MESSAGES = setOf(1, 2, 3, 4, 5, 6, 7, 20, 21) + (30..49) + setOf(51, 52, 53) +
    (80..93) + setOf(96, 97, 98, 99, 100)
// The server's banner (53) is shown, but nothing the client sends with these ids is ours to log.
private val INCOMING_ONLY_LOGGED_MESSAGES = setOf(51, 52, 53)
private const val MAX_DECODED_PACKET_BYTES = 8192
private const val MAX_LOGGED_TEXT_CHARS = 4096
private val KEY_TYPE_PATTERN = Regex("[a-z0-9@.+-]{3,64}")

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
