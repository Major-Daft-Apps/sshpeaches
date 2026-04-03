package com.majordaftapps.sshpeaches.app.ui.terminal

import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle

class TermuxTerminalEngine(
    private val onWriteToRemote: (ByteArray) -> Unit,
    private val onCopyToClipboard: (String) -> Unit = {},
    private val onRequestPasteText: () -> String? = { null },
    private var onBellAction: () -> Unit = {},
    private val onTerminalDiagnostic: (String) -> Unit = {}
) : TerminalOutput(), TerminalSessionClient {

    private val emulator = TerminalEmulator(
        this,
        DEFAULT_COLUMNS,
        DEFAULT_ROWS,
        DEFAULT_CELL_WIDTH_PX,
        DEFAULT_CELL_HEIGHT_PX,
        DEFAULT_TRANSCRIPT_ROWS,
        this
    )
    private var terminalCursorStyle: Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    private var terminalBackgroundColor: Int = Color.BLACK
    private var incomingSequenceNumber = 0
    private var outgoingSequenceNumber = 0

    fun appendIncoming(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        emitByteDiagnostic(direction = "RX", data = bytes, offset = 0, count = bytes.size)
        emulator.append(bytes, bytes.size)
    }

    fun resize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        // Current published terminal-emulator API only accepts columns/rows.
        @Suppress("UNUSED_VARIABLE") val unusedCellWidth = cellWidthPx
        @Suppress("UNUSED_VARIABLE") val unusedCellHeight = cellHeightPx
        val safeColumns = columns.coerceAtLeast(2)
        val safeRows = rows.coerceAtLeast(2)
        emulator.resize(safeColumns, safeRows, cellWidthPx, cellHeightPx)
    }

    fun reset() {
        emulator.reset()
    }

    fun applyProfile(profile: TerminalProfile) {
        val foreground = parseColor(profile.foregroundHex, DEFAULT_FOREGROUND_COLOR)
        val background = parseColor(profile.backgroundHex, Color.BLACK)
        val cursor = parseColor(profile.cursorHex, DEFAULT_CURSOR_COLOR)
        terminalBackgroundColor = background
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = background
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = cursor
        terminalCursorStyle = when (profile.cursorStyle) {
            TerminalCursorStyle.BLOCK -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
            TerminalCursorStyle.UNDERLINE -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            TerminalCursorStyle.BAR -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
        }
        emulator.setCursorStyle()
    }

    fun backgroundColor(): Int = terminalBackgroundColor

    fun emulator(): TerminalEmulator = emulator

    fun setOnBellAction(handler: () -> Unit) {
        onBellAction = handler
    }

    fun renderText(): String {
        return emulator.screen.getTranscriptTextWithoutJoinedLines()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        val end = offset + count
        if (offset < 0 || end > data.size || offset >= end) return
        emitByteDiagnostic(direction = "TX", data = data, offset = offset, count = count)
        onWriteToRemote(data.copyOfRange(offset, end))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {}

    override fun onCopyTextToClipboard(text: String?) {
        onCopyToClipboard(text.orEmpty())
    }

    override fun onPasteTextFromClipboard() {
        val text = onRequestPasteText() ?: return
        if (text.isBlank()) return
        emulator.paste(text)
    }

    override fun onBell() {
        onBellAction()
    }

    override fun onColorsChanged() {}

    override fun onTerminalDebug(message: String) {
        onTerminalDiagnostic(message)
    }

    override fun onTextChanged(changedSession: TerminalSession) {}

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        onCopyToClipboard(text.orEmpty())
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        onPasteTextFromClipboard()
    }

    override fun onBell(session: TerminalSession) {
        onBellAction()
    }

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    private companion object {
        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 40
        private const val DEFAULT_CELL_WIDTH_PX = 0
        private const val DEFAULT_CELL_HEIGHT_PX = 0
        private const val DEFAULT_TRANSCRIPT_ROWS = 5_000
        private const val DEFAULT_FOREGROUND_COLOR = 0xFFE6E6E6.toInt()
        private const val DEFAULT_CURSOR_COLOR = 0xFFFFB74D.toInt()
        private const val DIAGNOSTIC_PREVIEW_BYTES = 48
    }

    private fun parseColor(value: String, fallback: Int): Int =
        runCatching { value.toColorInt() }.getOrDefault(fallback)

    private fun emitByteDiagnostic(direction: String, data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        val sequence = if (direction == "RX") {
            incomingSequenceNumber += 1
            incomingSequenceNumber
        } else {
            outgoingSequenceNumber += 1
            outgoingSequenceNumber
        }
        val previewLength = minOf(count, DIAGNOSTIC_PREVIEW_BYTES)
        val hex = buildString {
            for (index in 0 until previewLength) {
                if (index > 0) append(' ')
                val value = data[offset + index].toInt() and 0xFF
                append(value.toString(16).padStart(2, '0'))
            }
        }
        val ascii = buildString {
            for (index in 0 until previewLength) {
                val value = data[offset + index].toInt() and 0xFF
                append(
                    when (value) {
                        0x1B -> "\\e"
                        0x0D -> "\\r"
                        0x0A -> "\\n"
                        in 0x20..0x7E -> value.toChar().toString()
                        else -> "\\x" + value.toString(16).padStart(2, '0')
                    }
                )
            }
        }
        val truncated = if (count > previewLength) " (+${count - previewLength} bytes)" else ""
        onTerminalDiagnostic("TERM-EMU $direction#$sequence bytes=$count hex=$hex ascii=\"$ascii\"$truncated")
    }
}
