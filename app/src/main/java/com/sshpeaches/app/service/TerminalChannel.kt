package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.boost.boostclient.Shell as BoostShell
import com.majordaftapps.sshpeaches.boost.boostclient.ShellListener
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import net.schmizz.sshj.connection.channel.direct.Session

/**
 * The remote end of a terminal: what the shell's reader, writer and resizer loops talk to. [inputStream] is the
 * terminal's output (EOF when the shell ends), [outputStream] takes typed input.
 */
internal interface TerminalChannel {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun resize(columns: Int, rows: Int)
    /** Ends the terminal: its shell ends with it. */
    fun close()
}

/** A shell on an SSH session channel. */
internal class SshTerminalChannel(private val session: Session, private val shell: Session.Shell) : TerminalChannel {
    override val inputStream: InputStream get() = shell.inputStream
    override val outputStream: OutputStream get() = shell.outputStream

    override fun resize(columns: Int, rows: Int) {
        shell.changeWindowDimensions(columns, rows, 0, 0)
    }

    override fun close() {
        runCatching { shell.close() }
        runCatching { session.close() }
    }
}

/**
 * A login shell in the Boost helper. It doesn't depend on the SSH connection: the Go side reattaches over a new
 * UDP (or TCP) connection whenever the old one breaks, so the terminal carries on through SSH reconnects and
 * network changes. [onState] hears "connected" / "reconnecting" / "lost" with a detail.
 */
internal class BoostTerminalChannel(
    private val boost: BoostHelper,
    columns: Int,
    rows: Int,
    term: String,
    private val onState: (state: String, detail: String) -> Unit
) : TerminalChannel {
    private object Ended
    private class Lost(val reason: String)

    private val chunks = LinkedBlockingQueue<Any>()

    /** False once the shell exited or can't be reached any more. */
    @Volatile
    var alive = true
        private set

    private val shell: BoostShell = boost.startShell(rows, columns, term, object : ShellListener {
        // Called on the shell's Go goroutine, one at a time; nothing here may throw back into Go.
        override fun onOutput(data: ByteArray) {
            chunks.offer(data)
        }

        override fun onExit(status: Long) {
            alive = false
            chunks.offer(Ended)
        }

        override fun onState(state: String, detail: String) {
            if (state == "lost") {
                alive = false
                chunks.offer(Lost(detail))
            }
            runCatching { onState(state, detail) }
        }
    })

    override val inputStream: InputStream = object : InputStream() {
        private var current: ByteArray? = null
        private var position = 0

        private fun fill(): Boolean {
            while (current == null || position >= current!!.size) {
                when (val next = chunks.take()) {
                    is ByteArray -> {
                        current = next
                        position = 0
                    }
                    is Lost -> {
                        chunks.offer(next)
                        throw IOException("Boost lost the terminal: ${next.reason}")
                    }
                    else -> {
                        chunks.offer(Ended)
                        return false
                    }
                }
            }
            return true
        }

        override fun read(): Int {
            if (!fill()) return -1
            return current!![position++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!fill()) return -1
            val bytes = current!!
            val count = minOf(length, bytes.size - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }
    }

    override val outputStream: OutputStream = object : OutputStream() {
        override fun write(byte: Int) {
            write(byteArrayOf(byte.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length > 0) shell.write(buffer.copyOfRange(offset, offset + length))
        }
    }

    override fun resize(columns: Int, rows: Int) {
        shell.resize(rows.toLong(), columns.toLong())
    }

    /** "UDP" or "TCP": what the helper was reached over. */
    val transport: String get() = boost.transport.uppercase()

    /** Smoothed round trip to the helper from the terminal's pings, or null before the first answer. */
    fun rttMillis(): Long? = shell.rttMillis().takeIf { it >= 0 }

    /** Reattaches now over a new connection: the network changed, so the old path is probably gone. */
    fun kick() {
        shell.kick()
    }

    override fun close() {
        alive = false
        runCatching { shell.close(true) }
        runCatching { boost.close() }
        chunks.offer(Ended)
    }
}
