package com.majordaftapps.sshpeaches.app.data.model

enum class TerminalFont(
    val label: String,
    val typefaceFamilies: List<String>
) {
    SYSTEM_MONOSPACE(
        label = "System Monospace",
        typefaceFamilies = emptyList()
    ),
    CASCADIA_CODE_MONO(
        label = "Cascadia Code Mono",
        typefaceFamilies = listOf("Cascadia Mono", "Cascadia Code")
    ),
    SAUCE_CODE_PRO(
        label = "Sauce Code Pro",
        typefaceFamilies = listOf("Source Code Pro", "Sauce Code Pro")
    ),
    ROBOTO_MONO(
        label = "Roboto Mono",
        typefaceFamilies = listOf("Roboto Mono")
    ),
    JETBRAINS_MONO(
        label = "JetBrains Mono",
        typefaceFamilies = listOf("JetBrains Mono")
    ),
    FIRA_CODE(
        label = "Fira Code",
        typefaceFamilies = listOf("Fira Code")
    ),
    OPEN_DYSLEXIC(
        label = "Open Dyslexic",
        typefaceFamilies = listOf("OpenDyslexic", "Open Dyslexic")
    ),
    INCOLSOLATA(
        label = "Incolsolata",
        typefaceFamilies = listOf("Inconsolata", "Incolsolata")
    ),
    DROID_SANS_MONO(
        label = "Droid Sans Mono",
        typefaceFamilies = listOf("Droid Sans Mono")
    );

    companion object {
        fun fromStorageValue(value: String?): TerminalFont =
            values().firstOrNull { it.name == value } ?: SYSTEM_MONOSPACE
    }
}
