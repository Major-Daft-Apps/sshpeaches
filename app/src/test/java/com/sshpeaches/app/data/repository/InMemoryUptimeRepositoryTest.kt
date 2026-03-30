package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryUptimeRepositoryTest {

    @Test
    fun addHostAndUpdateConfig_publishUpdatedConfig() = runBlocking {
        val repository = InMemoryUptimeRepository()

        repository.addHost("host-1")
        repository.updateConfig(
            hostId = "host-1",
            method = UptimeCheckMethod.ICMP,
            port = 2222,
            intervalMinutes = 30,
            enabled = true
        )

        val configs = repository.configs.first()
        assertEquals(1, configs.size)
        assertEquals(UptimeCheckMethod.ICMP, configs.single().method)
        assertEquals(2222, configs.single().port)
        assertEquals(30, configs.single().intervalMinutes)
    }

    @Test
    fun setEnabledAndRemoveHost_publishLatestState() = runBlocking {
        val repository = InMemoryUptimeRepository()

        repository.addHost("host-1")
        repository.setEnabled("host-1", false)

        val disabledConfig = repository.configs.first().single()
        assertFalse(disabledConfig.enabled)

        repository.removeHost("host-1")
        assertTrue(repository.configs.first().isEmpty())
    }
}
