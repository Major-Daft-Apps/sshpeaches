package com.majordaftapps.sshpeaches.livetest

import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.scp.server.ScpCommandFactory
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ShellFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.security.PublicKey

private const val PASSWORD_USERNAME = "tester"
private const val PASSWORD_VALUE = "peaches-password"
private const val PUBLIC_KEY_USERNAME = "tester-key"
private const val READY_PREFIX = "LIVE_SSH_SERVER_READY"
private const val FORWARD_HTTP_RESPONSE = "SSHPEACHES_FORWARD_OK"

fun main(args: Array<String>) {
    val options = Options.parse(args)
    val stateRoot = options.stateDir.toAbsolutePath().normalize()
    val keyDir = stateRoot.resolve("keys").createDirectories()
    val sandboxParent = stateRoot.resolve("sandboxes").createDirectories()
    val sandboxRoot = Files.createTempDirectory(sandboxParent, "sandbox-").normalize()
    seedSandbox(sandboxRoot)
    val forwardHttpServer = createForwardHttpServer(options.httpPort)

    val server = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = options.port
        keyPairProvider = SimpleGeneratorHostKeyProvider(
            keyDir.resolve("${options.keyProfile}.ser")
        ).apply {
            setAlgorithm("RSA")
        }
        fileSystemFactory = VirtualFileSystemFactory(sandboxRoot)
        passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == PASSWORD_USERNAME && password == PASSWORD_VALUE
        }
        publickeyAuthenticator = PublickeyAuthenticator { username: String, _: PublicKey, _: org.apache.sshd.server.session.ServerSession ->
            username == PUBLIC_KEY_USERNAME
        }
        forwardingFilter = AcceptAllForwardingFilter.INSTANCE
        shellFactory = ShellFactory { _: ChannelSession ->
            FakeShellCommand(sandboxRoot)
        }
        commandFactory = ScpCommandFactory.Builder()
            .withDelegate(CommandFactory { _: ChannelSession, command: String ->
                FakeExecCommand(sandboxRoot, command)
            })
            .build()
        subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { forwardHttpServer.stop(0) }
        runCatching { server.stop(true) }
        runCatching { deleteRecursively(sandboxRoot, sandboxParent) }
    })

    forwardHttpServer.start()
    server.start()
    println("$READY_PREFIX port=${server.port} httpPort=${options.httpPort} sandbox=${sandboxRoot.absolutePathString()} keyProfile=${options.keyProfile}")
    val latch = CountDownLatch(1)
    latch.await()
}

private fun createForwardHttpServer(port: Int): HttpServer =
    HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
        createContext("/") { exchange ->
            val body = "$FORWARD_HTTP_RESPONSE ${exchange.requestURI.path}".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { stream ->
                stream.write(body)
            }
        }
        executor = null
    }

private fun seedSandbox(root: Path) {
    root.resolve("docs").createDirectories()
    root.resolve("uploads").createDirectories()
    root.resolve("downloads").createDirectories()
    root.resolve("docs").resolve("welcome.txt")
        .writeText("SSHPeaches live test sandbox\n", StandardCharsets.UTF_8)
    root.resolve("docs").resolve("notes.txt")
        .writeText("safe sandbox only\n", StandardCharsets.UTF_8)
}

private fun deleteRecursively(path: Path, sandboxParent: Path) {
    val normalized = path.toAbsolutePath().normalize()
    check(normalized.startsWith(sandboxParent.toAbsolutePath().normalize())) {
        "Refusing to delete outside sandbox parent: ${normalized.absolutePathString()}"
    }
    if (!normalized.exists()) return
    Files.walkFileTree(normalized, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            file.deleteIfExists()
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
            dir.deleteIfExists()
            return FileVisitResult.CONTINUE
        }
    })
}

