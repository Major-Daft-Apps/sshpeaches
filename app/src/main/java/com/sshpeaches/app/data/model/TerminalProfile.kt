package com.majordaftapps.sshpeaches.app.data.model

import java.util.UUID

data class TerminalProfile(
    val id: String,
    val name: String,
    val font: TerminalFont = TerminalFont.DROID_SANS_MONO,
    val fontSizeSp: Int = 10,
    val foregroundHex: String = "#E6E6E6",
    val backgroundHex: String = "#101010",
    val cursorHex: String = "#FFB74D",
    val cursorStyle: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    val cursorBlink: Boolean = true
)

enum class TerminalCursorStyle(val label: String) {
    BLOCK("Block"),
    UNDERLINE("Underline"),
    BAR("Bar")
}

object TerminalProfileDefaults {
    const val DEFAULT_PROFILE_ID: String = "builtin-gnome-dark"

    val builtInProfiles: List<TerminalProfile> = listOf(
        TerminalProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Termux",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#A9B7C6",
            backgroundHex = "#000000",
            cursorHex = "#00FF00",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-gnome-light",
            name = "Linux Console",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#00AA00",
            backgroundHex = "#000000",
            cursorHex = "#00FF00",
            cursorStyle = TerminalCursorStyle.UNDERLINE,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-solarized-dark",
            name = "Solarized Dark",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#839496",
            backgroundHex = "#002B36",
            cursorHex = "#93A1A1",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-green-screen",
            name = "Solarized Light",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#657B83",
            backgroundHex = "#FDF6E3",
            cursorHex = "#586E75",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        )
    )

    fun profileById(id: String?): TerminalProfile? = builtInProfiles.firstOrNull { it.id == id }

    fun customTemplate(name: String = "Custom Profile"): TerminalProfile =
        TerminalProfile(
            id = "custom-${UUID.randomUUID()}",
            name = name,
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#E6E6E6",
            backgroundHex = "#101010",
            cursorHex = "#FFB74D",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        )
}
