package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
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
    suspend fun setHostHasPassword(id: String, hasPassword: Boolean)

    /**
     * Writes an imported host, merging with an existing host of the same id instead of
     * overwriting it: a locally newer host is kept, and the local startup script and saved-password
     * flag survive (imports never carry scripts, and the secret may already be in the vault).
     */
    suspend fun importHost(host: HostConnection)

    suspend fun addIdentity(identity: Identity)
    suspend fun updateIdentity(identity: Identity)
    suspend fun deleteIdentity(identity: Identity)
    suspend fun setIdentityHasPrivateKey(id: String, hasPrivateKey: Boolean)

    /** Marks every host password and identity key as absent (after the secure store was reset). */
    suspend fun clearAllSecretFlags()

    suspend fun addPortForward(forward: PortForward)
    suspend fun updatePortForward(forward: PortForward)
    suspend fun deletePortForward(forward: PortForward)

    suspend fun addSnippet(snippet: Snippet)
    suspend fun updateSnippet(snippet: Snippet)
    suspend fun deleteSnippet(snippet: Snippet)
}
