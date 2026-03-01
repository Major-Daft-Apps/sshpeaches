package com.majordaftapps.sshpeaches.app.data.local

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet

fun HostEntity.asModel(): HostConnection = HostConnection(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    preferredAuth = preferredAuth,
    group = group,
    lastUsedEpochMillis = lastUsedEpochMillis,
    favorite = favorite,
    osMetadata = osMetadata,
    notes = notes,
    defaultMode = defaultMode,
    attachedForwards = attachedForwards,
    snippets = snippets,
    hasPassword = hasPassword,
    useMosh = useMosh,
    preferredIdentityId = preferredIdentityId,
    preferredForwardId = preferredForwardId,
    startupScript = startupScript,
    backgroundBehavior = backgroundBehavior,
    terminalProfileId = terminalProfileId
)

fun HostConnection.asEntity(): HostEntity = HostEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    preferredAuth = preferredAuth,
    group = group,
    lastUsedEpochMillis = lastUsedEpochMillis,
    favorite = favorite,
    osMetadata = osMetadata,
    notes = notes,
    defaultMode = defaultMode,
    attachedForwards = attachedForwards,
    snippets = snippets,
    hasPassword = hasPassword,
    useMosh = useMosh,
    preferredIdentityId = preferredIdentityId,
    preferredForwardId = preferredForwardId,
    startupScript = startupScript,
    backgroundBehavior = backgroundBehavior,
    terminalProfileId = terminalProfileId
)

fun IdentityEntity.asModel(): Identity = Identity(
    id = id,
    label = label,
    fingerprint = fingerprint,
    username = username,
    createdEpochMillis = createdEpochMillis,
    lastUsedEpochMillis = lastUsedEpochMillis,
    favorite = favorite,
    tags = tags,
    notes = notes,
    hasPrivateKey = hasPrivateKey,
    keyImportEpochMillis = keyImportEpochMillis
)

fun Identity.asEntity(): IdentityEntity = IdentityEntity(
    id = id,
    label = label,
    fingerprint = fingerprint,
    username = username,
    createdEpochMillis = createdEpochMillis,
    lastUsedEpochMillis = lastUsedEpochMillis,
    favorite = favorite,
    tags = tags,
    notes = notes,
    hasPrivateKey = hasPrivateKey,
    keyImportEpochMillis = keyImportEpochMillis
)

fun PortForwardEntity.asModel(): PortForward = PortForward(
    id = id,
    label = label,
    type = type,
    sourceHost = sourceHost,
    sourcePort = sourcePort,
    destinationHost = destinationHost,
    destinationPort = destinationPort,
    associatedHosts = associatedHosts,
    favorite = favorite,
    enabled = enabled
)

fun PortForward.asEntity(): PortForwardEntity = PortForwardEntity(
    id = id,
    label = label,
    type = type,
    sourceHost = sourceHost,
    sourcePort = sourcePort,
    destinationHost = destinationHost,
    destinationPort = destinationPort,
    associatedHosts = associatedHosts,
    favorite = favorite,
    enabled = enabled
)

fun SnippetEntity.asModel(): Snippet = Snippet(
    id = id,
    title = title,
    description = description,
    command = command,
    tags = tags,
    autoRunHostIds = autoRunHostIds,
    requireConfirmation = requireConfirmation,
    favorite = favorite
)

fun Snippet.asEntity(): SnippetEntity = SnippetEntity(
    id = id,
    title = title,
    description = description,
    command = command,
    tags = tags,
    autoRunHostIds = autoRunHostIds,
    requireConfirmation = requireConfirmation,
    favorite = favorite
)
