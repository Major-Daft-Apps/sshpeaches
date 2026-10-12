package com.majordaftapps.sshpeaches.app.util

import java.util.Base64

/** The first `-----BEGIN type-----` ... `-----END type-----` block in some key material, base64-decoded. */
internal class PemBlock(
    val type: String,
    val body: ByteArray
) {
    companion object {
        private val MARKER_REGEX = Regex("-----(BEGIN|END) ([A-Z0-9 ]+)-----")
        private val WHITESPACE_REGEX = Regex("\\s")

        fun parse(input: String): PemBlock? {
            val text = input.trim()
            // One pass over the markers. A single regex with a backreference rescanned the rest of the text for every
            // BEGIN without a matching END, which took minutes on a large malformed file.
            val openBodies = mutableMapOf<String, Int>()
            for (marker in MARKER_REGEX.findAll(text)) {
                val type = marker.groupValues[2]
                if (marker.groupValues[1] == "BEGIN") {
                    openBodies.putIfAbsent(type, marker.range.last + 1)
                    continue
                }
                val bodyStart = openBodies[type] ?: continue
                val bodyText = text.substring(bodyStart, marker.range.first).replace(WHITESPACE_REGEX, "")
                if (bodyText.isBlank()) return null
                val body = runCatching { Base64.getDecoder().decode(bodyText) }.getOrNull() ?: return null
                return PemBlock(type = type.trim(), body = body)
            }
            return null
        }
    }
}
