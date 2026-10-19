package com.majordaftapps.sshpeaches.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ShellQuotingTest {

    @Test
    fun singleQuotesAreClosedEscapedAndReopened() {
        assertEquals("'plain'", posixShellQuote("plain"))
        assertEquals("'it'\\''s'", posixShellQuote("it's"))
        assertEquals("''", posixShellQuote(""))
    }

    @Test
    fun hostileNamesStayOneLiteralWordInARealShell() {
        val sh = File("/bin/sh")
        assumeTrue(sh.canExecute())
        val names = listOf(
            "x';echo INJECTED #",
            "x'\\'';echo INJECTED #",
            "\$(echo INJECTED)",
            "`echo INJECTED`",
            "a b\tc;echo INJECTED|cat&&echo INJECTED",
            "'\"'\"'",
            "\\",
            "~/notes.txt"
        )
        names.forEach { name ->
            val process = ProcessBuilder(sh.path, "-c", "printf '%s' ${posixShellQuote(name)}")
                .redirectErrorStream(true)
                .start()
            process.waitFor(10, TimeUnit.SECONDS)
            assertEquals(name, process.inputStream.bufferedReader().readText())
        }
    }

    @Test
    fun scpPathsThatLookLikeOptionsAreMadeRelative() {
        assertEquals("'./-rf'", scpRemotePathArgument("-rf"))
        assertEquals("'/srv/-rf'", scpRemotePathArgument("/srv/-rf"))
    }

    @Test
    fun scpRejectsLineBreaks() {
        assertThrows(IllegalArgumentException::class.java) { scpRemotePathArgument("a\nb") }
        assertThrows(IllegalArgumentException::class.java) { scpRemotePathArgument("a\rb") }
        assertThrows(IllegalArgumentException::class.java) { scpRemotePathArgument("") }
    }
}
