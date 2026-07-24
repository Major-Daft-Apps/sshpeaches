package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboardIconPackTest {

    @Test
    fun byId_containsDedicatedFnIcons() {
        assertNotNull(KeyboardIconPack.byId("fn"))
        assertNotNull(KeyboardIconPack.byId("fn_active"))
    }
}
