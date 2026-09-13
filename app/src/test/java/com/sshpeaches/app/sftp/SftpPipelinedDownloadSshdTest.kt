package com.majordaftapps.sshpeaches.app.sftp

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SftpPipelinedDownloadSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun downloadsLargerThanOneChunkTwiceOnTheSameClient() {
        val sandbox = temp.newFolder("sshd-pipeline-sandbox")
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("pipeline-hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == TEST_USERNAME && password == TEST_PASSWORD
            }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        val payload = ByteArray(3 * 1024 * 1024) { index -> (index % 251).toByte() }
        File(sandbox, "large-video.mp4").writeBytes(payload)
        val firstDownload = temp.newFile("large-video-first.mp4")
        val secondDownload = temp.newFile("large-video-second.mp4")
        val host = HostConnection(
            id = "pipeline-sshd",
            name = "Pipelined SFTP SSHD",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_pipeline_sshd"),
            host = host
        )
        val connectCount = AtomicInteger(0)
        val authCount = AtomicInteger(0)
        val settings = SftpTransferSettings.fastPreset()
        assertEquals(262144, settings.sftpReadSize)
        assertEquals(128, settings.sftpMaxRequests)
        assertEquals(4, settings.parallelDownloads)
        assertEquals(
            4,
            SftpPipelinedDownloader.segmentCount((3 * 1024 * 1024).toLong(), settings)
        )
        try {
            client.connect(host.host, host.port)
            connectCount.incrementAndGet()
            client.authPassword(TEST_USERNAME, TEST_PASSWORD)
            authCount.incrementAndGet()
            assertEquals(32 * 1024, client.connection.maxPacketSize)
            client.newSFTPClient().use { sftp ->
                SftpPipelinedDownloader.downloadFromSftp(
                    sftp = sftp,
                    remotePath = "/large-video.mp4",
                    localFile = firstDownload,
                    settings = settings
                )
                SftpPipelinedDownloader.downloadFromSftp(
                    sftp = sftp,
                    remotePath = "/large-video.mp4",
                    localFile = secondDownload,
                    settings = settings
                )
            }
            assertTrue(firstDownload.readBytes().contentEquals(File(sandbox, "large-video.mp4").readBytes()))
            assertTrue(firstDownload.readBytes().contentEquals(secondDownload.readBytes()))
            assertArrayEquals(payload, firstDownload.readBytes())
            assertArrayEquals(payload, secondDownload.readBytes())
            assertEquals(1, connectCount.get())
            assertEquals(1, authCount.get())
        } finally {
            runCatching { client.close() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun downloadsSeveralFilesConcurrentlyOnOneConnectedClient() {
        val sandbox = temp.newFolder("sshd-parallel-sandbox")
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("parallel-hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == TEST_USERNAME && password == TEST_PASSWORD
            }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        val payloads = List(6) { index ->
            ByteArray(48 * 1024) { byteIndex -> (index * 17 + byteIndex).toByte() }
        }
        payloads.forEachIndexed { index, payload ->
            File(sandbox, "file-$index.bin").writeBytes(payload)
        }
        val host = HostConnection(
            id = "pipeline-sshd-parallel",
            name = "Pipelined SFTP parallel",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_pipeline_parallel"),
            host = host
        )
        val connectCount = AtomicInteger(0)
        val authCount = AtomicInteger(0)
        val settings = SftpTransferSettings(
            sftpReadSize = 8 * 1024,
            sftpMaxRequests = 8,
            parallelDownloads = 4
        )
        val locals = List(payloads.size) { index -> temp.newFile("dl-$index.bin") }
        val peak = AtomicInteger(0)
        val reachedParallel = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            client.connect(host.host, host.port)
            connectCount.incrementAndGet()
            client.authPassword(TEST_USERNAME, TEST_PASSWORD)
            authCount.incrementAndGet()
            val finished = java.util.concurrent.CountDownLatch(1)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            Thread {
                try {
                    client.newSFTPClient().use { sftp ->
                        SftpParallelDownloads.downloadAllFromSftp(
                            sftp = sftp,
                            jobs = payloads.indices.map { index ->
                                SftpFileDownloadJob("/file-$index.bin", locals[index])
                            },
                            settings = settings,
                            observer = { inflight ->
                                peak.updateAndGet { current -> maxOf(current, inflight) }
                                if (inflight >= settings.parallelDownloads) {
                                    reachedParallel.countDown()
                                }
                                release.await()
                            }
                        )
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    finished.countDown()
                }
            }.start()
            val reached = reachedParallel.await(15, java.util.concurrent.TimeUnit.SECONDS)
            release.countDown()
            assertTrue(
                "expected ${settings.parallelDownloads} SFTP downloads in flight together (peak=${peak.get()})",
                reached
            )
            assertTrue(peak.get() >= settings.parallelDownloads)
            assertTrue(finished.await(30, java.util.concurrent.TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            payloads.forEachIndexed { index, payload ->
                assertArrayEquals(payload, locals[index].readBytes())
            }
            assertEquals(1, connectCount.get())
            assertEquals(1, authCount.get())
        } finally {
            runCatching { client.close() }
            runCatching { server.stop(true) }
        }
    }

    private companion object {
        const val TEST_USERNAME = "tester"
        const val TEST_PASSWORD = "peaches-password"
    }
}
