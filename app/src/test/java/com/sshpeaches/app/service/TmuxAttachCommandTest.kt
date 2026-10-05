package com.majordaftapps.sshpeaches.app.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs the real attach command in a local sh, with stand-ins for tmux and the login shell. */
class TmuxAttachCommandTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun attachesToTheSshpeachesTmuxSessionWhenTmuxIsInstalled() {
        val bin = temp.newFolder("bin")
        script(File(bin, "tmux"), "echo \"tmux $*\"")

        assertEquals("tmux new-session -A -s sshpeaches", run(path = bin.absolutePath))
    }

    @Test
    fun fallsBackToTheLoginShellWithoutTmux() {
        val bin = temp.newFolder("bin")
        val loginShell = script(File(temp.root, "login-shell"), "echo \"login shell $*\"")

        assertEquals("login shell -l", run(path = bin.absolutePath, shell = loginShell.absolutePath))
    }

    private fun run(path: String, shell: String = "/bin/false"): String {
        val sh = File("/bin/sh")
        assumeTrue("needs a POSIX sh", sh.canExecute())
        // `command -v` must not find a real tmux, so PATH holds only the stand-ins plus sh itself.
        java.nio.file.Files.createSymbolicLink(File(path, "sh").toPath(), sh.toPath())
        val process = ProcessBuilder(sh.absolutePath, "-c", SessionService.TMUX_ATTACH_COMMAND)
            .redirectErrorStream(true)
            .apply {
                environment()["PATH"] = path
                environment()["SHELL"] = shell
            }
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return output
    }

    private fun script(file: File, body: String): File {
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        return file
    }
}
