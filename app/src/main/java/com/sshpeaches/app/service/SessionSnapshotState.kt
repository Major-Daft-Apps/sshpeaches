package com.majordaftapps.sshpeaches.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal fun MutableStateFlow<List<SessionService.SessionSnapshot>>.upsertSessionSnapshot(
    snapshot: SessionService.SessionSnapshot
) {
    update { current ->
        val existingIndex = current.indexOfFirst { it.hostId == snapshot.hostId }
        if (existingIndex < 0) {
            current + snapshot
        } else {
            current.toMutableList().apply {
                this[existingIndex] = snapshot
            }
        }
    }
}

internal fun MutableStateFlow<List<SessionService.SessionSnapshot>>.removeSessionSnapshot(
    sessionId: String
) {
    update { current -> current.filterNot { it.hostId == sessionId } }
}
