package com.majordaftapps.sshpeaches.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAppRepositoryImportTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        SshPeachesDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
    private val repository = RoomAppRepository(database)

    @After
    fun tearDown() {
        database.close()
    }

    private fun host(updated: Long, os: OsMetadata, name: String = "box") = HostConnection(
        id = "host-1",
        name = name,
        host = "box.example",
        username = "me",
        preferredAuth = AuthMethod.PASSWORD,
        updatedEpochMillis = updated,
        osMetadata = os
    )

    @Test
    fun aNewerImportWithoutAnOsKeepsTheDetectedOne() = runBlocking {
        val ubuntu = OsMetadata.Known(OsFamily.UBUNTU, "24.04")
        repository.addHost(host(updated = 1, os = ubuntu))

        // A QR code or OpenSSH config: newer, renamed, but it knows nothing about the OS.
        repository.importHost(host(updated = 2, os = OsMetadata.Undetected, name = "renamed"))

        val saved = repository.hosts.first().single()
        assertEquals("renamed", saved.name)
        assertEquals(ubuntu, saved.osMetadata)
    }

    @Test
    fun aNewerImportWithAnOsReplacesIt() = runBlocking {
        repository.addHost(host(updated = 1, os = OsMetadata.Known(OsFamily.UBUNTU, "22.04")))
        val debian = OsMetadata.Known(OsFamily.DEBIAN, "12")

        repository.importHost(host(updated = 2, os = debian))

        assertEquals(debian, repository.hosts.first().single().osMetadata)
    }
}
