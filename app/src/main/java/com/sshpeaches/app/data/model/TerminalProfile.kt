package com.sshpeaches.app.data.model

import java.util.UUID

data class TerminalProfile(
    val id: String,
    val name: String,
    val fontSizeSp: Int = 12,
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
            name = "GNOME Dark",
            fontSizeSp = 12,
            foregroundHex = "#EEEEEC",
            backgroundHex = "#2E3436",
            cursorHex = "#FCE94F",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-gnome-light",
            name = "GNOME Light",
            fontSizeSp = 12,
            foregroundHex = "#2E3436",
            backgroundHex = "#FFFFFF",
            cursorHex = "#3465A4",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-solarized-dark",
            name = "Solarized Dark",
            fontSizeSp = 12,
            foregroundHex = "#839496",
            backgroundHex = "#002B36",
            cursorHex = "#93A1A1",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        ),
        TerminalProfile(
            id = "builtin-green-screen",
            name = "Green on Black",
            fontSizeSp = 12,
            foregroundHex = "#33FF66",
            backgroundHex = "#000000",
            cursorHex = "#99FF66",
            cursorStyle = TerminalCursorStyle.UNDERLINE,
            cursorBlink = false
        )
    )

    fun profileById(id: String?): TerminalProfile? = builtInProfiles.firstOrNull { it.id == id }

    fun customTemplate(name: String = "Custom Profile"): TerminalProfile =
        TerminalProfile(
            id = "custom-${UUID.randomUUID()}",
            name = name,
            fontSizeSp = 12,
            foregroundHex = "#E6E6E6",
            backgroundHex = "#101010",
            cursorHex = "#FFB74D",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true
        )
}

