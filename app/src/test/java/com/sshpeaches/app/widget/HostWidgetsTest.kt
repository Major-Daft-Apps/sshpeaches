package com.majordaftapps.sshpeaches.app.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HostWidgetsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearWidgetState()
    }

    @After
    fun tearDown() {
        clearWidgetState()
    }

    @Test
    fun quickWidgetMinimumAllocatesOneHostAndOneSessionWithOverflow() {
        val capacity = WidgetLayoutCapacity.calculate(
            variant = WidgetVariant.QUICK_CONNECT,
            heightDp = HostWidgets.QUICK_CONNECT_MIN_HEIGHT_DP,
            hostCount = 3,
            sessionCount = 3
        )

        assertEquals(1, capacity.hostRows)
        assertEquals(1, capacity.sessionRows)
        assertEquals(4, capacity.hiddenCount)
    }

    @Test
    fun sessionsWidgetMinimumPrioritizesTwoSessionsAndReportsOverflow() {
        val capacity = WidgetLayoutCapacity.calculate(
            variant = WidgetVariant.SESSIONS,
            heightDp = HostWidgets.SESSIONS_MIN_HEIGHT_DP,
            hostCount = 3,
            sessionCount = 3
        )

        assertEquals(1, capacity.hostRows)
        assertEquals(2, capacity.sessionRows)
        assertEquals(3, capacity.hiddenCount)
    }

    @Test
    fun resizingAddsRowsAndEventuallyRemovesOverflow() {
        val medium = WidgetLayoutCapacity.calculate(
            variant = WidgetVariant.QUICK_CONNECT,
            heightDp = 440,
            hostCount = 3,
            sessionCount = 3
        )
        val large = WidgetLayoutCapacity.calculate(
            variant = WidgetVariant.QUICK_CONNECT,
            heightDp = 500,
            hostCount = 3,
            sessionCount = 3
        )

        assertEquals(5, medium.hostRows + medium.sessionRows)
        assertEquals(1, medium.hiddenCount)
        assertEquals(3, large.hostRows)
        assertEquals(3, large.sessionRows)
        assertEquals(0, large.hiddenCount)
    }

    @Test
    fun sessionStoreIncludesModeStatusAndEndpointInSubtitle() {
        WidgetSessionStore.write(
            context,
            listOf(
                snapshot(
                    mode = ConnectionMode.SCP,
                    status = SessionService.SessionStatus.ACTIVE
                )
            )
        )

        val stored = WidgetSessionStore.read(context).single()

        assertEquals("Production", stored.title)
        assertEquals(
            "Upload / download · Active · deploy@example.test:2222",
            stored.subtitle
        )
    }

    @Test
    fun hostRowUsesDistinctDirectionalLabelsAndFortyEightDpTargets() {
        val applied = HostWidgets.buildHostRow(context, host())
            .apply(context, FrameLayout(context))
        val upload = applied.findViewById<View>(R.id.widget_btn_upload)
        val download = applied.findViewById<View>(R.id.widget_btn_download)
        val expectedTargetHeight =
            (48 * context.resources.displayMetrics.density).toInt()

        assertTrue(upload.contentDescription.toString().startsWith("Upload files to"))
        assertTrue(download.contentDescription.toString().startsWith("Download files from"))
        assertEquals(expectedTargetHeight, upload.layoutParams.width)
        assertEquals(expectedTargetHeight, upload.layoutParams.height)
        assertEquals(expectedTargetHeight, download.layoutParams.width)
        assertEquals(expectedTargetHeight, download.layoutParams.height)
    }

    @Test
    fun uploadAndDownloadPendingIntentsKeepDistinctEntryModes() {
        val upload = HostWidgets.createConnectPendingIntent(
            context = context,
            hostId = "host",
            mode = ConnectionMode.SCP,
            fileTransferEntryMode = FileTransferEntryMode.UPLOAD
        )
        val download = HostWidgets.createConnectPendingIntent(
            context = context,
            hostId = "host",
            mode = ConnectionMode.SCP,
            fileTransferEntryMode = FileTransferEntryMode.DOWNLOAD
        )

        assertNotEquals(upload, download)
        assertEquals(
            FileTransferEntryMode.UPLOAD.name,
            shadowOf(upload).savedIntent.getStringExtra(
                HostWidgets.EXTRA_FILE_TRANSFER_ENTRY_MODE
            )
        )
        assertEquals(
            FileTransferEntryMode.DOWNLOAD.name,
            shadowOf(download).savedIntent.getStringExtra(
                HostWidgets.EXTRA_FILE_TRANSFER_ENTRY_MODE
            )
        )
    }

    @Test
    fun openSessionRowRendersSubtitleAndAccessibleActions() {
        val session = WidgetSessionStore.WidgetOpenSession(
            sessionId = "host|SCP|session",
            title = "Production",
            subtitle = "Upload / download · Connecting · deploy@example.test:2222"
        )
        val applied = HostWidgets.buildOpenSessionRow(context, session)
            .apply(context, FrameLayout(context))
        val subtitle = applied.findViewById<TextView>(R.id.widget_open_session_subtitle)
        val open = applied.findViewById<View>(R.id.widget_btn_open)
        val disconnect = applied.findViewById<View>(R.id.widget_btn_disconnect)
        val expectedTargetHeight =
            (48 * context.resources.displayMetrics.density).toInt()

        assertEquals(session.subtitle, subtitle.text.toString())
        assertTrue(open.contentDescription.toString().contains(session.subtitle))
        assertTrue(disconnect.contentDescription.toString().contains(session.subtitle))
        assertEquals(expectedTargetHeight, open.layoutParams.width)
        assertEquals(expectedTargetHeight, open.layoutParams.height)
        assertEquals(expectedTargetHeight, disconnect.layoutParams.width)
        assertEquals(expectedTargetHeight, disconnect.layoutParams.height)
    }

    private fun snapshot(
        mode: ConnectionMode,
        status: SessionService.SessionStatus
    ): SessionService.SessionSnapshot =
        SessionService.SessionSnapshot(
            hostId = "host|${mode.name}|session",
            host = host(),
            mode = mode,
            status = status,
            statusMessage = null
        )

    private fun host(): HostConnection =
        HostConnection(
            id = "host",
            name = "Production",
            host = "example.test",
            port = 2222,
            username = "deploy",
            preferredAuth = AuthMethod.IDENTITY
        )

    private fun clearWidgetState() {
        context.getSharedPreferences("sshpeaches_widget_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("sshpeaches_widget_security", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
