package com.majordaftapps.sshpeaches.app.themes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalThemeApplicationTest {

    @Test
    fun applyProfile_updatesEmulatorColorsAndCursorStyle() {
        val engine = TermuxTerminalEngine(onWriteToRemote = {})
        val profile = TerminalProfile(
            id = "test-profile",
            name = "Test Profile",
            font = TerminalFont.JETBRAINS_MONO,
            fontSizeSp = 14,
            foregroundHex = "#112233",
            backgroundHex = "#445566",
            cursorHex = "#778899",
            cursorStyle = TerminalCursorStyle.BAR,
            ansiColors = List(16) { index -> "#${(index + 1).toString(16).padStart(6, '0')}" }
        )

        engine.applyProfile(profile)

        val emulator = engine.emulator()
        assertEquals(0xFF112233.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND])
        assertEquals(0xFF445566.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        assertEquals(0xFF778899.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR])
        assertEquals(0xFF000001.toInt(), emulator.mColors.mCurrentColors[0])
        assertEquals(0xFF000010.toInt(), emulator.mColors.mCurrentColors[15])
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, emulator.cursorStyle)

        engine.reset()

        assertEquals(0xFF112233.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND])
        assertEquals(0xFF445566.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        assertEquals(0xFF778899.toInt(), emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR])
        assertEquals(0xFF000001.toInt(), emulator.mColors.mCurrentColors[0])
        assertEquals(0xFF000010.toInt(), emulator.mColors.mCurrentColors[15])
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, emulator.cursorStyle)
    }
}
