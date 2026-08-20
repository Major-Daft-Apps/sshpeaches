package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectoryEntry
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.xfer.FilePermission

internal data class LoadedSftpDirectory(
    val path: String,
    val entries: List<RemoteDirectoryEntry>
)

internal fun sftpDirectoryRefreshKey(
    snapshot: SessionService.RemoteDirectorySnapshot?
): String = snapshot?.let { "${it.path}|${it.refreshToken}" }.orEmpty()

internal fun shouldResolveSftpLinkMetadata(mode: ConnectionMode?): Boolean =
    mode == ConnectionMode.SCP

/**
 * Loads one directory with a request budget that is constant for SFTP console listings.
 *
 * SCP's visual browser still requests link metadata because it uses target type and broken-link
 * state for navigation. The SFTP console does not display that metadata, so resolving it there
 * only adds two serial network round trips per symbolic link.
 */
internal fun loadSftpDirectory(
    requestedPath: String,
    resolveSymbolicLinks: Boolean,
    canonicalize: (String) -> String,
    listDirectory: (String) -> List<RemoteResourceInfo>,
    stat: (String) -> FileAttributes
): LoadedSftpDirectory {
    val listingPath = requestedPath.trim().ifBlank { "." }
    val resolvedPath = runCatching { canonicalize(listingPath) }.getOrDefault(listingPath)
    val entries = listDirectory(resolvedPath)
        .asSequence()
        .filterNot { it.name == "." || it.name == ".." }
        .map { item ->
            val attributes = item.attributes
            val isSymbolicLink = attributes.type == FileMode.Type.SYMLINK
            val linkTargetPath = if (isSymbolicLink && resolveSymbolicLinks) {
                runCatching { canonicalize(item.path) }.getOrNull()
            } else {
                null
            }
            val linkTargetAttributes = linkTargetPath?.let { targetPath ->
                runCatching { stat(targetPath) }.getOrNull()
            }
            RemoteDirectoryEntry(
                name = item.name,
                isDirectory = item.isDirectory(),
                sizeBytes = attributes.size,
                absolutePath = item.path,
                modifiedAtEpochMillis = attributes
                    .takeIf { it.has(FileAttributes.Flag.ACMODTIME) }
                    ?.mtime
                    ?.times(1000L),
                permissionSummary = buildPermissionSummary(attributes),
                isSymbolicLink = isSymbolicLink,
                linkTargetPath = linkTargetPath,
                linkTargetIsDirectory =
                    linkTargetAttributes?.type == FileMode.Type.DIRECTORY,
                isBrokenLink =
                    isSymbolicLink && resolveSymbolicLinks && linkTargetPath == null
            )
        }
        .sortedWith(REMOTE_DIRECTORY_ENTRY_COMPARATOR)
        .toList()
    return LoadedSftpDirectory(path = resolvedPath, entries = entries)
}

private val REMOTE_DIRECTORY_ENTRY_COMPARATOR = Comparator<RemoteDirectoryEntry> { left, right ->
    when {
        left.isDirectory != right.isDirectory -> if (left.isDirectory) -1 else 1
        else -> {
            val caseInsensitive = String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name)
            if (caseInsensitive != 0) caseInsensitive else left.name.compareTo(right.name)
        }
    }
}

private fun buildPermissionSummary(attributes: FileAttributes): String {
    if (!attributes.has(FileAttributes.Flag.MODE)) return ""
    val permissions = attributes.permissions
    fun has(permission: FilePermission): Boolean = permissions.contains(permission)
    val type = when (attributes.type) {
        FileMode.Type.DIRECTORY -> 'd'
        FileMode.Type.SYMLINK -> 'l'
        FileMode.Type.BLOCK_SPECIAL -> 'b'
        FileMode.Type.CHAR_SPECIAL -> 'c'
        FileMode.Type.FIFO_SPECIAL -> 'p'
        FileMode.Type.SOCKET_SPECIAL -> 's'
        else -> '-'
    }
    return buildString(10) {
        append(type)
        append(if (has(FilePermission.USR_R)) 'r' else '-')
        append(if (has(FilePermission.USR_W)) 'w' else '-')
        append(
            when {
                has(FilePermission.SUID) && has(FilePermission.USR_X) -> 's'
                has(FilePermission.SUID) -> 'S'
                has(FilePermission.USR_X) -> 'x'
                else -> '-'
            }
        )
        append(if (has(FilePermission.GRP_R)) 'r' else '-')
        append(if (has(FilePermission.GRP_W)) 'w' else '-')
        append(
            when {
                has(FilePermission.SGID) && has(FilePermission.GRP_X) -> 's'
                has(FilePermission.SGID) -> 'S'
                has(FilePermission.GRP_X) -> 'x'
                else -> '-'
            }
        )
        append(if (has(FilePermission.OTH_R)) 'r' else '-')
        append(if (has(FilePermission.OTH_W)) 'w' else '-')
        append(
            when {
                has(FilePermission.STICKY) && has(FilePermission.OTH_X) -> 't'
                has(FilePermission.STICKY) -> 'T'
                has(FilePermission.OTH_X) -> 'x'
                else -> '-'
            }
        )
    }
}
