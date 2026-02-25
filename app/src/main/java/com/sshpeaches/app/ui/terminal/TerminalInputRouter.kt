package com.sshpeaches.app.ui.terminal

import android.view.KeyEvent
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import java.nio.charset.StandardCharsets

class TerminalInputRouter(
    private val emulatorProvider: () -> TerminalEmulator,
    private val onWriteToRemote: (ByteArray) -> Unit
) {

    fun sendText(text: String) {
        if (text.isEmpty()) return
        onWriteToRemote(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun sendVirtualKey(
        keyCode: Int,
        ctrlDown: Boolean = false,
        altDown: Boolean = false,
        shiftDown: Boolean = false
    ): Boolean {
        var keyMod = 0
        if (ctrlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (altDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        val emulator = emulatorProvider()
        val code = KeyHandler.getCode(
            keyCode,
            keyMod,
            emulator.isCursorKeysApplicationMode,
            emulator.isKeypadApplicationMode
        )
        if (code.isNullOrEmpty()) return false
        onWriteToRemote(code.toByteArray(StandardCharsets.UTF_8))
        return true
    }

    fun onAndroidKeyDown(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            val chars = event.characters ?: return true
            if (chars.isNotEmpty()) {
                onWriteToRemote(chars.toByteArray(StandardCharsets.UTF_8))
            }
            return true
        }

        if (event.isSystem && event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false

        val controlDown = event.isCtrlPressed
        val leftAltDown = (event.metaState and KeyEvent.META_ALT_LEFT_ON) != 0
        val shiftDown = event.isShiftPressed

        var keyMod = if (controlDown) KeyHandler.KEYMOD_CTRL else 0
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        val keyCodeSequence = KeyHandler.getCode(
            event.keyCode,
            keyMod,
            emulatorProvider().isCursorKeysApplicationMode,
            emulatorProvider().isKeypadApplicationMode
        )
        if (!keyCodeSequence.isNullOrEmpty()) {
            onWriteToRemote(keyCodeSequence.toByteArray(StandardCharsets.UTF_8))
            return true
        }

        val rightAltDownFromEvent = (event.metaState and KeyEvent.META_ALT_RIGHT_ON) != 0
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDownFromEvent) {
            bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        var effectiveMetaState = event.metaState and bitsToClear.inv()
        if (shiftDown) {
            effectiveMetaState = effectiveMetaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }

        val unicode = event.getUnicodeChar(effectiveMetaState)
        if (unicode != 0) {
            sendCodePoint(unicode, controlDown, event.isAltPressed || leftAltDown)
            return true
        }

        return false
    }

    private fun sendCodePoint(codePoint: Int, ctrlDown: Boolean, altDown: Boolean) {
        val normalized = normalizeCodePoint(codePoint, ctrlDown)
        if (normalized < 0) return
        val payload = if (normalized == 0) {
            byteArrayOf(0)
        } else {
            String(Character.toChars(normalized)).toByteArray(StandardCharsets.UTF_8)
        }
        if (altDown) {
            onWriteToRemote(byteArrayOf(0x1B))
        }
        onWriteToRemote(payload)
    }

    private fun normalizeCodePoint(codePoint: Int, ctrlDown: Boolean): Int {
        if (!ctrlDown) return codePoint
        return when {
            codePoint in 'a'.code..'z'.code -> codePoint - 'a'.code + 1
            codePoint in 'A'.code..'Z'.code -> codePoint - 'A'.code + 1
            codePoint == ' '.code || codePoint == '2'.code -> 0
            codePoint == '['.code || codePoint == '3'.code -> 27
            codePoint == '\\'.code || codePoint == '4'.code -> 28
            codePoint == ']'.code || codePoint == '5'.code -> 29
            codePoint == '^'.code || codePoint == '6'.code -> 30
            codePoint == '_'.code || codePoint == '7'.code || codePoint == '/'.code -> 31
            codePoint == '8'.code -> 127
            else -> codePoint
        }
    }
}
