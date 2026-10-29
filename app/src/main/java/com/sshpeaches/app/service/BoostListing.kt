package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectoryEntry
import org.json.JSONObject

/**
 * Turns a file boost listing (tools/boost wire.Listing) into the browser's entries, the same way an SFTP listing
 * would come out. Without [resolveSymbolicLinks] (the console), links carry no target details, as over SFTP.
 */
internal fun parseBoostListing(json: String, resolveSymbolicLinks: Boolean): LoadedSftpDirectory {
    val root = JSONObject(json)
    val path = root.getString("path")
    val items = root.optJSONArray("entries")
    val entries = ArrayList<RemoteDirectoryEntry>(items?.length() ?: 0)
    for (index in 0 until (items?.length() ?: 0)) {
        val item = items!!.getJSONObject(index)
        val name = item.getString("n")
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) continue
        val type = item.optString("t", "o")
        val isLink = type == "l"
        val broken = isLink && item.optBoolean("lb")
        entries += RemoteDirectoryEntry(
            name = name,
            isDirectory = type == "d",
            sizeBytes = item.optLong("s"),
            absolutePath = if (path.endsWith("/")) path + name else "$path/$name",
            modifiedAtEpochMillis = item.optLong("m").takeIf { it > 0L }?.times(1000L),
            permissionSummary = boostPermissionSummary(type, item.optInt("p")),
            isSymbolicLink = isLink,
            linkTargetPath = if (isLink && resolveSymbolicLinks && !broken) item.optString("lt") else null,
            linkTargetIsDirectory = isLink && resolveSymbolicLinks && item.optBoolean("ld"),
            isBrokenLink = resolveSymbolicLinks && broken
        )
    }
    return LoadedSftpDirectory(path = path, entries = entries.sortedForBrowsing())
}

/** `drwxr-xr-x`, like `ls -l`, from the entry's type letter and Unix mode bits. */
internal fun boostPermissionSummary(type: String, mode: Int): String = buildString(10) {
    append(
        when (type) {
            "d" -> 'd'
            "l" -> 'l'
            "f" -> '-'
            else -> '?'
        }
    )
    fun bit(mask: Int, char: Char) = append(if (mode and mask != 0) char else '-')
    fun exec(mask: Int, special: Int, set: Char, unset: Char) = append(
        when {
            mode and special != 0 && mode and mask != 0 -> set
            mode and special != 0 -> unset
            mode and mask != 0 -> 'x'
            else -> '-'
        }
    )
    // Octal 0400 0200 0100 / 040 020 010 / 04 02 01, with setuid 04000, setgid 02000 and sticky 01000.
    bit(0x100, 'r'); bit(0x80, 'w'); exec(0x40, 0x800, 's', 'S')
    bit(0x20, 'r'); bit(0x10, 'w'); exec(0x8, 0x400, 's', 'S')
    bit(0x4, 'r'); bit(0x2, 'w'); exec(0x1, 0x200, 't', 'T')
}
