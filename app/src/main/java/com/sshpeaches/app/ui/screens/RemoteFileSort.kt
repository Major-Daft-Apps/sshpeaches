package com.majordaftapps.sshpeaches.app.ui.screens

import com.majordaftapps.sshpeaches.app.service.SessionService

internal enum class RemoteFileSortField {
    NAME,
    SIZE,
    DATE
}

internal fun sortRemoteDirectoryEntries(
    entries: List<SessionService.RemoteDirectoryEntry>,
    field: RemoteFileSortField,
    ascending: Boolean,
    foldersFirst: Boolean
): List<SessionService.RemoteDirectoryEntry> {
    val byName = Comparator<SessionService.RemoteDirectoryEntry> { left, right ->
        val caseInsensitive = String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name)
        if (caseInsensitive != 0) caseInsensitive else left.name.compareTo(right.name)
    }
    val byField = when (field) {
        RemoteFileSortField.NAME -> byName
        RemoteFileSortField.SIZE ->
            compareBy<SessionService.RemoteDirectoryEntry> { it.sizeBytes }.then(byName)
        RemoteFileSortField.DATE ->
            compareBy<SessionService.RemoteDirectoryEntry> {
                it.modifiedAtEpochMillis ?: Long.MIN_VALUE
            }.then(byName)
    }
    val directed = if (ascending) byField else byField.reversed()
    val comparator = if (foldersFirst) {
        Comparator<SessionService.RemoteDirectoryEntry> { left, right ->
            when {
                left.isDirectory == right.isDirectory -> 0
                left.isDirectory -> -1
                else -> 1
            }
        }.then(directed)
    } else {
        directed
    }
    return entries.sortedWith(comparator)
}
