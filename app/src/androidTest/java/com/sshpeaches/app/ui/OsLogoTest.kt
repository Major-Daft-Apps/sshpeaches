package com.majordaftapps.sshpeaches.app.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.ui.components.OsLogoCache
import com.majordaftapps.sshpeaches.app.ui.components.logoResOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host cards draw the bundled OS logos at once, without an async load that could fail at a cold start. */
@RunWith(AndroidJUnit4::class)
class OsLogoTest {
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun everyOsLogoDrawsAndFillsMostOfItsSquare() {
        OsFamily.entries.forEach { family ->
            val res = OsMetadata.Known(family).logoResOrNull()!!
            val logo = OsLogoCache.get(resources, res, 96)
            assertNotNull("$family didn't draw", logo)
            val pixels = logo!!.toPixelMap()
            var minX = 96; var minY = 96; var maxX = -1; var maxY = -1
            for (x in 0 until 96) for (y in 0 until 96) {
                if (pixels[x, y].alpha > 0.1f) {
                    minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                }
            }
            // Scaled to the square (some SVGs have no viewBox), not a few stray pixels in a corner.
            assertTrue("$family is blank", maxX >= 0)
            assertTrue("$family is only ${maxX - minX + 1}x${maxY - minY + 1} px", maxOf(maxX - minX, maxY - minY) >= 60)
        }
    }

    @Test
    fun anUndetectedOsHasNoLogo() {
        assertNull(OsMetadata.Undetected.logoResOrNull())
    }
}
