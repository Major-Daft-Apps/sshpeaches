package com.majordaftapps.sshpeaches.app.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionServiceNotificationTest {

    private lateinit var controller: ServiceController<SessionService>
    private lateinit var service: SessionService
    private lateinit var shadowService: ShadowService
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        SettingsStore.init(context)
        controller = Robolectric.buildService(SessionService::class.java).create()
        service = controller.get()
        shadowService = shadowOf(service)
        shadowNotificationManager = shadowOf(
            checkNotNull(context.getSystemService(NotificationManager::class.java))
        )
    }

    @After
    fun tearDown() {
        service.publishSessionNotifications(emptyList())
        controller.destroy()
    }

    @Test
    fun parallelSessionsHaveStableSummaryDistinctLabelsAndIndependentActions() {
        val first = snapshot("session-a")
        val second = snapshot("session-b")

        service.publishSessionNotifications(listOf(first, second))

        val summaryId = shadowService.lastForegroundNotificationId
        val summary = shadowService.lastForegroundNotification
        assertEquals("2 active SSH sessions", notificationTitle(summary))
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(
            SessionService.ACTION_OPEN_SESSIONS,
            summaryOpenIntent(summary).action
        )

        val children = childNotifications()
        assertEquals(2, children.size)
        val childTitles = children.map(::notificationTitle)
        assertNotEquals(childTitles[0], childTitles[1])
        assertTrue(childTitles.any { it.endsWith("Session 1") })
        assertTrue(childTitles.any { it.endsWith("Session 2") })
        assertEquals(setOf(first.hostId, second.hostId), children.map(::disconnectSessionId).toSet())
        assertEquals(2, children.map { disconnectRequestCode(it) }.toSet().size)

        service.publishSessionNotifications(listOf(second))

        assertEquals(summaryId, shadowService.lastForegroundNotificationId)
        val singleSessionSummary = shadowService.lastForegroundNotification
        assertEquals("1 active SSH session", notificationTitle(singleSessionSummary))
        assertEquals(
            SessionService.ACTION_OPEN_SESSION,
            summaryOpenIntent(singleSessionSummary).action
        )
        assertEquals(
            second.hostId,
            summaryOpenIntent(singleSessionSummary)
                .getStringExtra(SessionService.EXTRA_HOST_ID)
        )
        val remainingChildren = childNotifications()
        assertEquals(1, remainingChildren.size)
        assertEquals(second.hostId, disconnectSessionId(remainingChildren.single()))
        assertTrue(notificationTitle(remainingChildren.single()).endsWith("Session 2"))

        service.publishSessionNotifications(emptyList())

        assertTrue(shadowService.isForegroundStopped)
        assertTrue(shadowService.notificationShouldRemoved)
        assertTrue(shadowNotificationManager.allNotifications.isEmpty())
    }

    @Test
    fun childNotificationsAreExplicitlyGroupedWithTheForegroundSummary() {
        service.publishSessionNotifications(listOf(snapshot("session-a"), snapshot("session-b")))

        val summary = shadowService.lastForegroundNotification
        val groupKey = summary.group
        assertFalse(groupKey.isNullOrBlank())
        childNotifications().forEach { child ->
            assertEquals(groupKey, child.group)
            assertTrue(child.flags and Notification.FLAG_GROUP_SUMMARY == 0)
        }
    }

    @Test
    fun parallelConnectingSessionNotificationsOpenTheirOwnSession() {
        val first = snapshot(
            sessionId = "session-a",
            status = SessionService.SessionStatus.CONNECTING
        )
        val second = snapshot(
            sessionId = "session-b",
            status = SessionService.SessionStatus.CONNECTING
        )

        service.publishSessionNotifications(listOf(first, second))

        val children = childNotifications()
        assertEquals(2, children.size)
        assertEquals(
            setOf(first.hostId, second.hostId),
            children.map(::contentSessionId).toSet()
        )
        assertEquals(
            setOf(first.hostId, second.hostId),
            children.map(::openActionSessionId).toSet()
        )
        assertEquals(2, children.map(::contentRequestCode).toSet().size)
        assertEquals(2, children.map(::openActionRequestCode).toSet().size)
        assertEquals(2, children.map(::contentIntentData).toSet().size)
        children.forEach { notification ->
            assertEquals(contentSessionId(notification), openActionSessionId(notification))
            assertEquals(contentIntentData(notification), openActionIntentData(notification))
        }
    }

    @Test
    fun observableSessionStateFlowsKeepStableIdentity() {
        assertSame(service.sessionsFlow(), service.sessionsFlow())
        assertSame(service.hostKeyPromptsFlow(), service.hostKeyPromptsFlow())
        assertSame(service.passwordPromptsFlow(), service.passwordPromptsFlow())
        assertSame(service.shellOutputFlow(), service.shellOutputFlow())
        assertSame(service.remoteDirectoryFlow(), service.remoteDirectoryFlow())
        assertSame(service.fileTransferProgressFlow(), service.fileTransferProgressFlow())
    }

    private fun childNotifications(): List<Notification> =
        shadowNotificationManager.allNotifications.filter { notification ->
            notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }

    private fun contentSessionId(notification: Notification): String =
        shadowOf(notification.contentIntent)
            .savedIntent
            .getStringExtra(SessionService.EXTRA_HOST_ID)
            .orEmpty()

    private fun contentRequestCode(notification: Notification): Int =
        shadowOf(notification.contentIntent).requestCode

    private fun contentIntentData(notification: Notification): String =
        shadowOf(notification.contentIntent).savedIntent.dataString.orEmpty()

    private fun openActionSessionId(notification: Notification): String {
        val openAction = notification.actions.single { it.title.toString() == "Open" }
        return shadowOf(openAction.actionIntent)
            .savedIntent
            .getStringExtra(SessionService.EXTRA_HOST_ID)
            .orEmpty()
    }

    private fun openActionRequestCode(notification: Notification): Int {
        val openAction = notification.actions.single { it.title.toString() == "Open" }
        return shadowOf(openAction.actionIntent).requestCode
    }

    private fun openActionIntentData(notification: Notification): String {
        val openAction = notification.actions.single { it.title.toString() == "Open" }
        return shadowOf(openAction.actionIntent).savedIntent.dataString.orEmpty()
    }

    private fun disconnectSessionId(notification: Notification): String {
        val disconnectAction = notification.actions.single { it.title.toString() == "Disconnect" }
        return shadowOf(disconnectAction.actionIntent)
            .savedIntent
            .getStringExtra(SessionService.EXTRA_HOST_ID)
            .orEmpty()
    }

    private fun disconnectRequestCode(notification: Notification): Int {
        val disconnectAction = notification.actions.single { it.title.toString() == "Disconnect" }
        return shadowOf(disconnectAction.actionIntent).requestCode
    }

    private fun summaryOpenIntent(notification: Notification): android.content.Intent {
        val openAction = notification.actions.single {
            it.title.toString() == "Open" || it.title.toString() == "Open sessions"
        }
        return shadowOf(openAction.actionIntent).savedIntent
    }

    private fun notificationTitle(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun snapshot(
        sessionId: String,
        status: SessionService.SessionStatus = SessionService.SessionStatus.ACTIVE
    ): SessionService.SessionSnapshot =
        SessionService.SessionSnapshot(
            hostId = sessionId,
            host = HostConnection(
                id = "shared-host",
                name = "Production",
                host = "server.example",
                username = "deploy",
                preferredAuth = AuthMethod.IDENTITY
            ),
            mode = ConnectionMode.SSH,
            status = status,
            statusMessage = when (status) {
                SessionService.SessionStatus.CONNECTING -> "Opening SSH connection..."
                SessionService.SessionStatus.ACTIVE -> "Interactive shell session ready"
                SessionService.SessionStatus.ERROR -> "Connection failed"
            }
        )
}
