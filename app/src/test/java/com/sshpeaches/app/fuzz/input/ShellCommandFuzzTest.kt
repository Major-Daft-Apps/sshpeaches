package com.majordaftapps.sshpeaches.app.fuzz.input

import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.util.snippetCommandToTerminalPayload
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Snippet payloads and the tmux attach command.
 *
 * Note: neither takes a user-chosen name. The tmux session name is the constant "sshpeaches", and
 * snippets are sent verbatim as typed lines (no quoting is applied, by design). The checks are
 * therefore (1) the snippet payload is exactly the user's lines, each ended by one CR, and (2) the
 * attach command parses (POSIX-sh oracle) to the intended argv and survives hostile $SHELL values.
 */
class ShellCommandFuzzTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ------------------------------------------------------------------ snippets

    @Test
    fun snippetPayload_isTheUserLinesEachEndedByCr() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(10_000) { i ->
                val lines = List(r.nextInt(1, 5)) { Fuzz.text(r, 12).replace("\r", "").replace("\n", "") }
                val sep = listOf("\n", "\r\n", "\r").random(r)
                val command = lines.joinToString(sep) + if (r.nextBoolean()) sep else ""
                val payload = snippetCommandToTerminalPayload(command)
                val label = "seed=$seed i=$i cmd=${Fuzz.show(command)} payload=${Fuzz.show(payload)}"
                assertFalse("$label contains LF", '\n' in payload)
                val expectedLines = lines.joinToString("\n").trimEnd().split('\n')
                if (lines.joinToString("\n").isBlank()) {
                    assertEquals(label, "", payload)
                } else {
                    assertTrue(label, payload.endsWith("\r"))
                    assertEquals(label, expectedLines, payload.dropLast(1).split('\r'))
                }
            }
        }
    }

    @Test
    fun snippetPayload_hugeInput_isFast() {
        val big = buildString { repeat(50_000) { append("echo line $it\r\n") } }
        val payload = Fuzz.timed("50k-line snippet", 3_000) { snippetCommandToTerminalPayload(big) }
        assertEquals(50_000, payload.count { it == '\r' })
    }

    // ------------------------------------------------------------------ tmux attach command

    /** Minimal POSIX sh word splitter: single quotes, double quotes (with \ escapes), backslash. */
    private fun shWords(s: String): List<String> {
        val words = mutableListOf<String>()
        val cur = StringBuilder()
        var inWord = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\'' -> {
                    val end = s.indexOf('\'', i + 1)
                    require(end >= 0) { "unterminated single quote" }
                    cur.append(s, i + 1, end); inWord = true; i = end
                }
                c == '"' -> {
                    i++
                    while (s[i] != '"') {
                        if (s[i] == '\\' && s[i + 1] in "\"\\$`\n") { i++ }
                        cur.append(s[i]); i++
                    }
                    inWord = true
                }
                c == '\\' -> { cur.append(s[i + 1]); i++; inWord = true }
                c == ' ' || c == '\t' -> if (inWord) { words += cur.toString(); cur.setLength(0); inWord = false }
                else -> { cur.append(c); inWord = true }
            }
            i++
        }
        if (inWord) words += cur.toString()
        return words
    }

    @Test
    fun tmuxAttachCommand_parsesToShDashCScript() {
        val argv = shWords(SessionService.TMUX_ATTACH_COMMAND)
        assertEquals(3, argv.size)
        assertEquals(listOf("sh", "-c"), argv.take(2))
        val script = argv[2]
        assertTrue(script.contains("exec tmux new-session -A -s sshpeaches"))
        assertTrue("\$SHELL must be double-quoted", script.contains("exec \"\${SHELL:-/bin/sh}\" -l"))
    }

    private fun runAttach(shellValue: String, path: String): Pair<String, Int> {
        val process = ProcessBuilder("/bin/sh", "-c", SessionService.TMUX_ATTACH_COMMAND)
            .redirectErrorStream(true)
            .directory(temp.root)
            .apply {
                environment()["PATH"] = path
                environment()["SHELL"] = shellValue
            }
            .start()
        process.outputStream.close()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        val out = process.inputStream.bufferedReader().readText().trim()
        return out to (if (finished) process.exitValue() else -999)
    }

    @Test
    fun tmuxAttachCommand_hostileShellValues_runExactlyThatPath_noInjection() {
        val sh = File("/bin/sh")
        assumeTrue("needs a POSIX sh", sh.canExecute())
        val bin = temp.newFolder("bin")
        Files.createSymbolicLink(File(bin, "sh").toPath(), sh.toPath())
        // PATH holds only sh, so injections use builtins: ':>pwned' creates the marker in the cwd.
        val marker = File(temp.root, "pwned")
        val r = Random(Fuzz.SEEDS[0])
        val names = listOf(
            "plain", "with space", "it's", "dq\"x", "\$(:>pwned)", "`:>pwned`", "semi;:>pwned", "a&&:>pwned",
            "glob*?[a]", "back\\slash", "-l", "--", "~tilde", "new\nline", "\${IFS}x", "dollar\$HOME"
        ) + List(15) { Fuzz.text(r, 10).filter { it.code in 1..127 && it != '/' }.ifEmpty { "x" } }
        names.forEachIndexed { i, name ->
            val dir = File(temp.root, "d$i").apply { mkdirs() }
            val shellFile = File(dir, name)
            val created = runCatching { shellFile.writeText("#!/bin/sh\necho \"LOGIN \$*\"\n"); shellFile.setExecutable(true) }.isSuccess
            if (!created) return@forEachIndexed
            val (out, code) = runAttach(shellFile.absolutePath, bin.absolutePath)
            assertEquals("SHELL=${Fuzz.show(shellFile.absolutePath)} exit=$code out=${Fuzz.show(out)}", "LOGIN -l", out)
            assertFalse("injection via SHELL=${Fuzz.show(name)}", marker.exists())
        }
        // A SHELL that is itself a command substitution must not be evaluated.
        runAttach("\$(:>pwned)", bin.absolutePath)
        runAttach("; :>pwned", bin.absolutePath)
        runAttach("`:>pwned`", bin.absolutePath)
        assertFalse("SHELL value was evaluated", marker.exists())
    }
}
