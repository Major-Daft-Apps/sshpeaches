package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RemoteFileTypeIconTest {

    @Test
    fun foldersAndBrokenLinksUseDedicatedIcons() {
        assertEquals(
            RemoteFileKind.FOLDER,
            remoteFileKind("docs", true, false, false, null)
        )
        assertEquals(Icons.Default.Folder, remoteFileKindIcon(RemoteFileKind.FOLDER))
        assertEquals(
            RemoteFileKind.LINK,
            remoteFileKind("missing", false, true, true, null)
        )
        assertEquals(Icons.Default.Link, remoteFileKindIcon(RemoteFileKind.LINK))
    }

    @Test
    fun commonFilesMapToTypeIconsNotExtensionText() {
        assertEquals(RemoteFileKind.IMAGE, kindOf("photo.PNG"))
        assertEquals(Icons.Default.Image, remoteFileKindIcon(RemoteFileKind.IMAGE))
        assertEquals(RemoteFileKind.VIDEO, kindOf("clip.mkv"))
        assertEquals(Icons.Default.Movie, remoteFileKindIcon(RemoteFileKind.VIDEO))
        assertEquals(RemoteFileKind.AUDIO, kindOf("track.flac"))
        assertEquals(Icons.Default.AudioFile, remoteFileKindIcon(RemoteFileKind.AUDIO))
        assertEquals(RemoteFileKind.PDF, kindOf("manual.pdf"))
        assertEquals(Icons.AutoMirrored.Filled.Article, remoteFileKindIcon(RemoteFileKind.PDF))
        assertEquals(RemoteFileKind.SPREADSHEET, kindOf("budget.xlsx"))
        assertEquals(Icons.Default.TableChart, remoteFileKindIcon(RemoteFileKind.SPREADSHEET))
        assertEquals(RemoteFileKind.ARCHIVE, kindOf("backup.tar.gz"))
        assertEquals(Icons.Default.Archive, remoteFileKindIcon(RemoteFileKind.ARCHIVE))
        assertEquals(RemoteFileKind.CODE, kindOf("Main.kt"))
        assertEquals(Icons.Default.Code, remoteFileKindIcon(RemoteFileKind.CODE))
        assertEquals(RemoteFileKind.SCRIPT, kindOf("setup.sh"))
        assertEquals(Icons.Default.Terminal, remoteFileKindIcon(RemoteFileKind.SCRIPT))
        assertEquals(RemoteFileKind.GENERIC, kindOf("no-extension"))
        assertEquals(Icons.AutoMirrored.Filled.InsertDriveFile, remoteFileKindIcon(RemoteFileKind.GENERIC))
        assertNotEquals(Icons.AutoMirrored.Filled.InsertDriveFile, remoteFileKindIcon(RemoteFileKind.PDF))
        assertNotEquals(Icons.AutoMirrored.Filled.InsertDriveFile, remoteFileKindIcon(RemoteFileKind.IMAGE))
    }

    @Test
    fun dotfilesWithoutARealExtensionStayGeneric() {
        assertEquals("", fileExtensionKey(".bashrc"))
        assertEquals(RemoteFileKind.GENERIC, kindOf(".bashrc"))
    }

    @Test
    fun namedSymlinksFollowTheTargetFileType() {
        assertEquals(
            RemoteFileKind.IMAGE,
            remoteFileKind("latest.jpg", false, true, false, false)
        )
        assertEquals(
            RemoteFileKind.LINK,
            remoteFileKind("current", false, true, false, null)
        )
    }

    private fun kindOf(name: String): RemoteFileKind =
        remoteFileKind(
            name = name,
            isDirectory = false,
            isSymbolicLink = false,
            isBrokenLink = false,
            linkTargetIsDirectory = null
        )
}
