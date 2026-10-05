package com.majordaftapps.sshpeaches.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteFileToolsTest {

    @Test
    fun breadcrumbsForAbsolutePaths() {
        assertEquals(
            listOf("/" to "/", "home" to "/home", "tester" to "/home/tester", "projects" to "/home/tester/projects"),
            remoteBreadcrumbs("/home/tester/projects/")
        )
        assertEquals(listOf("/" to "/"), remoteBreadcrumbs("/"))
        assertEquals(emptyList<Pair<String, String>>(), remoteBreadcrumbs("."))
    }

    @Test
    fun modesRoundTripThroughLsStyleSummaries() {
        assertEquals("644", formatMode(permissionSummaryToMode("-rw-r--r--")!!))
        assertEquals("755", formatMode(permissionSummaryToMode("drwxr-xr-x")!!))
        assertEquals("4755", formatMode(permissionSummaryToMode("-rwsr-xr-x")!!))
        assertEquals("1777", formatMode(permissionSummaryToMode("drwxrwxrwt")!!))
        assertEquals("2640", formatMode(permissionSummaryToMode("-rw-r-S---")!!))
        assertNull(permissionSummaryToMode(""))
        assertNull(permissionSummaryToMode("garbage!!"))
    }

    @Test
    fun parsesOnlyThreeOrFourOctalDigits() {
        assertEquals(0b110_100_100, parseMode("644"))
        assertEquals(0b100_111_101_101, parseMode("4755"))
        assertNull(parseMode("68"))
        assertNull(parseMode("648"))
        assertNull(parseMode("07555"))
    }
}
