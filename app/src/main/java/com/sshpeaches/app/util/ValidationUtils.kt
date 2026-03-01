package com.majordaftapps.sshpeaches.app.util

private val fingerprintRegex =
    Regex("^(SHA256:)?[A-Za-z0-9+/=]{20,}\$|^([0-9a-fA-F]{2}:){15}[0-9a-fA-F]{2}\$")

private val hostnameRegex =
    Regex("^(?=.{1,253}\$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\$")

private val ipv4Regex =
    Regex("^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$")

fun isValidFingerprint(value: String): Boolean =
    fingerprintRegex.matches(value.trim())

fun isValidHostAddress(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.equals("localhost", true)) return true
    return hostnameRegex.matches(trimmed) || ipv4Regex.matches(trimmed)
}

fun isValidPort(port: Int): Boolean = port in 1..65535

fun parsePort(text: String): Int? =
    text.toIntOrNull()?.takeIf { isValidPort(it) }
