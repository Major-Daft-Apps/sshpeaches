package com.majordaftapps.sshpeaches.app.service

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import net.schmizz.sshj.connection.channel.Channel

/** Most output kept from a background command (OS detection, startup script, shortcuts): far more than they print. */
internal const val REMOTE_COMMAND_OUTPUT_LIMIT = 64 * 1024

/**
 * What [channel] has written to [stream], read only as it arrives: until the channel ends, [limit] bytes are read,
 * or [waitMillis] pass. A blocking read stalled forever (and kept growing) when a server never ended its output.
 */
internal fun collectCommandOutput(
    channel: Channel,
    stream: InputStream,
    waitMillis: Long,
    limit: Int = REMOTE_COMMAND_OUTPUT_LIMIT,
    nanoTime: () -> Long = System::nanoTime,
    pause: (Long) -> Unit = { Thread.sleep(it) }
): String {
    val deadline = nanoTime() + waitMillis * 1_000_000L
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    fun readAvailable(): Boolean {
        var readAny = false
        while (output.size() < limit) {
            val available = stream.available()
            if (available <= 0) break
            val read = stream.read(buffer, 0, minOf(available, buffer.size, limit - output.size()))
            if (read <= 0) break
            output.write(buffer, 0, read)
            readAny = true
        }
        return readAny
    }
    while (output.size() < limit) {
        if (readAvailable()) continue
        if (channel.isEOF || !channel.isOpen) {
            // Output sent before the end is already buffered: take it, then stop.
            readAvailable()
            break
        }
        if (nanoTime() >= deadline) break
        pause(20L)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

/**
 * Stops the mosh-server started with [pid], if that is still what runs under it: this runs at close and before a
 * restart, maybe long after the server exited, and a reused pid could belong to any of the user's processes.
 */
internal fun moshServerStopCommand(pid: Int): String =
    "case \"$(ps -p $pid -o comm= 2>/dev/null)\" in *mosh-server*) kill $pid 2>/dev/null;; esac"
