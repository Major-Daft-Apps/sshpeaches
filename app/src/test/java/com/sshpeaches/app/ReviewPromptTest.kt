package com.majordaftapps.sshpeaches.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_800_000_000_000L

    @Test
    fun asksOnceAfterFiveSessionsOverThreeDays() {
        assertTrue(ReviewPrompt.shouldAsk(5, start, start + 3 * day, alreadyAsked = false))
        assertFalse("too soon", ReviewPrompt.shouldAsk(50, start, start + 2 * day, alreadyAsked = false))
        assertFalse("too few sessions", ReviewPrompt.shouldAsk(4, start, start + 30 * day, alreadyAsked = false))
        assertFalse("already asked", ReviewPrompt.shouldAsk(9, start, start + 9 * day, alreadyAsked = true))
        assertFalse("no first use recorded", ReviewPrompt.shouldAsk(9, 0L, start, alreadyAsked = false))
    }
}
