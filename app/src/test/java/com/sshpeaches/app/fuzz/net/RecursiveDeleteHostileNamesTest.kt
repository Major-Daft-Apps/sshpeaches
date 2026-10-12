package com.majordaftapps.sshpeaches.app.fuzz.net

import android.app.Application
import com.majordaftapps.sshpeaches.app.service.SessionService
import java.io.File
import java.lang.reflect.InvocationTargetException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SessionService's recursive delete joins each child name from the server's listing onto the
 * parent path without checking it. A listing entry named "../victim" makes it delete a sibling
 * directory that was never selected, bypassing the protected-path check done on the top path only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecursiveDeleteHostileNamesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: HostileSftpServer
    private lateinit var client: SSHClient

    @Before
    fun setUp() {
        sandbox = temp.newFolder("sandbox")
        server = HostileSftpServer(sandbox, temp.newFile("hostkey"), temp.newFile("known_hosts"))
        client = server.connect()
    }

    @After
    fun tearDown() {
        runCatching { client.close() }
        server.close()
    }

    private fun deleteRecursively(sftp: SFTPClient, path: String): Throwable? {
        val service = Robolectric.buildService(SessionService::class.java).get()
        val method = SessionService::class.java.getDeclaredMethod("deleteRemotePathRecursively", SFTPClient::class.java, String::class.java)
        method.isAccessible = true
        return try {
            Fuzz.withinMillis(15_000, "deleteRemotePathRecursively") { method.invoke(service, sftp, path) }
            null
        } catch (error: InvocationTargetException) {
            error.targetException
        }
    }

    @Test
    fun aChildNamedDotDotSlashSomethingCannotMakeDeleteLeaveTheSelectedDirectory() {
        val target = File(sandbox, "target").apply { mkdirs() }
        File(target, "junk.txt").writeText("junk")
        File(target, "decoy").writeText("decoy")
        val victim = File(sandbox, "victim").apply { mkdirs() }
        val precious = File(victim, "precious.txt").apply { writeText("keep me") }
        server.listingNames = mapOf("decoy" to "../victim")

        val error = client.newSFTPClient().use { sftp -> deleteRecursively(sftp, "/target") }

        assertTrue(
            "deleting /target removed /victim/precious.txt (delete error: ${error?.javaClass?.simpleName}: ${error?.message})",
            precious.exists()
        )
    }
}
