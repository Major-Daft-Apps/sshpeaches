package com.majordaftapps.sshpeaches.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Opens a database written by version 10 of the schema and checks Room upgrades it intact. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun version10HostsGainAttachTmuxAndKeepTheirData() {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            V10_TABLES.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO hosts (id, name, host, port, username, preferredAuth, favorite, osMetadata, notes, " +
                    "defaultMode, attachedForwards, snippets, hasPassword, useMosh, startupScript, " +
                    "backgroundBehavior, infoCommands) VALUES ('h1', 'web', 'example.com', 2222, 'alice', " +
                    "'PASSWORD', 0, 'Undetected', '', 'SSH', '', '', 0, 0, 'uptime', 'INHERIT', '')"
            )
            db.version = 10
        }

        val room = Room.databaseBuilder(context, SshPeachesDatabase::class.java, name)
            .addMigrations(SshPeachesDatabase.MIGRATION_10_11)
            .build()
        try {
            val host = runBlocking { room.hostDao().getById("h1") }!!
            assertEquals("example.com", host.host)
            assertEquals(2222, host.port)
            assertEquals("uptime", host.startupScript)
            assertFalse(host.attachTmux)
            assertEquals(11, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    private companion object {
        // The schema as version 10 shipped it (before `attachTmux`).
        val V10_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `hosts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, `username` TEXT NOT NULL, `preferredAuth` TEXT NOT NULL, `group` TEXT, `createdEpochMillis` INTEGER, `updatedEpochMillis` INTEGER, `lastUsedEpochMillis` INTEGER, `favorite` INTEGER NOT NULL, `osMetadata` TEXT NOT NULL, `notes` TEXT NOT NULL, `defaultMode` TEXT NOT NULL, `attachedForwards` TEXT NOT NULL, `snippets` TEXT NOT NULL, `hasPassword` INTEGER NOT NULL, `useMosh` INTEGER NOT NULL, `preferredIdentityId` TEXT, `preferredForwardId` TEXT, `startupScript` TEXT NOT NULL, `backgroundBehavior` TEXT NOT NULL, `terminalProfileId` TEXT, `infoCommands` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `identities` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `username` TEXT, `group` TEXT, `createdEpochMillis` INTEGER NOT NULL, `updatedEpochMillis` INTEGER, `lastUsedEpochMillis` INTEGER, `favorite` INTEGER NOT NULL, `tags` TEXT NOT NULL, `notes` TEXT NOT NULL, `hasPrivateKey` INTEGER NOT NULL, `keyImportEpochMillis` INTEGER, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `port_forwards` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `group` TEXT, `createdEpochMillis` INTEGER, `updatedEpochMillis` INTEGER, `lastUsedEpochMillis` INTEGER, `type` TEXT NOT NULL, `sourceHost` TEXT NOT NULL, `sourcePort` INTEGER NOT NULL, `destinationHost` TEXT NOT NULL, `destinationPort` INTEGER NOT NULL, `associatedHosts` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `snippets` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `group` TEXT, `createdEpochMillis` INTEGER, `updatedEpochMillis` INTEGER, `lastUsedEpochMillis` INTEGER, `description` TEXT NOT NULL, `command` TEXT NOT NULL, `tags` TEXT NOT NULL, `autoRunHostIds` TEXT NOT NULL, `requireConfirmation` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
