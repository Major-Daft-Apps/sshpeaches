package com.majordaftapps.sshpeaches.app.ui

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickConnectUiStateMappingTest {

    @Test
    fun networkFailureKindFlowsFromSessionSnapshot() {
        val host = HostConnection(
            id = "host-id",
            name = "Test host",
            host = "server.example",
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
        val snapshot = SessionService.SessionSnapshot(
            hostId = "session-id",
            host = host,
            mode = ConnectionMode.SSH,
            status = SessionService.SessionStatus.ERROR,
            statusMessage = "Connection refused",
            failureKind = ConnectionFailureKind.NETWORK
        )

        val state = quickConnectUiStateFromSnapshot(snapshot, host)

        assertEquals(QuickConnectPhase.ERROR, state.phase)
        assertEquals("Connection refused", state.message)
        assertEquals(ConnectionFailureKind.NETWORK, state.failureKind)
    }
}
