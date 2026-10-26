package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import net.schmizz.sshj.SSHClient
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The pipelined directory listing and the transfer connection pool against an in-process SFTP server. */
class SftpPipelinedTransferPoolSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: SshServer
    private lateinit var host: HostConnection
    private val clients = mutableListOf<SSHClient>()

    @Before
    fun startServer() {
        sandbox = temp.newFolder("sandbox")
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        host = HostConnection(
            id = "pool", name = "pool", host = "127.0.0.1", port = server.port,
            username = USER, preferredAuth = AuthMethod.PASSWORD
        )
    }

    @After
    fun stopServer() {
        clients.forEach { runCatching { it.disconnect() } }
        runCatching { server.stop(true) }
    }

    private fun connect(): SSHClient =
        SshClientProvider.createClientForTesting(knownHostsFile = temp.newFile(), host = host).apply {
            connect(host.host, host.port)
            authPassword(USER, PASSWORD)
            synchronized(clients) { clients += this }
        }

    @Test
    fun pipelinedListingMatchesTheOneRequestAtATimeListing() {
        val dir = File(sandbox, "dir").apply { mkdirs() }
        repeat(350) { index -> File(dir, "file-%03d".format(index)).writeText("x".repeat(index)) }
        File(dir, "sub").mkdirs()
        File(dir, ".hidden").writeText("h")
        Files.createSymbolicLink(File(dir, "to-sub").toPath(), File(dir, "sub").toPath())
        Files.createSymbolicLink(File(dir, "to-file").toPath(), File(dir, "file-001").toPath())
        Files.createSymbolicLink(File(dir, "broken").toPath(), File(dir, "nowhere").toPath())
        connect().newSFTPClient().use { sftp ->
            listOf(true, false).forEach { resolveLinks ->
                listOf("/dir", "/dir/", "dir", "/dir/sub/..").forEach { path ->
                    val expected = loadSftpDirectory(
                        requestedPath = path,
                        resolveSymbolicLinks = resolveLinks,
                        canonicalize = sftp::canonicalize,
                        listDirectory = sftp::ls,
                        stat = sftp::stat
                    )
                    val actual = loadSftpDirectoryPipelined(sftp, path, resolveLinks)
                    assertEquals("$path links=$resolveLinks", expected, actual)
                }
            }
            val listed = loadSftpDirectoryPipelined(sftp, "/dir", resolveSymbolicLinks = true)
            assertEquals(355, listed.entries.size)
            assertEquals(true, listed.entries.single { it.name == "to-sub" }.linkTargetIsDirectory)
            assertEquals(false, listed.entries.single { it.name == "to-file" }.linkTargetIsDirectory)
            // The session's SFTP channel still works after the pipelined requests.
            assertEquals(0, loadSftpDirectoryPipelined(sftp, "/dir/sub", true).entries.size)
        }
    }

    @Test
    fun listingAMissingDirectoryFails() {
        connect().newSFTPClient().use { sftp ->
            try {
                loadSftpDirectoryPipelined(sftp, "/no/such/dir", resolveSymbolicLinks = true)
                fail("listing a missing directory must fail")
            } catch (_: IOException) {
            }
            assertEquals(0, loadSftpDirectoryPipelined(sftp, "/", true).entries.size)
        }
    }

    private fun pool(
        max: Int,
        session: SSHClient? = null,
        open: () -> SSHClient = ::connect,
        fallbacks: AtomicInteger = AtomicInteger()
    ) = SftpTransferPool(
        maxConnections = { max },
        openConnection = open,
        sessionClient = { session },
        schedule = { _, _ -> },
        onFallback = { fallbacks.incrementAndGet() }
    )

    @Test
    fun poolOpensUpToItsLimitAndReusesConnections() {
        val opened = AtomicInteger()
        val pool = pool(max = 2, open = { opened.incrementAndGet(); connect() })
        val first = pool.acquire { false }!!
        val second = pool.acquire { false }!!
        assertFalse(first.shared)
        // Both are out: a third caller waits, and gives up when told to stop.
        val stopAt = System.currentTimeMillis() + 300
        assertNull(pool.acquire { System.currentTimeMillis() > stopAt })
        first.release()
        val third = pool.acquire { false }!!
        assertEquals("a returned connection is reused", first.sftp, third.sftp)
        assertEquals(2, opened.get())
        second.discard()
        pool.acquire { false }!!.release()
        assertEquals("a discarded connection is replaced", 3, opened.get())
        third.release()
        pool.close()
    }

    @Test
    fun poolFallsBackToTheSessionConnectionWhenASecondLoginFails() {
        val session = connect()
        val fallbacks = AtomicInteger()
        val pool = pool(max = 4, session = session, open = { throw IOException("too many logins") }, fallbacks = fallbacks)
        val lease = pool.acquire { false }!!
        assertTrue(lease.shared)
        assertTrue(pool.usingSessionConnection)
        assertEquals(1, fallbacks.get())
        lease.sftp.ls("/")
        // Only one transfer at a time shares the session's connection.
        val stopAt = System.currentTimeMillis() + 300
        assertNull(pool.acquire { System.currentTimeMillis() > stopAt })
        lease.release()
        assertTrue(session.isConnected)
        pool.acquire { false }!!.release()
        pool.close()
    }

    @Test
    fun poolMakesDoWithTheConnectionsAServerAllows() {
        val opened = AtomicInteger()
        val pool = pool(max = 4, open = {
            if (opened.incrementAndGet() > 2) throw IOException("MaxSessions") else connect()
        })
        val leases = listOf(pool.acquire { false }!!, pool.acquire { false }!!)
        val stopAt = System.currentTimeMillis() + 300
        assertNull("a third caller waits for one of the two", pool.acquire { System.currentTimeMillis() > stopAt })
        assertFalse(pool.usingSessionConnection)
        leases[0].release()
        assertNotNull(pool.acquire { false })
        pool.close()
    }

    @Test
    fun warmUpOpensOneConnectionAheadAndCloseShutsEverything() {
        val opened = AtomicInteger()
        val pool = pool(max = 3, open = { opened.incrementAndGet(); connect() })
        pool.warmUp()
        assertEquals(1, opened.get())
        val lease = pool.acquire { false }!!
        assertEquals("the warm connection is used first", 1, opened.get())
        lease.release()
        pool.close()
        assertTrue(clients.none { it.isConnected })
        try {
            pool.acquire { false }
            fail("a closed pool lends nothing")
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
    }
}
