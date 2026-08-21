package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardIconPackTest {

    @Test
    fun byId_keepsFnTextOnlyAndBackAsAnIcon() {
        assertNull(KeyboardIconPack.byId("fn"))
        assertNull(KeyboardIconPack.byId("fn_active"))
        assertNotNull(KeyboardIconPack.byId("fn_back"))
    }
}