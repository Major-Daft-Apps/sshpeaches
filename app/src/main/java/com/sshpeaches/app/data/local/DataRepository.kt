package com.sshpeaches.app.data.local

import android.content.Context
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataRepository private constructor(database: SshPeachesDatabase) {
    private val hostDao = database.hostDao()
    private val identityDao = database.identityDao()
    private val portForwardDao = database.portForwardDao()
    private val snippetDao = database.snippetDao()

    val hosts: Flow<List<HostConnection>> = hostDao.observeAll().map { list -> list.map { it.asModel() } }
    val identities: Flow<List<Identity>> = identityDao.observeAll().map { list -> list.map { it.asModel() } }
    val portForwards: Flow<List<PortForward>> = portForwardDao.observeAll().map { list -> list.map { it.asModel() } }
    val snippets: Flow<List<Snippet>> = snippetDao.observeAll().map { list -> list.map { it.asModel() } }

    suspend fun upsertHost(host: HostConnection) = hostDao.upsert(host.asEntity())
    suspend fun deleteHost(host: HostConnection) = hostDao.delete(host.asEntity())

    suspend fun upsertIdentity(identity: Identity) = identityDao.upsert(identity.asEntity())
    suspend fun deleteIdentity(identity: Identity) = identityDao.delete(identity.asEntity())

    suspend fun upsertForward(forward: PortForward) = portForwardDao.upsert(forward.asEntity())
    suspend fun deleteForward(forward: PortForward) = portForwardDao.delete(forward.asEntity())

    suspend fun upsertSnippet(snippet: Snippet) = snippetDao.upsert(snippet.asEntity())
    suspend fun deleteSnippet(snippet: Snippet) = snippetDao.delete(snippet.asEntity())

    companion object {
        @Volatile private var instance: DataRepository? = null
        fun get(context: Context): DataRepository =
            instance ?: synchronized(this) {
                instance ?: DataRepository(SshPeachesDatabase.get(context)).also { instance = it }
            }
    }
}
