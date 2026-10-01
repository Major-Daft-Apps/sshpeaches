package com.majordaftapps.sshpeaches.app.util

fun snippetCommandToTerminalPayload(command: String): String {
    val normalized = command
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd()
    if (normalized.isBlank()) return ""
    return normalized.lines().joinToString("\r", postfix = "\r")
}
