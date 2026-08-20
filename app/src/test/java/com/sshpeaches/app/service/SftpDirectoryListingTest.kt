package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.PathComponents
import net.schmizz.sshj.sftp.RemoteResourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpDirectoryListingTest {

    @Test
    fun `console listing has a constant request budget for many symbolic links`() {
        var canonicalizeCalls = 0
        var listCalls = 0
        var statCalls = 0
        val links = (0 until 1_000).map { index ->
            remoteResource(
                parent = "/srv/files",
                name = "link-$index",
                type = FileMode.Type.SYMLINK
            )
        }

        val result = loadSftpDirectory(
            requestedPath = ".",
            resolveSymbolicLinks = false,
            canonicalize = {
                canonicalizeCalls += 1
                "/srv/files"
            },
            listDirectory = { path ->
                listCalls += 1
                assertEquals("/srv/files", path)
                links
            },
            stat = {
                statCalls += 1
                attributes(FileMode.Type.DIRECTORY)
            }
        )

        assertEquals(1, canonicalizeCalls)
        assertEquals(1, listCalls)
        assertEquals(0, statCalls)
        assertEquals(1_000, result.entries.size)
        assertTrue(result.entries.all { it.isSymbolicLink })
        assertTrue(result.entries.all { it.linkTargetPath == null })
        assertTrue(result.entries.none { it.isBrokenLink })
    }

    @Test
    fun `absolute path keeps one canonicalization request for stable path semantics`() {
        var canonicalizeCalls = 0

        val result = loadSftpDirectory(
            requestedPath = "/home/user/files-link",
            resolveSymbolicLinks = false,
            canonicalize = {
                canonicalizeCalls += 1
                "/srv/files"
            },
            listDirectory = { emptyList() },
            stat = { attributes(FileMode.Type.REGULAR) }
        )

        assertEquals(1, canonicalizeCalls)
        assertEquals("/srv/files", result.path)
    }

    @Test
    fun `scp browser can still resolve symbolic link metadata`() {
        var canonicalizeCalls = 0
        var statCalls = 0
        val link = remoteResource(
            parent = "/srv/files",
            name = "docs",
            type = FileMode.Type.SYMLINK
        )

        val result = loadSftpDirectory(
            requestedPath = ".",
            resolveSymbolicLinks = true,
            canonicalize = { path ->
                canonicalizeCalls += 1
                if (path == ".") "/srv/files" else "/srv/targets/docs"
            },
            listDirectory = { listOf(link) },
            stat = {
                statCalls += 1
                attributes(FileMode.Type.DIRECTORY)
            }
        )

        val entry = result.entries.single()
        assertEquals(2, canonicalizeCalls)
        assertEquals(1, statCalls)
        assertTrue(entry.isSymbolicLink)
        assertEquals("/srv/targets/docs", entry.linkTargetPath)
        assertEquals(true, entry.linkTargetIsDirectory)
        assertFalse(entry.isBrokenLink)
    }

    @Test
    fun `new refresh token distinguishes an unchanged listing response`() {
        val entry = SessionService.RemoteDirectoryEntry(
            name = "readme.txt",
            isDirectory = false,
            sizeBytes = 42L
        )
        val first = SessionService.RemoteDirectorySnapshot(
            path = "/srv/files",
            entries = listOf(entry),
            refreshToken = 100L
        )
        val second = first.copy(refreshToken = 101L)

        assertNotEquals(sftpDirectoryRefreshKey(first), sftpDirectoryRefreshKey(second))
        assertEquals("", sftpDirectoryRefreshKey(null))
        assertNull(first.entries.single().linkTargetPath)
    }

    @Test
    fun `only scp browser listings resolve link metadata`() {
        assertFalse(shouldResolveSftpLinkMetadata(ConnectionMode.SFTP))
        assertTrue(shouldResolveSftpLinkMetadata(ConnectionMode.SCP))
        assertFalse(shouldResolveSftpLinkMetadata(ConnectionMode.SSH))
        assertFalse(shouldResolveSftpLinkMetadata(null))
    }

    private fun remoteResource(
        parent: String,
        name: String,
        type: FileMode.Type
    ): RemoteResourceInfo = RemoteResourceInfo(
        PathComponents(parent, name, "/"),
        attributes(type)
    )

    private fun attributes(type: FileMode.Type): FileAttributes =
        FileAttributes.Builder()
            .withType(type)
            .withPermissions(if (type == FileMode.Type.DIRECTORY) 0b111101101 else 0b110100100)
            .withSize(42L)
            .build()
}
