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
        SnippetEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SshPeachesDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun snippetDao(): SnippetDao

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
                    .addMigrations(MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE identities ADD COLUMN `group` TEXT")
                database.execSQL("ALTER TABLE port_forwards ADD COLUMN `group` TEXT")
                database.execSQL("ALTER TABLE snippets ADD COLUMN `group` TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
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
    }
}
