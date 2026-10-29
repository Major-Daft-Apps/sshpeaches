package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostListingTest {

    private val json = """
        {"path":"/srv/files","entries":[
          {"n":"b.txt","t":"f","s":5,"m":1700000000,"p":420},
          {"n":"A-dir","t":"d","s":4096,"m":1700000001,"p":493},
          {"n":"to-dir","t":"l","s":7,"m":0,"p":511,"lt":"A-dir","ld":true},
          {"n":"gone","t":"l","s":7,"m":0,"p":511,"lt":"missing","lb":true},
          {"n":"setuid","t":"f","s":1,"m":1,"p":2541},
          {"n":"../escape","t":"f","s":1,"m":1,"p":420},
          {"n":"","t":"f","s":1,"m":1,"p":420}
        ]}
    """.trimIndent()

    @Test
    fun browserListingMatchesWhatSftpGives() {
        val listing = parseBoostListing(json, resolveSymbolicLinks = true)
        assertEquals("/srv/files", listing.path)
        assertEquals(listOf("A-dir", "b.txt", "gone", "setuid", "to-dir"), listing.entries.map { it.name })
        val dir = listing.entries.single { it.name == "A-dir" }
        assertTrue(dir.isDirectory)
        assertEquals("drwxr-xr-x", dir.permissionSummary)
        assertEquals("/srv/files/A-dir", dir.absolutePath)
        assertEquals(1700000001000L, dir.modifiedAtEpochMillis)
        val file = listing.entries.single { it.name == "b.txt" }
        assertEquals("-rw-r--r--", file.permissionSummary)
        assertEquals(5L, file.sizeBytes)
        val link = listing.entries.single { it.name == "to-dir" }
        assertTrue(link.isSymbolicLink)
        assertFalse(link.isDirectory)
        assertEquals("A-dir", link.linkTargetPath)
        assertEquals(true, link.linkTargetIsDirectory)
        assertNull(link.modifiedAtEpochMillis)
        val broken = listing.entries.single { it.name == "gone" }
        assertTrue(broken.isBrokenLink)
        assertNull(broken.linkTargetPath)
        // 2541 is octal 04755: setuid on an executable.
        assertEquals("-rwsr-xr-x", listing.entries.single { it.name == "setuid" }.permissionSummary)
    }

    @Test
    fun consoleListingCarriesNoLinkDetails() {
        val listing = parseBoostListing(json, resolveSymbolicLinks = false)
        val link = listing.entries.single { it.name == "to-dir" }
        assertTrue(link.isSymbolicLink)
        assertNull(link.linkTargetPath)
        assertEquals(false, link.linkTargetIsDirectory)
        assertFalse(listing.entries.single { it.name == "gone" }.isBrokenLink)
    }

    @Test
    fun rootPathsJoinWithoutADoubleSlash() {
        val listing = parseBoostListing("""{"path":"/","entries":[{"n":"etc","t":"d","s":0,"m":1,"p":493}]}""", true)
        assertEquals("/etc", listing.entries.single().absolutePath)
    }

    @Test
    fun permissionSummaryHandlesEveryBit() {
        assertEquals("-rwSr-Sr-T", boostPermissionSummary("f", 0x800 or 0x400 or 0x200 or 0x180 or 0x20 or 0x4))
        assertEquals("?---------", boostPermissionSummary("o", 0))
    }
}
