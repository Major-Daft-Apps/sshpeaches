package com.majordaftapps.sshpeaches.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HostEntity::class,
        IdentityEntity::class,
        PortForwardEntity::class,
        SnippetEntity::class,
        HostUptimeConfigEntity::class,
        HostUptimeSampleEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SshPeachesDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun snippetDao(): SnippetDao
    abstract fun hostUptimeConfigDao(): HostUptimeConfigDao
    abstract fun hostUptimeSampleDao(): HostUptimeSampleDao

    companion object {
        @Volatile private var instance: SshPeachesDatabase? = null

        fun get(context: Context): SshPeachesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SshPeachesDatabase::class.java,
                    "sshpeaches.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_8)
                    .addMigrations(MIGRATION_7_8)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createUptimeTables(database)
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE identities ADD COLUMN `group` TEXT")
                database.execSQL("ALTER TABLE port_forwards ADD COLUMN `group` TEXT")
                database.execSQL("ALTER TABLE snippets ADD COLUMN `group` TEXT")

                database.execSQL("ALTER TABLE hosts ADD COLUMN `createdEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE hosts ADD COLUMN `updatedEpochMillis` INTEGER")
                database.execSQL(
                    "UPDATE hosts SET createdEpochMillis = COALESCE(lastUsedEpochMillis, CAST(strftime('%s','now') AS INTEGER) * 1000)"
                )
                database.execSQL("UPDATE hosts SET updatedEpochMillis = createdEpochMillis")

                database.execSQL("ALTER TABLE identities ADD COLUMN `updatedEpochMillis` INTEGER")
                database.execSQL("UPDATE identities SET updatedEpochMillis = createdEpochMillis")

                database.execSQL("ALTER TABLE port_forwards ADD COLUMN `createdEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE port_forwards ADD COLUMN `updatedEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE port_forwards ADD COLUMN `lastUsedEpochMillis` INTEGER")
                database.execSQL(
                    "UPDATE port_forwards SET createdEpochMillis = CAST(strftime('%s','now') AS INTEGER) * 1000"
                )
                database.execSQL("UPDATE port_forwards SET updatedEpochMillis = createdEpochMillis")

                database.execSQL("ALTER TABLE snippets ADD COLUMN `createdEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE snippets ADD COLUMN `updatedEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE snippets ADD COLUMN `lastUsedEpochMillis` INTEGER")
                database.execSQL(
                    "UPDATE snippets SET createdEpochMillis = CAST(strftime('%s','now') AS INTEGER) * 1000"
                )
                database.execSQL("UPDATE snippets SET updatedEpochMillis = createdEpochMillis")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createUptimeTables(database)
            }
        }

        private fun createUptimeTables(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `host_uptime_configs` (
                    `hostId` TEXT NOT NULL,
                    `method` TEXT NOT NULL,
                    `port` INTEGER NOT NULL,
                    `intervalMinutes` INTEGER NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `lastCheckedAt` INTEGER,
                    `lastStatus` TEXT,
                    `lastReason` TEXT,
                    `lastTransitionAt` INTEGER,
                    PRIMARY KEY(`hostId`),
                    FOREIGN KEY(`hostId`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_host_uptime_configs_hostId` ON `host_uptime_configs`(`hostId`)"
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `host_uptime_samples` (
                    `hostId` TEXT NOT NULL,
                    `checkedAt` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `reason` TEXT,
                    PRIMARY KEY(`hostId`, `checkedAt`),
                    FOREIGN KEY(`hostId`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_host_uptime_samples_hostId` ON `host_uptime_samples`(`hostId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_host_uptime_samples_checkedAt` ON `host_uptime_samples`(`checkedAt`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_host_uptime_samples_hostId_checkedAt` ON `host_uptime_samples`(`hostId`, `checkedAt`)"
            )
        }
    }
}
