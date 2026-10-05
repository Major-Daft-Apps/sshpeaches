package com.majordaftapps.sshpeaches.app.sftp

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import net.schmizz.sshj.SSHClient
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SftpResumableDownloadSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: SshServer
    private lateinit var client: SSHClient
    private val payload = ByteArray(3 * 1024 * 1024) { index -> (index % 251).toByte() }
    private val settings = SftpTransferSettings.fastPreset()

    @Before
    fun startServer() {
        sandbox = temp.newFolder("sandbox")
        File(sandbox, "video.mp4").writeBytes(payload)
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        val host = HostConnection(
            id = "resume", name = "resume", host = "127.0.0.1", port = server.port,
            username = USER, preferredAuth = AuthMethod.PASSWORD
        )
        client = SshClientProvider.createClientForTesting(knownHostsFile = temp.newFile(), host = host).apply {
            connect(host.host, host.port)
            authPassword(USER, PASSWORD)
        }
    }

    @After
    fun stopServer() {
        runCatching { client.close() }
        runCatching { server.stop(true) }
    }

    @Test
    fun aFailedDownloadResumesAndOnlyFetchesWhatIsMissing() {
        val partial = SftpPartialDownload.forRemote(temp.newFolder("partials"), "tester@host:22", "/video.mp4")
        val failAfter = payload.size / 2L
        client.newSFTPClient().use { sftp ->
            try {
                SftpResumableDownloader.download(
                    sftp = sftp,
                    remotePath = "/video.mp4",
                    partial = partial,
                    settings = settings,
                    onBytesTransferred = { total -> if (total >= failAfter) throw IOException("network dropped") }
                )
                fail("download should have failed")
            } catch (_: IOException) {
            }
        }
        assertTrue("partial data kept after a failure", partial.dataFile.exists())

        val resumedFrom = AtomicLong(-1)
        val firstReport = AtomicLong(-1)
        client.newSFTPClient().use { sftp ->
            val already = SftpResumableDownloader.download(
                sftp = sftp,
                remotePath = "/video.mp4",
                partial = partial,
                settings = settings,
                onBytesTransferred = { total -> firstReport.compareAndSet(-1, total) },
                onResume = { done, size ->
                    resumedFrom.set(done)
                    assertEquals(payload.size.toLong(), size)
                }
            )
            assertEquals(resumedFrom.get(), already)
        }
        assertTrue("resumed from a saved position, got ${resumedFrom.get()}", resumedFrom.get() > 0L)
        assertTrue("progress continues from the resumed bytes", firstReport.get() > resumedFrom.get())
        assertArrayEquals(payload, partial.dataFile.readBytes())
    }

    @Test
    fun aChangedRemoteFileStartsOver() {
        val partial = SftpPartialDownload.forRemote(temp.newFolder("partials"), "tester@host:22", "/video.mp4")
        client.newSFTPClient().use { sftp ->
            runCatching {
                SftpResumableDownloader.download(
                    sftp, "/video.mp4", partial, settings,
                    onBytesTransferred = { total -> if (total > payload.size / 3) throw IOException("dropped") }
                )
            }
        }
        val changed = ByteArray(payload.size) { index -> (index % 13).toByte() }
        File(sandbox, "video.mp4").apply {
            writeBytes(changed)
            setLastModified(lastModified() + 60_000)
        }

        var resumed = false
        client.newSFTPClient().use { sftp ->
            SftpResumableDownloader.download(sftp, "/video.mp4", partial, settings, onResume = { _, _ -> resumed = true })
        }
        assertFalse(resumed)
        assertArrayEquals(changed, partial.dataFile.readBytes())
    }

    @Test
    fun cancellingDiscardsThePartial() {
        val partial = SftpPartialDownload.forRemote(temp.newFolder("partials"), "tester@host:22", "/video.mp4")
        val seen = AtomicLong()
        client.newSFTPClient().use { sftp ->
            runCatching {
                SftpResumableDownloader.download(
                    sftp, "/video.mp4", partial, settings,
                    onBytesTransferred = { seen.set(it) },
                    isCancelled = { seen.get() > payload.size / 4 }
                )
            }
        }
        assertFalse(partial.dataFile.exists())
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
    }
}
