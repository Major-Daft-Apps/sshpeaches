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
        ),
        TerminalProfile(
            id = "builtin-dracula",
            name = "Dracula",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#F8F8F2",
            backgroundHex = "#282A36",
            cursorHex = "#F8F8F2",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-tango",
            name = "Tango",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#D3D7CF",
            backgroundHex = "#2E3436",
            cursorHex = "#D3D7CF",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-github-light",
            name = "GitHub Light",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#24292F",
            backgroundHex = "#FFFFFF",
            cursorHex = "#0969DA",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-github-dark",
            name = "GitHub Dark",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#E6EDF3",
            backgroundHex = "#0D1117",
            cursorHex = "#2F81F7",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-one-dark",
            name = "One Dark",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#ABB2BF",
            backgroundHex = "#282C34",
            cursorHex = "#528BFF",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-gruvbox-dark",
            name = "Gruvbox",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#EBDBB2",
            backgroundHex = "#282828",
            cursorHex = "#FABD2F",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-nord",
            name = "Nord",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#D8DEE9",
            backgroundHex = "#2E3440",
            cursorHex = "#88C0D0",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-monokai",
            name = "Monokai",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#F8F8F2",
            backgroundHex = "#272822",
            cursorHex = "#A6E22E",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-xterm-default",
            name = "XTerm",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#000000",
            backgroundHex = "#FFFFFF",
            cursorHex = "#000000",
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
