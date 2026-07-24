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
    val cursorBlink: Boolean = true,
    val ansiColors: List<String> = TerminalAnsiPalette.TERMUX
)

enum class TerminalCursorStyle(val label: String) {
    BLOCK("Block"),
    UNDERLINE("Underline"),
    BAR("Bar")
}

/** The first 16 indexed colors used by terminal SGR sequences (30-37 and 90-97). */
object TerminalAnsiPalette {
    val TERMUX: List<String> = palette(
        "#757575", "#EF5350", "#66BB6A", "#F9A825",
        "#42A5F5", "#AB47BC", "#26C6DA", "#E0E0E0",
        "#9E9E9E", "#FF6B6B", "#7BD88F", "#FFD166",
        "#64B5F6", "#CE93D8", "#4DD0E1", "#FFFFFF"
    )

    val LINUX_CONSOLE: List<String> = palette(
        "#757575", "#CC0000", "#4E9A06", "#C4A000",
        "#3465A4", "#75507B", "#06989A", "#D3D7CF",
        "#888A85", "#EF2929", "#8AE234", "#FCE94F",
        "#729FCF", "#AD7FA8", "#34E2E2", "#EEEEEC"
    )

    val SOLARIZED_DARK: List<String> = palette(
        "#657B83", "#DC322F", "#859900", "#B58900",
        "#268BD2", "#D33682", "#2AA198", "#EEE8D5",
        "#839496", "#CB4B16", "#93A100", "#C99B00",
        "#4AA3DF", "#E15D9A", "#45B8AE", "#FDF6E3"
    )

    val SOLARIZED_LIGHT: List<String> = palette(
        "#073642", "#DC322F", "#657B00", "#8F5902",
        "#005FAF", "#B0005F", "#007C7C", "#586E75",
        "#002B36", "#CB4B16", "#586E00", "#7A5B00",
        "#006DAE", "#9B2C6B", "#006E75", "#657B83"
    )

    val DRACULA: List<String> = palette(
        "#6272A4", "#FF5555", "#50FA7B", "#F1FA8C",
        "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
        "#8292C4", "#FF6E6E", "#69FF94", "#FFFFA5",
        "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF"
    )

    val TANGO: List<String> = palette(
        "#888A85", "#CC0000", "#4E9A06", "#C4A000",
        "#3465A4", "#75507B", "#06989A", "#D3D7CF",
        "#BABDB6", "#EF2929", "#8AE234", "#FCE94F",
        "#729FCF", "#AD7FA8", "#34E2E2", "#EEEEEC"
    )

    val GITHUB_LIGHT: List<String> = palette(
        "#24292F", "#CF222E", "#116329", "#7D4E00",
        "#0969DA", "#8250DF", "#1B7C83", "#57606A",
        "#424A53", "#A40E26", "#1A7F37", "#633C01",
        "#0550AE", "#6639BA", "#116B74", "#6E7781"
    )

    val GITHUB_DARK: List<String> = palette(
        "#6E7681", "#FF7B72", "#3FB950", "#D29922",
        "#58A6FF", "#BC8CFF", "#39C5CF", "#B1BAC4",
        "#8C959F", "#FFA198", "#56D364", "#E3B341",
        "#79C0FF", "#D2A8FF", "#56D4DD", "#F0F6FC"
    )

    val ONE_DARK: List<String> = palette(
        "#5C6370", "#E06C75", "#98C379", "#E5C07B",
        "#61AFEF", "#C678DD", "#56B6C2", "#ABB2BF",
        "#7F848E", "#E88388", "#A8D08D", "#EED08B",
        "#74B9F2", "#D18BE4", "#6CC1CB", "#C8CCD4"
    )

    val GRUVBOX: List<String> = palette(
        "#928374", "#CC241D", "#98971A", "#D79921",
        "#458588", "#B16286", "#689D6A", "#EBDBB2",
        "#A89984", "#FB4934", "#B8BB26", "#FABD2F",
        "#83A598", "#D3869B", "#8EC07C", "#FBF1C7"
    )

    val NORD: List<String> = palette(
        "#7B88A1", "#BF616A", "#A3BE8C", "#EBCB8B",
        "#81A1C1", "#B48EAD", "#88C0D0", "#E5E9F0",
        "#929DB3", "#D06F79", "#B1D196", "#F0D399",
        "#8FBCBB", "#C895C1", "#93CCDC", "#ECEFF4"
    )

    val MONOKAI: List<String> = palette(
        "#75715E", "#F92672", "#A6E22E", "#E6DB74",
        "#66D9EF", "#AE81FF", "#A1EFE4", "#F8F8F2",
        "#9A9580", "#FF5C8D", "#B6F24A", "#F3E889",
        "#85E4F4", "#C49AFF", "#B7F5EC", "#FFFFFF"
    )

    val XTERM_LIGHT: List<String> = palette(
        "#000000", "#CD0000", "#007A00", "#7A6500",
        "#0000CD", "#A000A0", "#007A7A", "#595959",
        "#3D3D3D", "#A80000", "#006B00", "#665500",
        "#0000A8", "#850085", "#006666", "#707070"
    )

    private fun palette(vararg colors: String): List<String> {
        check(colors.size == 16)
        return colors.toList()
    }
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.TERMUX
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.LINUX_CONSOLE
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.SOLARIZED_DARK
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.SOLARIZED_LIGHT
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.DRACULA
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.TANGO
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.GITHUB_LIGHT
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.GITHUB_DARK
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.ONE_DARK
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.GRUVBOX
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.NORD
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
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.MONOKAI
        ),
        TerminalProfile(
            id = "builtin-xterm-default",
            name = "xterm",
            font = TerminalFont.DROID_SANS_MONO,
            fontSizeSp = 10,
            foregroundHex = "#000000",
            backgroundHex = "#FFFFFF",
            cursorHex = "#000000",
            cursorStyle = TerminalCursorStyle.BLOCK,
            cursorBlink = true,
            ansiColors = TerminalAnsiPalette.XTERM_LIGHT
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
