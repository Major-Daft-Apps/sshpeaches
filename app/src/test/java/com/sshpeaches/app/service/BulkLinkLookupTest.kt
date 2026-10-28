package com.majordaftapps.sshpeaches.app.service

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BulkLinkLookupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun run(directory: File, names: List<String>, command: String = bulkLinkLookupCommand(directory.path)): String {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
        process.outputStream.use { input -> names.forEach { input.write("$it\n".toByteArray()) } }
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        return output
    }

    private fun linkFolder(name: String = "it's a folder"): Pair<File, List<String>> {
        val dir = temp.newFolder(name)
        File(dir, "target.txt").writeText("x")
        File(dir, "target dir").mkdirs()
        val links = mapOf(
            "to-file" to "target.txt",
            "to dir" to "target dir",
            "broken" to "nowhere",
            "-dash" to "target.txt",
            "quote'link" to "target dir",
            "\$(touch pwned)" to "target.txt"
        )
        links.forEach { (link, target) -> Files.createSymbolicLink(File(dir, link).toPath(), File(target).toPath()) }
        return dir to links.keys.toList()
    }

    private fun assertLookup(found: Map<String, LinkInfo>, expectTargets: Boolean) {
        assertEquals(6, found.size)
        assertTrue(found.getValue("to dir").isDirectory)
        assertTrue(found.getValue("quote'link").isDirectory)
        assertFalse(found.getValue("to-file").isDirectory)
        assertFalse(found.getValue("-dash").broken)
        assertTrue(found.getValue("broken").broken)
        assertFalse(found.getValue("\$(touch pwned)").broken)
        if (expectTargets) assertEquals("target dir", found.getValue("to dir").target)
    }

    @Test
    fun findReportsEveryLinkWithItsTarget() {
        val (dir, names) = linkFolder()
        val probe = ProcessBuilder("sh", "-c", "find . -maxdepth 0 -printf ''").start()
        assumeTrue("needs GNU find", probe.waitFor() == 0)
        assertLookup(parseBulkLinkLookup(run(dir, names)), expectTargets = true)
        assertFalse("a name never runs as a command", File(dir, "pwned").exists())
    }

    @Test
    fun shellLoopWithoutFindPrintfReportsTypes() {
        val (dir, names) = linkFolder()
        // The branch busybox, BSD and macOS take: make find's -printf probe fail.
        val command = bulkLinkLookupCommand(dir.path).replace("find . -maxdepth 0 -printf ''", "false")
        assertLookup(parseBulkLinkLookup(run(dir, names, command)), expectTargets = false)
        assertFalse("a name never runs as a command", File(dir, "pwned").exists())
        assertFalse(File(temp.root, "pwned").exists())
    }

    @Test
    fun namesTheCommandCantCarryAreLeftToSftp() {
        assertFalse(canLookUpInBulk("a\nb"))
        assertFalse(canLookUpInBulk("a\tb"))
        assertTrue(canLookUpInBulk("a b 'c' \$(d)"))
    }

    @Test
    fun malformedLinesAreIgnored() {
        val parsed = parseBulkLinkLookup("d\tok\tt\nbogus line\nq\tunknown\tx\n\nx\tgone\t\n")
        assertEquals(setOf("ok", "gone"), parsed.keys)
        assertTrue(parsed.getValue("gone").broken)
    }
}
