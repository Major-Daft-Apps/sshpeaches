package com.sshpeaches.app.data.repository

import com.sshpeaches.app.data.local.SshPeachesDatabase
import com.sshpeaches.app.data.local.asEntity
import com.sshpeaches.app.data.local.asModel
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAppRepository(
    private val database: SshPeachesDatabase
) : AppRepository {

    private val hostDao = database.hostDao()
    private val identityDao = database.identityDao()
    private val portForwardDao = database.portForwardDao()
    private val snippetDao = database.snippetDao()

    override val hosts: Flow<List<HostConnection>> =
        hostDao.observeAll().map { list -> list.map { it.asModel() } }

    override val identities: Flow<List<Identity>> =
        identityDao.observeAll().map { list -> list.map { it.asModel() } }

    override val portForwards: Flow<List<PortForward>> =
        portForwardDao.observeAll().map { list -> list.map { it.asModel() } }

    override val snippets: Flow<List<Snippet>> =
        snippetDao.observeAll().map { list -> list.map { it.asModel() } }

    override suspend fun toggleFavorite(id: String) {
        hostDao.getById(id)?.let { host ->
            hostDao.updateFavorite(id, !host.favorite)
            return
        }
        identityDao.getById(id)?.let { identity ->
            identityDao.updateFavorite(id, !identity.favorite)
            return
        }
        portForwardDao.getById(id)?.let { forward ->
            portForwardDao.updateFavorite(id, !forward.favorite)
            return
        }
        snippetDao.getById(id)?.let { snippet ->
            snippetDao.updateFavorite(id, !snippet.favorite)
        }
    }

    override suspend fun addHost(host: HostConnection) {
        hostDao.upsert(host.asEntity())
    }

    override suspend fun updateHost(host: HostConnection) {
        hostDao.upsert(host.asEntity())
    }

    override suspend fun deleteHost(host: HostConnection) {
        hostDao.delete(host.asEntity())
    }

    override suspend fun addIdentity(identity: Identity) {
        identityDao.upsert(identity.asEntity())
    }

    override suspend fun updateIdentity(identity: Identity) {
        identityDao.upsert(identity.asEntity())
    }

    override suspend fun deleteIdentity(identity: Identity) {
        identityDao.delete(identity.asEntity())
    }

    override suspend fun addPortForward(forward: PortForward) {
        portForwardDao.upsert(forward.asEntity())
    }

    override suspend fun updatePortForward(forward: PortForward) {
        portForwardDao.upsert(forward.asEntity())
    }

    override suspend fun deletePortForward(forward: PortForward) {
        portForwardDao.delete(forward.asEntity())
    }
}
