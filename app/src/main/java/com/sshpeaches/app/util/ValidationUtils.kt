package com.majordaftapps.sshpeaches.app.util

private val fingerprintRegex =
    Regex("^(SHA256:)?[A-Za-z0-9+/=]{20,}\$|^([0-9a-fA-F]{2}:){15}[0-9a-fA-F]{2}\$")

// One or more DNS labels; single-label names (raspberrypi, db) resolve through search domains, mDNS or /etc/hosts.
private val hostnameRegex =
    Regex("^(?=.{1,253}\$)[a-zA-Z0-9_](?:[a-zA-Z0-9_-]{0,61}[a-zA-Z0-9_])?(?:\\.[a-zA-Z0-9_](?:[a-zA-Z0-9_-]{0,61}[a-zA-Z0-9_])?)*\\.?\$")

private val ipv6GroupRegex = Regex("^[0-9a-fA-F]{1,4}\$")

private val ipv4Regex =
    Regex("^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$")

fun isValidFingerprint(value: String): Boolean =
    fingerprintRegex.matches(value.trim())

fun isValidHostAddress(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.equals("localhost", true)) return true
    if (trimmed.contains(':')) return isValidIpv6Literal(trimmed)
    // All-numeric names are IPv4 addresses, so 1.2.3.999 must not pass as a host name.
    if (trimmed.all { it.isDigit() || it == '.' }) return ipv4Regex.matches(trimmed)
    return hostnameRegex.matches(trimmed)
}

/** An unbracketed IPv6 address such as ::1, fe80::1%wlan0 or ::ffff:192.0.2.1, checked without any DNS lookup. */
private fun isValidIpv6Literal(value: String): Boolean {
    val address = value.substringBefore('%')
    val zone = value.substringAfter('%', missingDelimiterValue = "")
    if (value.contains('%') && (zone.isEmpty() || !zone.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' })) {
        return false
    }
    val halves = address.split("::")
    if (halves.size > 2) return false
    fun groups(part: String): List<String>? = if (part.isEmpty()) emptyList() else part.split(':')
    val head = groups(halves[0]) ?: return false
    val tail = if (halves.size == 2) groups(halves[1]) ?: return false else emptyList()
    val all = head + tail
    // An embedded IPv4 address may only end the address, and counts as two groups.
    val last = all.lastOrNull()
    val endsWithIpv4 = last != null && last.contains('.')
    if (endsWithIpv4 && !ipv4Regex.matches(last!!)) return false
    val hexGroups = if (endsWithIpv4) all.dropLast(1) else all
    if (!hexGroups.all { ipv6GroupRegex.matches(it) }) return false
    val groupCount = hexGroups.size + if (endsWithIpv4) 2 else 0
    return if (halves.size == 2) groupCount <= 7 else groupCount == 8
}

fun isValidPort(port: Int): Boolean = port in 1..65535

fun parsePort(text: String): Int? =
    text.toIntOrNull()?.takeIf { isValidPort(it) }
