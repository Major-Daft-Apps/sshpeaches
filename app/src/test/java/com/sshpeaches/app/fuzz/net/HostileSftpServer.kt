package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import net.schmizz.sshj.SSHClient
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystem
import org.apache.sshd.sftp.server.SftpSubsystemConfigurator
import org.apache.sshd.sftp.server.SftpSubsystemFactory

/**
 * An in-process SFTP server on 127.0.0.1 that serves a real sandbox directory but can lie:
 * rename entries in directory listings and answer READs with empty DATA or an early EOF.
 */
internal class HostileSftpServer(
    private val sandbox: File,
    hostKey: File,
    private val knownHosts: File,
    passwordAuthenticator: PasswordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD },
    /** Runs for every exec request on its own thread; returning ends the command with status 0. */
    private val execHandler: ((command: String, stdout: OutputStream, stderr: OutputStream) -> Unit)? = null
) : Closeable {
    /** Real file name -> name reported in directory listings. */
    @Volatile var listingNames: Map<String, String> = emptyMap()
    @Volatile var readBehaviour: ReadBehaviour = ReadBehaviour.Honest

    sealed class ReadBehaviour {
        object Honest : ReadBehaviour()
        object EmptyData : ReadBehaviour()
        data class EofAt(val bytes: Long) : ReadBehaviour()
    }

    private inner class Subsystem(channel: ChannelSession, configurator: SftpSubsystemConfigurator) :
        SftpSubsystem(channel, configurator) {
        override fun getShortName(f: Path): String {
            val real = super.getShortName(f)
            return listingNames[real] ?: real
        }

        override fun doRead(
            id: Int,
            handle: String,
            offset: Long,
            length: Int,
            data: ByteArray,
            doff: Int,
            eof: AtomicReference<Boolean>?
        ): Int = when (val behaviour = readBehaviour) {
            ReadBehaviour.Honest -> super.doRead(id, handle, offset, length, data, doff, eof)
            ReadBehaviour.EmptyData -> 0
            is ReadBehaviour.EofAt ->
                if (offset >= behaviour.bytes) -1
                else super.doRead(id, handle, offset, minOf(length.toLong(), behaviour.bytes - offset).toInt(), data, doff, eof)
        }
    }

    private val factory = object : SftpSubsystemFactory() {
        override fun createSubsystem(channel: ChannelSession): Command = Subsystem(channel, this)
    }

    val server: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(hostKey.toPath())
        fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
        this.passwordAuthenticator = passwordAuthenticator
        subsystemFactories = listOf(factory)
        execHandler?.let { handler -> commandFactory = CommandFactory { _, command -> ExecCommand(command, handler) } }
        start()
    }

    private class ExecCommand(
        private val command: String,
        private val handler: (String, OutputStream, OutputStream) -> Unit
    ) : Command {
        private var out: OutputStream? = null
        private var err: OutputStream? = null
        private var exit: ExitCallback? = null
        private var worker: Thread? = null
        override fun setInputStream(input: InputStream) = Unit
        override fun setOutputStream(output: OutputStream) { out = output }
        override fun setErrorStream(error: OutputStream) { err = error }
        override fun setExitCallback(callback: ExitCallback) { exit = callback }
        override fun start(channel: ChannelSession, env: Environment) {
            worker = Thread({
                runCatching { handler(command, out!!, err!!) }
                runCatching { out?.flush(); err?.flush() }
                exit?.onExit(0)
            }, "hostile-exec").apply { isDaemon = true; start() }
        }
        override fun destroy(channel: ChannelSession) {
            worker?.interrupt()
        }
    }

    fun connect(): SSHClient {
        val host = HostConnection(
            id = "fuzz", name = "fuzz", host = "127.0.0.1", port = server.port,
            username = USER, preferredAuth = AuthMethod.PASSWORD
        )
        return SshClientProvider.createClientForTesting(knownHostsFile = knownHosts, host = host).apply {
            connect(host.host, host.port)
            authPassword(USER, PASSWORD)
        }
    }

    override fun close() {
        runCatching { server.stop(true) }
    }

    companion object {
        const val USER = "fuzzer"
        const val PASSWORD = "fuzz-password"
    }
}
