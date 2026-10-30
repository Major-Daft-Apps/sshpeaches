package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentListingsTest {
    @Test
    fun keepsTheLastFiveFolders() {
        val recent = RecentListings<String>(5)
        (1..6).forEach { recent.put("/d$it", "listing $it") }
        assertNull("the oldest folder was dropped", recent.get("/d1"))
        assertEquals("listing 6", recent.get("/d6"))
        assertEquals(listOf("/d2", "/d3", "/d4", "/d5", "/d6"), recent.paths().sorted())
    }

    @Test
    fun goingBackToAFolderKeepsIt() {
        val recent = RecentListings<String>(5)
        (1..5).forEach { recent.put("/d$it", "listing $it") }
        recent.get("/d1")
        recent.put("/d6", "listing 6")
        assertEquals("listing 1", recent.get("/d1"))
        assertNull(recent.get("/d2"))
    }

    @Test
    fun aTrailingSlashIsTheSameFolder() {
        val recent = RecentListings<String>(5)
        recent.put("/srv/data/", "data")
        assertEquals("data", recent.get("/srv/data"))
        recent.put("/", "root")
        assertEquals("root", recent.get("/"))
        assertEquals("root", recent.get("//"))
    }

    @Test
    fun aNewerListingReplacesTheOlder() {
        val recent = RecentListings<String>(5)
        recent.put("/a", "old")
        recent.put("/a", "new")
        assertEquals("new", recent.get("/a"))
        assertEquals(1, recent.paths().size)
    }

    @Test
    fun theHomeFolderIsNeverPushedOut() {
        val recent = RecentListings<String>(5)
        recent.putHome("/home/me", "home")
        (1..8).forEach { recent.put("/d$it", "listing $it") }
        assertEquals("home", recent.get("/home/me"))
        assertEquals("home", recent.get("/home/me/"))
        assertEquals("\".\" is home", "home", recent.get("."))
        assertEquals(5, recent.paths().size)
        recent.put("/home/me", "home again")
        assertEquals("home again", recent.get("."))
    }
}
