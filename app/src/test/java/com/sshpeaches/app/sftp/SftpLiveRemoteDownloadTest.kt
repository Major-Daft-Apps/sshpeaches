package com.majordaftapps.sshpeaches.app.sftp

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Optional live download against a real SSH host. Skipped unless
 * SSHPEACHES_LIVE_SFTP_HOST / USER / PASSWORD are set.
 */
class SftpLiveRemoteDownloadTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun downloadsRemoteFileTwiceWithPipelinedEngine() {
        val hostName = System.getenv("SSHPEACHES_LIVE_SFTP_HOST")?.trim().orEmpty()
        val username = System.getenv("SSHPEACHES_LIVE_SFTP_USER")?.trim().orEmpty()
        val password = System.getenv("SSHPEACHES_LIVE_SFTP_PASSWORD").orEmpty()
        val remotePath = System.getenv("SSHPEACHES_LIVE_SFTP_PATH")?.trim().orEmpty()
        assumeTrue(
            "Live SFTP host env is not set",
            hostName.isNotBlank() && username.isNotBlank() && password.isNotBlank() && remotePath.isNotBlank()
        )
        val expectedSha = System.getenv("SSHPEACHES_LIVE_SFTP_SHA256")?.trim()?.lowercase()
        val host = HostConnection(
            id = "live-enzu-sftp",
            name = "Live Enzu SFTP",
            host = hostName,
            username = username,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_live_sftp"),
            host = host
        )
        val first = temp.newFile("live-first.bin")
        val second = temp.newFile("live-second.bin")
        val settings = SftpTransferSettings.fastPreset()
        try {
            client.connect(host.host, host.port)
            client.authPassword(username, password)
            assertTrue(client.isConnected)
            assertTrue(client.isAuthenticated)
            client.newSFTPClient().use { sftp ->
                val size = sftp.stat(remotePath).size
                println("LIVE SFTP remote=$hostName path=$remotePath size=$size")
                val firstMs = downloadOnce(sftp, remotePath, first, settings)
                val secondMs = downloadOnce(sftp, remotePath, second, settings)
                println(
                    "LIVE SFTP first=${formatRate(size, firstMs)} second=${formatRate(size, secondMs)} " +
                        "settings=${settings.sftpReadSize}/${settings.sftpMaxRequests}/${settings.parallelDownloads}"
                )
            }
            assertTrue(first.length() > 0L)
            assertEquals(first.length(), second.length())
            val firstSha = sha256(first)
            val secondSha = sha256(second)
            assertEquals(firstSha, secondSha)
            if (!expectedSha.isNullOrBlank()) {
                assertEquals(expectedSha, firstSha)
            }
            println("LIVE SFTP sha256=$firstSha bytes=${first.length()}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun downloadOnce(
        sftp: net.schmizz.sshj.sftp.SFTPClient,
        remotePath: String,
        localFile: File,
        settings: SftpTransferSettings
    ): Long {
        val started = System.nanoTime()
        SftpPipelinedDownloader.downloadFromSftp(
            sftp = sftp,
            remotePath = remotePath,
            localFile = localFile,
            settings = settings
        )
        return (System.nanoTime() - started) / 1_000_000L
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun formatRate(bytes: Long, durationMs: Long): String {
        val seconds = durationMs.coerceAtLeast(1L) / 1000.0
        val mib = bytes / (1024.0 * 1024.0)
        return "${"%.2f".format(mib)} MiB in ${durationMs} ms (${"%.1f".format(mib / seconds)} MiB/s)"
    }
}
