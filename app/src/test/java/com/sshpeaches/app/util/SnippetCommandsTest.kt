package com.majordaftapps.sshpeaches.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetCommandsTest {

    @Test
    fun singleLineCommandGetsCarriageReturn() {
        assertEquals("uname -a\r", snippetCommandToTerminalPayload("uname -a"))
        assertEquals("uname -a\r", snippetCommandToTerminalPayload("uname -a\n"))
    }

    @Test
    fun multiLineCommandSendsEnterAfterEachLine() {
        assertEquals("echo a\recho b\r", snippetCommandToTerminalPayload("echo a\necho b\n"))
    }

    @Test
    fun blankCommandIsEmpty() {
        assertEquals("", snippetCommandToTerminalPayload("  \n"))
    }
}
