package com.majordaftapps.sshpeaches.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A host's logo follows its saved OS; a session that could only ask `uname` must not turn a distribution into "Linux". */
class OsMetadataAfterUnameTest {
    private val plainLinux = OsMetadata.Known(OsFamily.GENERIC, "6.8")

    @Test
    fun aKnownDistributionIsKeptWhenOnlyUnameAnswered() {
        for (family in listOf(OsFamily.UBUNTU, OsFamily.DEBIAN, OsFamily.FEDORA, OsFamily.SUSE, OsFamily.MINT, OsFamily.ARCH, OsFamily.REDHAT)) {
            assertNull("$family", OsMetadata.Known(family, "24.04").afterUnameOnly(plainLinux))
        }
    }

    @Test
    fun aHostNotKnownYetTakesWhatUnameSays() {
        assertEquals(plainLinux, OsMetadata.Undetected.afterUnameOnly(plainLinux))
        assertEquals(plainLinux, OsMetadata.Known(OsFamily.GENERIC, "6.1").afterUnameOnly(plainLinux))
        assertEquals(plainLinux, OsMetadata.Custom("box").afterUnameOnly(plainLinux))
    }

    @Test
    fun unameIsBelievedWhenItNamesAnotherKindOfSystem() {
        val mac = OsMetadata.Known(OsFamily.MAC, "24")
        assertEquals(mac, OsMetadata.Known(OsFamily.UBUNTU).afterUnameOnly(mac))
        // A Mac that now answers "Linux" was replaced by another machine.
        assertEquals(plainLinux, mac.afterUnameOnly(plainLinux))
    }

    @Test
    fun noAnswerChangesNothing() {
        assertNull(OsMetadata.Known(OsFamily.UBUNTU).afterUnameOnly(null))
        assertNull(OsMetadata.Undetected.afterUnameOnly(null))
    }
}
