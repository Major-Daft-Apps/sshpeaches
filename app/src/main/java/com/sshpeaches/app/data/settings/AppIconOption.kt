package com.majordaftapps.sshpeaches.app.data.settings

/** In the order the App Icon grid shows them. */
enum class AppIconOption {
    ORANGE_ON_CREAM,
    PEACH_LIGHT,
    PEACH_DARK,
    WHITE_ON_ORANGE,
    CORAL_ON_BLUSH,
    PEACH_ON_PLUM,
    BLACK_ON_PEACH,
    MINT_ON_CHARCOAL,
    ORANGE_ON_BLACK,
    TERMINAL_GREEN;

    companion object {
        // Includes "DEFAULT", the retired 3D icon: those users move to Orange on cream.
        fun fromStoredValue(value: String?): AppIconOption =
            entries.firstOrNull { it.name == value } ?: ORANGE_ON_CREAM
    }
}