private data class Options(
    val port: Int,
    val httpPort: Int,
    val stateDir: Path,
    val keyProfile: String
) {
    companion object {
        fun parse(args: Array<String>): Options {
            val parsed = args
                .mapNotNull { arg ->
                    val idx = arg.indexOf('=')
                    if (idx <= 2) null else arg.substring(2, idx) to arg.substring(idx + 1)
                }
                .toMap()
            val port = parsed["port"]?.toIntOrNull() ?: 56321
            val httpPort = parsed["httpPort"]?.toIntOrNull() ?: 56323
            val stateDir = Path.of(parsed["stateDir"] ?: Path.of("build", "live-ssh-server").toString())
            val keyProfile = parsed["keyProfile"]?.takeIf { it.isNotBlank() } ?: "primary"
            return Options(port = port, httpPort = httpPort, stateDir = stateDir, keyProfile = keyProfile)
        }
    }
}

private sealed class BaseSandboxCommand(
    private val sandboxRoot: Path
) : Command, Runnable {
    protected lateinit var stdinStream: InputStream
    protected lateinit var stdoutStream: OutputStream
    protected lateinit var stderrStream: OutputStream
    protected lateinit var completionCallback: ExitCallback
    protected var currentDirectory: String = "/"

    override fun setInputStream(inputStream: InputStream) {
        this.stdinStream = inputStream
    }

    override fun setOutputStream(outputStream: OutputStream) {
        this.stdoutStream = outputStream
    }

    override fun setErrorStream(errorStream: OutputStream) {
        this.stderrStream = errorStream
    }

    override fun setExitCallback(callback: ExitCallback) {
        this.completionCallback = callback
    }

    override fun start(channel: ChannelSession, env: org.apache.sshd.server.Environment) {
        Thread(this).start()
    }

    override fun destroy(channel: ChannelSession) {
    }

    protected fun resolvePath(pathToken: String?): Path {
        val token = pathToken?.trim().orEmpty()
        val virtualPath = when {
            token.isBlank() -> currentDirectory
            token.startsWith("/") -> token
            currentDirectory == "/" -> "/$token"
            else -> "$currentDirectory/$token"
        }
        val relative = virtualPath.removePrefix("/").takeIf { it.isNotBlank() } ?: "."
        val resolved = sandboxRoot.resolve(relative).normalize()
        require(resolved.startsWith(sandboxRoot.normalize())) { "Path escapes sandbox." }

        var probe: Path? = resolved
        while (probe != null && probe != sandboxRoot.parent) {
            if (probe.exists() && Files.isSymbolicLink(probe)) {
                error("Symlinks are not allowed.")
            }
            if (probe == sandboxRoot) {
                break
            }
            probe = probe.parent
        }
        return resolved
    }

    protected fun toVirtualPath(path: Path): String {
        val relative = path.normalize().relativeTo(sandboxRoot.normalize()).pathString
        return if (relative == ".") "/" else "/${relative.replace('\\', '/')}"
    }

    protected fun execute(commandLine: String, stdout: PrintWriter, stderr: PrintWriter): Int {
        val tokens = tokenize(commandLine)
        if (tokens.isEmpty()) {
            return 0
        }
        return when (val command = tokens.first()) {
            "pwd" -> {
                stdout.println(currentDirectory)
                0
            }
            "whoami" -> {
                stdout.println(PASSWORD_USERNAME)
                0
            }
            "uname" -> {
                val args = tokens.drop(1)
                if (args == listOf("-a")) {
                    stdout.println("Linux sshpeaches-live 6.1 test #1 SMP PREEMPT")
                } else {
                    stdout.println("Linux")
                }
                0
            }
            "help" -> {
                stdout.println("pwd ls cd cat echo mkdir touch rm mv cp uname whoami exit")
                0
            }
            "ls" -> {
                val target = resolvePath(tokens.getOrNull(1))
                if (!target.exists()) {
                    stderr.println("ls: not found")
                    1
                } else if (!target.isDirectory()) {
                    stdout.println(target.name)
                    0
                } else {
                    Files.list(target).use { children ->
                        children.sorted(compareBy<Path> { it.fileName.toString() })
                            .forEach { child -> stdout.println(child.fileName.toString()) }
                    }
                    0
                }
            }
            "cd" -> {
                val target = resolvePath(tokens.getOrNull(1))
                if (!target.exists() || !target.isDirectory()) {
                    stderr.println("cd: no such directory")
                    1
                } else {
                    currentDirectory = toVirtualPath(target)
                    0
                }
            }
            "cat" -> {
                val target = resolvePath(tokens.getOrNull(1))
                if (!target.exists() || target.isDirectory()) {
                    stderr.println("cat: not a file")
                    1
                } else {
                    stdout.print(target.readText(StandardCharsets.UTF_8))
                    if (!target.readText(StandardCharsets.UTF_8).endsWith("\n")) {
                        stdout.println()
                    }
                    0
                }
            }
            "echo" -> {
                stdout.println(tokens.drop(1).joinToString(" "))
                0
            }
            "mkdir" -> {
                val target = resolvePath(tokens.getOrNull(1))
                target.createDirectories()
                0
            }
            "touch" -> {
                val target = resolvePath(tokens.getOrNull(1))
                target.parent?.createDirectories()
                if (!target.exists()) {
                    target.writeText("", StandardCharsets.UTF_8)
                }
                0
            }
            "rm" -> {
                val target = resolvePath(tokens.getOrNull(1))
                if (!target.exists()) {
                    stderr.println("rm: not found")
                    1
                } else {
                    deletePath(target)
                    0
                }
            }
            "mv" -> {
                val source = resolvePath(tokens.getOrNull(1))
                val destination = resolvePath(tokens.getOrNull(2))
                destination.parent?.createDirectories()
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
                0
            }
            "cp" -> {
                val source = resolvePath(tokens.getOrNull(1))
                val destination = resolvePath(tokens.getOrNull(2))
                destination.parent?.createDirectories()
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                0
            }
            "exit", "logout" -> {
                130
            }
            else -> {
                stderr.println("$command: unsupported")
                127
            }
        }
    }

    private fun deletePath(path: Path) {
        if (!path.exists()) return
        if (!path.isDirectory()) {
            path.deleteIfExists()
            return
        }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                file.deleteIfExists()
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                dir.deleteIfExists()
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun tokenize(commandLine: String): List<String> {
        val pattern = Regex("""[^\s"']+|"([^"]*)"|'([^']*)'""")
        return pattern.findAll(commandLine)
            .map { match -> match.groups[1]?.value ?: match.groups[2]?.value ?: match.value }
            .toList()
    }
}

private class FakeExecCommand(
    sandboxRoot: Path,
    private val commandLine: String
) : BaseSandboxCommand(sandboxRoot) {
    override fun run() {
        val stdout = PrintWriter(stdoutStream.bufferedWriter(StandardCharsets.UTF_8), true)
        val stderr = PrintWriter(stderrStream.bufferedWriter(StandardCharsets.UTF_8), true)
        val exit = runCatching {
            execute(commandLine, stdout, stderr)
        }.getOrElse { error ->
            stderr.println(error.message ?: "command failed")
            1
        }
        stdout.flush()
        stderr.flush()
        completionCallback.onExit(exit)
    }
}

private class FakeShellCommand(
    sandboxRoot: Path
) : BaseSandboxCommand(sandboxRoot) {
    override fun run() {
        val reader = BufferedReader(InputStreamReader(stdinStream, StandardCharsets.UTF_8))
        val stdout = PrintWriter(stdoutStream.bufferedWriter(StandardCharsets.UTF_8), true)
        val stderr = PrintWriter(stderrStream.bufferedWriter(StandardCharsets.UTF_8), true)
        stdout.println("SSHPeaches live test shell")
        var exitCode = 0
        while (true) {
            stdout.print("tester:${currentDirectory}$ ")
            stdout.flush()
            val line = reader.readLine() ?: break
            exitCode = runCatching {
                execute(line, stdout, stderr)
            }.getOrElse { error ->
                stderr.println(error.message ?: "command failed")
                1
            }
            if (exitCode == 130) {
                exitCode = 0
                break
            }
        }
        stdout.flush()
        stderr.flush()
        completionCallback.onExit(exitCode)
    }
}
