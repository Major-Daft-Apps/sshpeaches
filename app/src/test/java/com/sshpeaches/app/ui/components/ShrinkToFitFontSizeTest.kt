package com.majordaftapps.sshpeaches.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ShrinkToFitFontSizeTest {
    // A text 10 px wide per sp of font size.
    private fun widthAt(size: Float) = (size * 10).toInt()

    @Test
    fun keepsTheFullSizeWhenItFits() {
        assertEquals(22f, shrinkToFitFontSize(22f, 15f, maxWidth = 300, widthAt = ::widthAt))
    }

    @Test
    fun shrinksToTheLargestSizeThatFits() {
        assertEquals(18f, shrinkToFitFontSize(22f, 15f, maxWidth = 185, widthAt = ::widthAt))
    }

    @Test
    fun stopsAtTheMinimumWhenNothingFits() {
        var measured = 0
        val size = shrinkToFitFontSize(22f, 15f, maxWidth = 50) { measured++; widthAt(it) }
        assertEquals(15f, size)
        assertEquals("22 down to 16 measured, 15 taken without measuring", 7, measured)
    }
}
