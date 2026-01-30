package com.sshpeaches.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.OsMetadata
import com.sshpeaches.app.data.model.PortForwardType

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val preferredAuth: AuthMethod,
    val group: String?,
    val lastUsedEpochMillis: Long?,
    val favorite: Boolean,
    val osMetadata: OsMetadata,
    val notes: String,
    val defaultMode: ConnectionMode,
    val attachedForwards: List<String>,
    val snippets: List<String>
)

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val label: String,
    val fingerprint: String,
    val username: String?,
    val createdEpochMillis: Long,
    val lastUsedEpochMillis: Long?,
    val favorite: Boolean,
    val tags: List<String>,
    val notes: String
)

@Entity(tableName = "port_forwards")
data class PortForwardEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: PortForwardType,
    val sourceHost: String,
    val sourcePort: Int,
    val destinationHost: String,
    val destinationPort: Int,
    val associatedHosts: List<String>,
    val favorite: Boolean,
    val enabled: Boolean
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val command: String,
    val tags: List<String>,
    val autoRunHostIds: List<String>,
    val requireConfirmation: Boolean,
    val favorite: Boolean
)
