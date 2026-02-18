package com.sshpeaches.app.data.repository

import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.OsFamily
import com.sshpeaches.app.data.model.OsMetadata
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.model.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryAppRepository : AppRepository {

    private val hostFlow = MutableStateFlow(sampleHosts())
    private val identityFlow = MutableStateFlow(sampleIdentities())
    private val portForwardFlow = MutableStateFlow(sampleForwards())
    private val snippetFlow = MutableStateFlow(sampleSnippets())

    override val hosts: Flow<List<HostConnection>> = hostFlow
    override val identities: Flow<List<Identity>> = identityFlow
    override val portForwards: Flow<List<PortForward>> = portForwardFlow
    override val snippets: Flow<List<Snippet>> = snippetFlow

    override suspend fun toggleFavorite(id: String) {
        hostFlow.update { list ->
            list.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
        }
    }

    override suspend fun addHost(host: HostConnection) {
        hostFlow.update { it + host }
    }

    override suspend fun updateHost(host: HostConnection) {
        hostFlow.update { list -> list.map { if (it.id == host.id) host else it } }
    }

    override suspend fun deleteHost(host: HostConnection) {
        hostFlow.update { list -> list.filterNot { it.id == host.id } }
    }

    override suspend fun addIdentity(identity: Identity) {
        identityFlow.update { it + identity }
    }

    override suspend fun updateIdentity(identity: Identity) {
        identityFlow.update { list -> list.map { if (it.id == identity.id) identity else it } }
    }

    override suspend fun deleteIdentity(identity: Identity) {
        identityFlow.update { list -> list.filterNot { it.id == identity.id } }
    }

    override suspend fun addPortForward(forward: PortForward) {
        portForwardFlow.update { it + forward }
    }

    override suspend fun updatePortForward(forward: PortForward) {
        portForwardFlow.update { list -> list.map { if (it.id == forward.id) forward else it } }
    }

    override suspend fun deletePortForward(forward: PortForward) {
        portForwardFlow.update { list -> list.filterNot { it.id == forward.id } }
    }

    companion object {
        private fun sampleHosts() = listOf(
            HostConnection(
                id = "host-1",
                name = "Prod Bastion",
                host = "bastion.prod.sshpeaches.com",
                username = "admin",
                preferredAuth = AuthMethod.IDENTITY,
                lastUsedEpochMillis = System.currentTimeMillis() - 3_600_000,
                favorite = true,
                defaultMode = ConnectionMode.SSH,
                osMetadata = OsMetadata.Known(OsFamily.UBUNTU, "22.04 LTS"),
                group = "Production",
                notes = "Primary entry point"
            ),
            HostConnection(
                id = "host-2",
                name = "File Mirror",
                host = "mirror.internal",
                username = "deploy",
                preferredAuth = AuthMethod.PASSWORD_AND_IDENTITY,
                defaultMode = ConnectionMode.SFTP,
                favorite = true,
                osMetadata = OsMetadata.Known(OsFamily.FEDORA, "39"),
                attachedForwards = listOf("pf-1"),
                snippets = listOf("snip-backup")
            ),
            HostConnection(
                id = "host-3",
                name = "Lab Nix",
                host = "nixos.lab",
                username = "nixos",
                preferredAuth = AuthMethod.IDENTITY,
                osMetadata = OsMetadata.Known(OsFamily.NIXOS),
                defaultMode = ConnectionMode.SSH
            )
        )

        private fun sampleIdentities() = listOf(
            Identity(
                id = "id-1",
                label = "Prod Admin",
                fingerprint = "SHA256:abcd1234",
                username = "admin",
                createdEpochMillis = System.currentTimeMillis() - 86_400_000,
                lastUsedEpochMillis = System.currentTimeMillis() - 7_200_000,
                favorite = true,
                tags = listOf("prod", "ed25519")
            ),
            Identity(
                id = "id-2",
                label = "Staging",
                fingerprint = "SHA256:efef9876",
                username = "deploy",
                createdEpochMillis = System.currentTimeMillis() - 604_800_000,
                tags = listOf("staging")
            )
        )

        private fun sampleForwards() = listOf(
            PortForward(
                id = "pf-1",
                label = "Postgres tunnel",
                type = PortForwardType.LOCAL,
                sourcePort = 5433,
                destinationHost = "db.internal",
                destinationPort = 5432,
                associatedHosts = listOf("host-2"),
                favorite = true,
                enabled = true
            ),
            PortForward(
                id = "pf-2",
                label = "Dynamic socks",
                type = PortForwardType.DYNAMIC,
                sourcePort = 1080,
                associatedHosts = listOf("host-1")
            )
        )

        private fun sampleSnippets() = listOf(
            Snippet(
                id = "snip-backup",
                title = "Backup status",
                description = "Check last backup timestamp",
                command = "sudo systemctl status backup.service",
                tags = listOf("Diagnostics"),
                autoRunHostIds = listOf("host-2"),
                requireConfirmation = false,
                favorite = true
            ),
            Snippet(
                id = "snip-disk",
                title = "Disk usage",
                description = "Show df -h",
                command = "df -h",
                tags = listOf("Diagnostics"),
                favorite = false
            )
        )
    }
}
