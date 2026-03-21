package com.majordaftapps.sshpeaches.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HostEntity::class,
        IdentityEntity::class,
        PortForwardEntity::class,
        SnippetEntity::class,
        HostUptimeConfigEntity::class,
        HostUptimeSampleEntity::class
    ],
    version = 6,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
