package com.majordaftapps.sshpeaches.livetest

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Disposable protocol fixture with ownership-safe client and server teardown. */
class SshProtocolMatrixTest {
    @Test
    fun terminalAndSftpSurviveConcurrentNewConnections() {
        val root = Files.createTempDirectory("sshpeaches-matrix-").toFile()
        File(root, "sentinel.txt").writeText("still here")
        val server = MatrixServer(root).also { it.start() }
        val probes = Executors.newFixedThreadPool(4)
        val primary = client()
        try {
            primary.connect("127.0.0.1", server.port)
            primary.authPassword(USER, PASSWORD)
            val terminal = primary.startSession()
            terminal.allocateDefaultPTY()
            val sftp = primary.newSFTPClient()
            try {
                val futures = (0 until 24).map {
                    probes.submit {
                        client().use { probe ->
                            probe.connect("127.0.0.1", server.port)
                            probe.authPassword(USER, PASSWORD)
                            probe.startSession().use { session -> session.allocateDefaultPTY() }
                        }
                    }
                }
                futures.forEach { it.get(15, TimeUnit.SECONDS) }
                assertTrue("primary SSH transport closed", primary.isConnected)
                assertTrue("primary terminal closed", terminal.isOpen)
                assertTrue("primary SFTP channel closed", sftp.ls("/").any { it.name == "sentinel.txt" })
            } finally {
                sftp.close()
                terminal.close()
            }
        } finally {
            probes.shutdown()
            assertTrue("probe workers did not terminate", probes.awaitTermination(15, TimeUnit.SECONDS))
            primary.disconnect()
            server.close()
            root.deleteRecursively()
        }
    }

    private fun client() = SSHClient().apply {
        addHostKeyVerifier(PromiscuousVerifier())
        connectTimeout = 5_000
        timeout = 10_000
    }

    private class MatrixServer(root: File) : AutoCloseable {
        private val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(
                Files.createTempFile("sshpeaches-matrix-key-", ".ser"),
            )
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == USER && password == PASSWORD
            }
            fileSystemFactory = VirtualFileSystemFactory(root.toPath())
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
        }

        val port: Int get() = server.port
        fun start() = server.start()
        override fun close() { runCatching { server.stop(true) } }
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
    }
}