package com.majordaftapps.sshpeaches.app.data.local

import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    @Test
    fun toPortType_mapsLegacyRemoteToLocal() {
        assertEquals(PortForwardType.LOCAL, Converters.toPortType("REMOTE"))
    }

    @Test
    fun toPortType_mapsLegacyDynamicToLocal() {
        assertEquals(PortForwardType.LOCAL, Converters.toPortType("DYNAMIC"))
    }

    @Test
    fun toPortType_handlesNullAsLocal() {
        assertEquals(PortForwardType.LOCAL, Converters.toPortType(null))
    }
}

