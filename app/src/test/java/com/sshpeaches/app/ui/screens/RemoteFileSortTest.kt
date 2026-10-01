package com.majordaftapps.sshpeaches.app.ui.screens

import com.majordaftapps.sshpeaches.app.service.SessionService
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteFileSortTest {

    private val folderA = entry("alpha", directory = true, size = 1, modified = 30)
    private val folderB = entry("beta", directory = true, size = 4, modified = 10)
    private val fileZ = entry("zeta.txt", directory = false, size = 8, modified = 20)
    private val fileM = entry("mu.txt", directory = false, size = 2, modified = 40)

    @Test
    fun foldersFirstThenNameAscending() {
        val sorted = sortRemoteDirectoryEntries(
            entries = listOf(fileZ, folderB, fileM, folderA),
            field = RemoteFileSortField.NAME,
            ascending = true,
            foldersFirst = true
        )
        assertEquals(listOf("alpha", "beta", "mu.txt", "zeta.txt"), sorted.map { it.name })
    }

    @Test
    fun sizeDescendingWithoutFoldersFirst() {
        val sorted = sortRemoteDirectoryEntries(
            entries = listOf(folderA, fileM, folderB, fileZ),
            field = RemoteFileSortField.SIZE,
            ascending = false,
            foldersFirst = false
        )
        assertEquals(listOf("zeta.txt", "beta", "mu.txt", "alpha"), sorted.map { it.name })
    }

    @Test
    fun dateAscendingKeepsFoldersFirst() {
        val sorted = sortRemoteDirectoryEntries(
            entries = listOf(fileZ, folderA, fileM, folderB),
            field = RemoteFileSortField.DATE,
            ascending = true,
            foldersFirst = true
        )
        assertEquals(listOf("beta", "alpha", "zeta.txt", "mu.txt"), sorted.map { it.name })
    }

    private fun entry(
        name: String,
        directory: Boolean,
        size: Long,
        modified: Long
    ): SessionService.RemoteDirectoryEntry =
        SessionService.RemoteDirectoryEntry(
            name = name,
            isDirectory = directory,
            sizeBytes = size,
            modifiedAtEpochMillis = modified
        )
}
