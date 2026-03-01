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
    suspend fun upsert(host: HostEntity)

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("UPDATE hosts SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: IdentityEntity)

    @Delete
    suspend fun delete(identity: IdentityEntity)

    @Query("UPDATE identities SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)
}

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards")
    fun observeAll(): Flow<List<PortForwardEntity>>

    @Query("SELECT * FROM port_forwards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PortForwardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forward: PortForwardEntity)

    @Delete
    suspend fun delete(forward: PortForwardEntity)

    @Query("UPDATE port_forwards SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: SnippetEntity)

    @Delete
    suspend fun delete(snippet: SnippetEntity)

    @Query("UPDATE snippets SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)
}
