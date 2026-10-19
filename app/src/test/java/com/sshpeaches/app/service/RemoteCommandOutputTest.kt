package com.majordaftapps.sshpeaches.app.service

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.connection.channel.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RemoteCommandOutputTest {

    private fun channel(eof: () -> Boolean): Channel =
        Proxy.newProxyInstance(Channel::class.java.classLoader, arrayOf(Channel::class.java)) { _, method, _ ->
            when (method.name) {
                "isEOF" -> eof()
                "isOpen" -> !eof()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Channel

    /** A stream that always has more to give and never ends, like a server streaming forever. */
    private val endless = object : InputStream() {
        override fun read(): Int = 'y'.code
        override fun available(): Int = 1024
    }

    @Test
    fun readsEverythingUpToTheEnd() {
        val output = collectCommandOutput(channel { true }, ByteArrayInputStream("ID=ubuntu\n".toByteArray()), 1_000)
        assertEquals("ID=ubuntu\n", output)
    }

    @Test
    fun endlessOutputIsCutAtTheLimit() {
        val output = collectCommandOutput(channel { false }, endless, waitMillis = 60_000, limit = 10_000)
        assertEquals(10_000, output.length)
    }

    @Test
    fun aServerThatNeverEndsTheOutputDoesNotStallTheRead() {
        var now = 0L
        val silent = object : InputStream() {
            override fun read(): Int = error("must not block on read")
            override fun available(): Int = 0
        }
        val output = collectCommandOutput(
            channel { false }, silent, waitMillis = 2_000,
            nanoTime = { now }, pause = { now += it * 1_000_000L }
        )
        assertEquals("", output)
        assertTrue(now >= 2_000_000_000L)
    }

    @Test
    fun moshStopOnlyKillsAMoshServer() {
        val sleep = listOf("/bin/sleep", "/usr/bin/sleep").map(::File).firstOrNull { it.canExecute() }
        assumeTrue(sleep != null && File("/bin/sh").canExecute())
        val dir = Files.createTempDirectory("mosh-stop").toFile()
        val fakeServer = File(dir, "mosh-server").also { Files.createSymbolicLink(it.toPath(), sleep!!.toPath()) }
        // The shell prints its pid, then becomes the program: the pid stays the same.
        fun launch(program: File): Pair<Process, Int> {
            val process = ProcessBuilder("/bin/sh", "-c", "echo $$; exec '${program.path}' 30").start()
            return process to process.inputStream.bufferedReader().readLine().trim().toInt()
        }
        val (other, otherPid) = launch(sleep!!)
        val (server, serverPid) = launch(fakeServer)
        try {
            fun stop(pid: Int) = ProcessBuilder("/bin/sh", "-c", moshServerStopCommand(pid)).start()
                .waitFor(10, TimeUnit.SECONDS)
            stop(otherPid)
            stop(serverPid)
            assertTrue("mosh-server stopped", server.waitFor(10, TimeUnit.SECONDS))
            assertFalse("a reused pid is left alone", other.waitFor(500, TimeUnit.MILLISECONDS))
        } finally {
            other.destroyForcibly()
            server.destroyForcibly()
            dir.deleteRecursively()
        }
    }
}
