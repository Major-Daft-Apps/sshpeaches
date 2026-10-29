package com.majordaftapps.sshpeaches.app.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostPortsTest {
    @Test
    fun rangesAndSinglePortsParse() {
        assertEquals(60000..61000, SftpTransferSettings.parseBoostPorts("60000-61000"))
        assertEquals(4500..4510, SftpTransferSettings.parseBoostPorts(" 4500 - 4510 "))
        assertEquals(4500..4500, SftpTransferSettings.parseBoostPorts("4500"))
        assertEquals(60000..61000, SftpTransferSettings().boostPortRange)
    }

    @Test
    fun blankMeansAnyPort() {
        assertNull(SftpTransferSettings.parseBoostPorts(""))
        assertTrue(SftpTransferSettings.isValidBoostPorts(" "))
        assertNull(SftpTransferSettings(boostPorts = "").boostPortRange)
    }

    @Test
    fun badRangesAreRefused() {
        for (text in listOf("61000-60000", "80", "1023-2000", "60000-70000", "65536", "60000-", "-61000", "6000O", "1-2-3")) {
            assertFalse(text, SftpTransferSettings.isValidBoostPorts(text))
            assertNull(text, SftpTransferSettings.parseBoostPorts(text))
        }
    }
}
