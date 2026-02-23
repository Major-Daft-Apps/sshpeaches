package com.sshpeaches.app.data.repository

import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    val hosts: Flow<List<HostConnection>>
    val identities: Flow<List<Identity>>
    val portForwards: Flow<List<PortForward>>
    val snippets: Flow<List<Snippet>>

    suspend fun toggleFavorite(id: String)
    suspend fun addHost(host: HostConnection)
    suspend fun updateHost(host: HostConnection)
    suspend fun deleteHost(host: HostConnection)

    suspend fun addIdentity(identity: Identity)
    suspend fun updateIdentity(identity: Identity)
    suspend fun deleteIdentity(identity: Identity)

    suspend fun addPortForward(forward: PortForward)
    suspend fun updatePortForward(forward: PortForward)
    suspend fun deletePortForward(forward: PortForward)

    suspend fun addSnippet(snippet: Snippet)
    suspend fun updateSnippet(snippet: Snippet)
    suspend fun deleteSnippet(snippet: Snippet)
}
