package com.majordaftapps.sshpeaches.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts")
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(host: HostEntity): Long

    @Delete
    suspend fun delete(host: HostEntity): Int

    @Query("UPDATE hosts SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("SELECT COUNT(*) FROM hosts")
    suspend fun countAll(): Int
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: IdentityEntity): Long

    @Delete
    suspend fun delete(identity: IdentityEntity): Int

    @Query("UPDATE identities SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("SELECT COUNT(*) FROM identities")
    suspend fun countAll(): Int
}

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards")
    fun observeAll(): Flow<List<PortForwardEntity>>

    @Query("SELECT * FROM port_forwards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PortForwardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forward: PortForwardEntity): Long

    @Delete
    suspend fun delete(forward: PortForwardEntity): Int

    @Query("UPDATE port_forwards SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("SELECT COUNT(*) FROM port_forwards")
    suspend fun countAll(): Int
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: SnippetEntity): Long

    @Delete
    suspend fun delete(snippet: SnippetEntity): Int

    @Query("UPDATE snippets SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("SELECT COUNT(*) FROM snippets")
    suspend fun countAll(): Int
}
