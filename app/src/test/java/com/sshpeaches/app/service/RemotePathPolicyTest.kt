package com.majordaftapps.sshpeaches.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemotePathPolicyTest {

    @Test
    fun `path normalization exposes exact protected roots`() {
        assertEquals("/etc", normalizeRemotePathLexically("//var/../etc/"))
        assertEquals("/", normalizeRemotePathLexically("/tmp/.."))
        assertEquals("../notes.txt", normalizeRemotePathLexically("../notes.txt"))
    }

    @Test
    fun `delete rejects protected source but permits descendants`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePathMutation("delete", "/var/")
        }

        validateRemotePathMutation("delete", "/var/tmp/application-cache")
    }

    @Test
    fun `rename rejects protected source and destination`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePathMutation("move", "/etc", "/srv/archive")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePathMutation("move", "/srv/archive", "/etc/./")
        }
    }

    @Test
    fun `transfer paths preserve meaningful whitespace`() {
        assertEquals(" /reports/final copy .txt ", preserveRemoteTransferPath(" /reports/final copy .txt "))
        assertEquals("", preserveRemoteTransferPath("   "))
    }

    @Test
    fun `console and browser deletion operations have distinct semantics`() {
        assertEquals(RemoteDeleteMode.NON_RECURSIVE, remoteDeleteMode("delete_file"))
        assertEquals(RemoteDeleteMode.RECURSIVE, remoteDeleteMode("delete"))
    }
}
