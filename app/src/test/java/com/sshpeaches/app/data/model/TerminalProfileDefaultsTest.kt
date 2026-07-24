package com.majordaftapps.sshpeaches.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalProfileDefaultsTest {

    @Test
    fun builtInProfiles_haveCompleteDistinctAnsiPalettes() {
        TerminalProfileDefaults.builtInProfiles.forEach { profile ->
            assertEquals("${profile.name} palette size", 16, profile.ansiColors.size)
        }

        val distinctPalettes = TerminalProfileDefaults.builtInProfiles.map { it.ansiColors }.distinct()
        assertTrue("Built-in themes should not all share the fallback palette", distinctPalettes.size > 8)
    }

    @Test
    fun defaultDarkProfile_doesNotMapAnsiBlackToItsBlackBackground() {
        val profile = TerminalProfileDefaults.profileById(TerminalProfileDefaults.DEFAULT_PROFILE_ID)!!

        assertEquals("#000000", profile.backgroundHex)
        assertNotEquals(profile.backgroundHex, profile.ansiColors.first())
        assertTrue(contrastRatio(profile.backgroundHex, profile.ansiColors.first()) >= 4.5)
    }

    private fun contrastRatio(first: String, second: String): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(hex: String): Double {
        val rgb = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
        val linear = rgb.map { value ->
            if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }
}
