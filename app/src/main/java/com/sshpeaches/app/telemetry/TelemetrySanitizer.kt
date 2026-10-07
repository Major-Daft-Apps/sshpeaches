package com.majordaftapps.sshpeaches.app.telemetry

/**
 * Strips what could identify a user's servers or files from anything we report: host names,
 * IP addresses, user@host pairs, URLs, file paths, and any extra values the caller names (the
 * session's host and username). Used for crash breadcrumbs, non-fatal reports, and problem reports.
 */
object TelemetrySanitizer {
    private val userAtHost = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val url = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val ipv6 = Regex("""\[?\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b\]?""")
    private val path = Regex("""(?<![\w<])(?:~|/)(?:[^\s/:'"`,;()<>]+/?)+""")
    private val hostName = Regex("""\b(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,24}\b""")
    private val fingerprint = Regex("""SHA256:[A-Za-z0-9+/=]{20,}""")

    fun scrub(text: String, secrets: Collection<String?> = emptyList()): String {
        var result = text
        secrets.filterNotNull()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }
            .forEach { secret -> result = result.replace(secret, "<redacted>", ignoreCase = true) }
        result = url.replace(result, "<url>")
        result = userAtHost.replace(result, "<user>@<host>")
        result = fingerprint.replace(result, "<fingerprint>")
        result = ipv4.replace(result, "<ip>")
        result = ipv6.replace(result) { match ->
            // "12:34:56" is a time of day, not an address.
            if (match.value.count { it == ':' } >= 2 && match.value.any { it.isLetter() || it == '[' } ||
                match.value.contains("::")
            ) "<ip>" else match.value
        }
        result = path.replace(result, "<path>")
        result = hostName.replace(result, "<host>")
        return result
    }

    /** A throwable with the same types and stack frames, but scrubbed messages, safe to report. */
    fun sanitize(throwable: Throwable, secrets: Collection<String?> = emptyList()): Throwable =
        sanitize(throwable, secrets, depth = 0)

    private fun sanitize(throwable: Throwable, secrets: Collection<String?>, depth: Int): Throwable {
        val message = throwable.message?.let { scrub(it, secrets) }
        val cause = throwable.cause?.takeIf { depth < MAX_CAUSE_DEPTH && it !== throwable }
            ?.let { sanitize(it, secrets, depth + 1) }
        return ReportedException(throwable.javaClass.name, message, cause).also {
            it.stackTrace = throwable.stackTrace
        }
    }

    private const val MAX_CAUSE_DEPTH = 8
}

/** Carries the original exception type in its message, since the scrubbed copy is a new class. */
class ReportedException(
    val originalType: String,
    originalMessage: String?,
    cause: Throwable?
) : Exception(if (originalMessage == null) originalType else "$originalType: $originalMessage", cause)
