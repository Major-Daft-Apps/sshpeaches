package com.majordaftapps.sshpeaches.app.telemetry

/**
 * Strips what could identify a user's servers or files from anything we report: host names,
 * IP addresses, user@host pairs, URLs, file paths, and any extra values the caller names (the
 * session's host and username). Used for crash breadcrumbs, non-fatal reports, and problem reports.
 */
object TelemetrySanitizer {
    // The patterns only start at the beginning of a token (the lookbehinds): starting anywhere inside a long run of
    // name characters made scrubbing quadratic, and a long server message froze "Send report".
    private val userAtHost = Regex("""(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val url = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    // Not \b: an address glued to a word ("addr_10.1.2.3") has no word boundary in front of it.
    private val ipv4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?!\d)""")
    private val ipv6 = Regex("""\[?\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b\]?""")
    // No repeated groups below: Java's regex engine recurses once per repetition and overflowed the stack on long
    // runs like "/a/a/a...".
    private val path = Regex("""(?<![\w<])(?:~|/)[^\s:'"`,;()<>]+""")
    // Relative paths ("projects/acme/x.conf"), and the rest of a path after a space ("My Docs/tax.pdf").
    private val relativePath = Regex("""(?<![\w<>./~-])[\w.-]+/[\w./-]+""")
    private val hostName = Regex("""(?<![A-Za-z0-9.-])[A-Za-z0-9-]+\.[A-Za-z0-9.-]*[A-Za-z]{2,24}\b""")
    private val fingerprint = Regex("""SHA256:[A-Za-z0-9+/=]{20,}""")
    private val timeOfDay = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")

    fun scrub(text: String, secrets: Collection<String?> = emptyList()): String {
        var result = text
        secrets.filterNotNull()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }
            .forEach { secret -> result = result.replace(secret, "<redacted>", ignoreCase = true) }
        // Reports never need more than this, and it bounds the regex work. Cut at a space so no address or name is
        // left half-scrubbed at the end.
        if (result.length > MAX_SCRUB_CHARS) {
            val cut = result.lastIndexOf(' ', MAX_SCRUB_CHARS).takeIf { it >= MAX_SCRUB_CHARS - 200 } ?: MAX_SCRUB_CHARS
            result = result.substring(0, cut) + " …"
        }
        result = url.replace(result, "<url>")
        result = userAtHost.replace(result, "<user>@<host>")
        result = fingerprint.replace(result, "<fingerprint>")
        result = ipv4.replace(result, "<ip>")
        result = ipv6.replace(result) { match ->
            // "12:34:56" is a time of day, not an address; anything longer is, even with no a-f in it.
            if (timeOfDay.matches(match.value)) match.value else "<ip>"
        }
        result = path.replace(result, "<path>")
        result = relativePath.replace(result, "<path>")
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
    private const val MAX_SCRUB_CHARS = 4_000
}

/** Carries the original exception type in its message, since the scrubbed copy is a new class. */
class ReportedException(
    val originalType: String,
    originalMessage: String?,
    cause: Throwable?
) : Exception(if (originalMessage == null) originalType else "$originalType: $originalMessage", cause)
