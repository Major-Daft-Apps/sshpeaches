package com.majordaftapps.sshpeaches.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostUptimeConfigDao {
    @Query("SELECT * FROM host_uptime_configs")
    fun observeAll(): Flow<List<HostUptimeConfigEntity>>

    @Query("SELECT * FROM host_uptime_configs")
    suspend fun getAll(): List<HostUptimeConfigEntity>

    @Query("SELECT * FROM host_uptime_configs WHERE enabled = 1")
    suspend fun getEnabled(): List<HostUptimeConfigEntity>

    @Query("SELECT * FROM host_uptime_configs WHERE hostId = :hostId LIMIT 1")
    suspend fun getByHostId(hostId: String): HostUptimeConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: HostUptimeConfigEntity)

    @Query("DELETE FROM host_uptime_configs WHERE hostId = :hostId")
    suspend fun deleteByHostId(hostId: String)
}

@Dao
interface HostUptimeSampleDao {
    @Query("SELECT * FROM host_uptime_samples WHERE checkedAt >= :since")
    fun observeSince(since: Long): Flow<List<HostUptimeSampleEntity>>

    @Query("SELECT * FROM host_uptime_samples WHERE hostId = :hostId AND checkedAt >= :since ORDER BY checkedAt ASC")
    suspend fun getForHostSince(hostId: String, since: Long): List<HostUptimeSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: HostUptimeSampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<HostUptimeSampleEntity>)

    @Query("DELETE FROM host_uptime_samples WHERE checkedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("DELETE FROM host_uptime_samples WHERE hostId = :hostId")
    suspend fun deleteByHostId(hostId: String)
}
