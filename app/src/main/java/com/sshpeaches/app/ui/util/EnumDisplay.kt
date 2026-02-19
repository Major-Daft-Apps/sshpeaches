package com.sshpeaches.app.ui.util

fun Enum<*>.toSentenceCaseLabel(): String {
    val raw = name.lowercase().replace('_', ' ')
    return raw.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}
