package com.majordaftapps.sshpeaches.app.data.repository

import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.local.asEntity
import com.majordaftapps.sshpeaches.app.data.local.asModel
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import androidx.room.withTransaction
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
        database.withTransaction {
            hostDao.delete(host.asEntity())
            snippetDao.getAll()
                .filter { host.id in it.autoRunHostIds }
                .forEach { snippetDao.upsert(it.copy(autoRunHostIds = it.autoRunHostIds - host.id)) }
            portForwardDao.getAll()
                .filter { host.id in it.associatedHosts }
                .forEach { portForwardDao.upsert(it.copy(associatedHosts = it.associatedHosts - host.id)) }
        }
    }

    override suspend fun setHostHasPassword(id: String, hasPassword: Boolean) {
        hostDao.updateHasPassword(id, hasPassword)
    }

    override suspend fun importHost(host: HostConnection) {
        database.withTransaction {
            val existing = hostDao.getById(host.id)?.asModel()
            if (existing == null) {
                hostDao.upsert(host.asEntity())
                return@withTransaction
            }
            val localIsNewer = (existing.updatedEpochMillis ?: 0L) > (host.updatedEpochMillis ?: 0L)
            val merged = if (localIsNewer) {
                existing.copy(favorite = existing.favorite || host.favorite)
            } else {
                host.copy(
                    startupScript = existing.startupScript,
                    hasPassword = existing.hasPassword || host.hasPassword
                )
            }
            hostDao.upsert(merged.asEntity())
        }
    }

    override suspend fun addIdentity(identity: Identity) {
        identityDao.upsert(identity.asEntity())
    }

    override suspend fun updateIdentity(identity: Identity) {
        identityDao.upsert(identity.asEntity())
    }

    override suspend fun deleteIdentity(identity: Identity) {
        database.withTransaction {
            identityDao.delete(identity.asEntity())
            hostDao.clearPreferredIdentity(identity.id)
        }
    }

    override suspend fun setIdentityHasPrivateKey(id: String, hasPrivateKey: Boolean) {
        identityDao.updateHasPrivateKey(id, hasPrivateKey)
    }

    override suspend fun clearAllSecretFlags() {
        database.withTransaction {
            hostDao.clearAllHasPassword()
            identityDao.clearAllHasPrivateKey()
        }
    }

    override suspend fun addPortForward(forward: PortForward) {
        portForwardDao.upsert(forward.asEntity())
    }

    override suspend fun updatePortForward(forward: PortForward) {
        portForwardDao.upsert(forward.asEntity())
    }

    override suspend fun deletePortForward(forward: PortForward) {
        database.withTransaction {
            portForwardDao.delete(forward.asEntity())
            hostDao.getAll()
                .filter { forward.id in it.attachedForwards || it.preferredForwardId == forward.id }
                .forEach { host ->
                    hostDao.upsert(
                        host.copy(
                            attachedForwards = host.attachedForwards - forward.id,
                            preferredForwardId = host.preferredForwardId?.takeIf { it != forward.id }
                        )
                    )
                }
        }
    }

    override suspend fun addSnippet(snippet: Snippet) {
        snippetDao.upsert(snippet.asEntity())
    }

    override suspend fun updateSnippet(snippet: Snippet) {
        snippetDao.upsert(snippet.asEntity())
    }

    override suspend fun deleteSnippet(snippet: Snippet) {
        snippetDao.delete(snippet.asEntity())
    }
}
