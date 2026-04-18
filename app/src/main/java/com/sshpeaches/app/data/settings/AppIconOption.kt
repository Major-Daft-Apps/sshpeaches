package com.majordaftapps.sshpeaches.app.data.settings

enum class AppIconOption {
    DEFAULT,
    PEACH_LIGHT,
    PEACH_DARK;

    companion object {
        fun fromStoredValue(value: String?): AppIconOption =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
