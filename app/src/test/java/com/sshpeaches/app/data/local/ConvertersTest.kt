package com.majordaftapps.sshpeaches.app.data.local

import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    @Test
    fun toPortType_readsEveryType() {
        for (type in PortForwardType.entries) {
            assertEquals(type, Converters.toPortType(Converters.fromPortType(type)))
        }
    }

    @Test
    fun toPortType_mapsUnknownTypesToLocal() {
        assertEquals(PortForwardType.LOCAL, Converters.toPortType("TUNNEL"))
        assertEquals(PortForwardType.LOCAL, Converters.toPortType(""))
    }

    @Test
    fun toPortType_handlesNullAsLocal() {
        assertEquals(PortForwardType.LOCAL, Converters.toPortType(null))
    }
}

