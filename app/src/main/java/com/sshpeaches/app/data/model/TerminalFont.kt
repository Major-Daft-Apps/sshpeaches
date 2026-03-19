package com.majordaftapps.sshpeaches.app.data.model

enum class TerminalFont(
    val label: String,
    val assetPath: String?,
    val selectable: Boolean = true
) {
    SYSTEM_MONOSPACE(
        label = "System Monospace",
        assetPath = null,
        selectable = false
    ),
    CASCADIA_CODE_MONO(
        label = "Cascadia Code Mono",
        assetPath = "fonts/CascadiaMono-Regular.ttf"
    ),
    SAUCE_CODE_PRO(
        label = "Sauce Code Pro",
        assetPath = "fonts/SourceCodePro.ttf"
    ),
    ROBOTO_MONO(
        label = "Roboto Mono",
        assetPath = "fonts/RobotoMono.ttf"
    ),
    JETBRAINS_MONO(
        label = "JetBrains Mono",
        assetPath = "fonts/JetBrainsMono.ttf"
    ),
    FIRA_CODE(
        label = "Fira Code",
        assetPath = "fonts/FiraCode.ttf"
    ),
    OPEN_DYSLEXIC(
        label = "Open Dyslexic",
        assetPath = "fonts/OpenDyslexicMono-Regular.ttf"
    ),
    INCOLSOLATA(
        label = "Inconsolata",
        assetPath = "fonts/Inconsolata-Regular.ttf"
    ),
    DROID_SANS_MONO(
        label = "Droid Sans Mono",
        assetPath = "fonts/DroidSansMono.ttf"
    );

    companion object {
        fun selectableValues(): List<TerminalFont> = values().filter { it.selectable }

        fun fromStorageValue(value: String?): TerminalFont =
            values().firstOrNull { it.name == value } ?: SYSTEM_MONOSPACE
    }
}
