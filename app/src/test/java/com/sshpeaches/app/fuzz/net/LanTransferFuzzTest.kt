package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.transfer.LanTransfer
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Drives [LanTransfer.receive] against a hostile "sender" on loopback: bogus length prefixes,
 * truncated and corrupted frames, slow drips, and fuzzed pairing URIs.
 */
class LanTransferFuzzTest {

    private val loopback = InetAddress.getLoopbackAddress()
    private val allowLoopback: (InetAddress) -> Boolean = { it.isLoopbackAddress }
    private val magic = "SSHPEACHES-LAN1\n".toByteArray(Charsets.US_ASCII)
    private val minFrame = magic.size + 16 + 12 + 16

    /** Serves [bytes] to the first connection, optionally pausing between [chunks]. */
    private fun hostileSender(chunks: List<ByteArray>, pauseMs: Long = 0L, holdOpenMs: Long = 0L): ServerSocket {
        val server = ServerSocket()
        server.bind(InetSocketAddress(loopback, 0), 1)
        Thread({
            runCatching {
                server.accept().use { socket ->
                    val out = socket.getOutputStream()
                    chunks.forEach { chunk ->
                        out.write(chunk)
                        out.flush()
                        if (pauseMs > 0) Thread.sleep(pauseMs)
                    }
                    if (holdOpenMs > 0) Thread.sleep(holdOpenMs)
                }
            }
            runCatching { server.close() }
        }, "hostile-lan-sender").apply { isDaemon = true }.start()
        return server
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun receiveFrom(server: ServerSocket, code: String, timeoutMs: Int): Result<String> =
        runCatching { LanTransfer.receive("127.0.0.1", server.localPort, code, timeoutMs, allowLoopback) }

    @Test
    fun hostileFramesOnlyEverFailWithIoExceptionsAndQuickly() {
        val random = Random(Fuzz.SEED)
        val code = LanTransfer.newCode()
        val sizes = listOf(Int.MIN_VALUE, -1, 0, 1, 3, minFrame - 1, minFrame, minFrame + 1, 4096, 32 * 1024 * 1024, 32 * 1024 * 1024 + 1, Int.MAX_VALUE)
        repeat(60) { iteration ->
            val declared = if (iteration < sizes.size) sizes[iteration] else random.nextInt(-10, 8192)
            val bodyLength = when (random.nextInt(4)) {
                0 -> 0
                1 -> declared.coerceIn(0, 8192)
                2 -> (declared.coerceIn(0, 8192) - random.nextInt(1, 32)).coerceAtLeast(0)
                else -> random.nextInt(0, 8192)
            }
            val body = ByteArray(bodyLength).also(random::nextBytes)
            if (random.nextBoolean() && body.size >= magic.size) magic.copyInto(body)
            val server = hostileSender(listOf(intBytes(declared), body))
            val outcome = Fuzz.withinMillis(5_000, "receive#$iteration") { receiveFrom(server, code, timeoutMs = 700) }
            val error = outcome.exceptionOrNull()
            assertNotNull("iteration $iteration: garbage decrypted as a payload", error)
            assertTrue(
                "iteration $iteration (declared=$declared body=$bodyLength): unexpected ${error!!.javaClass.name}: ${error.message}",
                error is IOException
            )
        }
    }

    @Test
    fun corruptedAndTruncatedRealFramesAreRejectedAsIoExceptions() {
        val random = Random(Fuzz.SEED + 1)
        val code = LanTransfer.newCode()
        val frame = LanTransfer.encrypt("{\"hosts\":[{\"name\":\"web\",\"host\":\"10.0.0.5\"}]}", code)
        repeat(25) { iteration ->
            val mutated = frame.copyOf()
            when (iteration % 3) {
                0 -> mutated[random.nextInt(mutated.size)] = (mutated[random.nextInt(mutated.size)].toInt() xor (1 shl random.nextInt(8))).toByte()
                1 -> repeat(random.nextInt(1, 8)) { mutated[random.nextInt(mutated.size)] = random.nextInt(256).toByte() }
                else -> Unit
            }
            val sent = if (iteration % 3 == 2) mutated.copyOf(random.nextInt(0, mutated.size)) else mutated
            val error = runCatching { LanTransfer.decrypt(sent, code) }.exceptionOrNull()
            if (sent.contentEquals(frame)) return@repeat
            assertTrue("iteration $iteration: ${error?.javaClass?.name}: ${error?.message}", error is IOException)
        }
    }

    /**
     * The receive timeout is applied per read, not to the whole transfer, so a sender that drips one
     * byte just inside the timeout keeps the receiver busy for far longer than [timeoutMs]
     * (in the app: the 15 s default, with a frame of up to 32 MiB, i.e. effectively forever).
     */
    @Test
    fun aSlowDrippingSenderCannotHoldTheReceiverPastItsTimeout() {
        val timeoutMs = 400
        val frameBytes = 30
        val chunks = listOf(intBytes(frameBytes)) + List(frameBytes) { byteArrayOf(it.toByte()) }
        val server = hostileSender(chunks, pauseMs = 150L)
        val elapsed = Fuzz.elapsedMillis {
            Fuzz.withinMillis(20_000, "slow-drip") { receiveFrom(server, LanTransfer.newCode(), timeoutMs) }
        }
        assertTrue("receive(timeoutMs=$timeoutMs) was held for ${elapsed}ms by a 1-byte-per-150ms sender", elapsed < timeoutMs * 3L)
    }

    /**
     * A local peer that connects to the sender and never reads makes [LanTransfer.Sender.awaitTransfer]
     * block in write() (SO_TIMEOUT doesn't cover writes), and [LanTransfer.Sender.close] doesn't
     * close the accepted socket, so the sending thread can never be released.
     */
    @Test
    fun aPeerThatNeverReadsCannotWedgeTheSenderAfterClose() {
        val payload = "x".repeat(24 * 1024 * 1024)
        val sender = LanTransfer.Sender(payload, loopback, allowPeer = allowLoopback)
        val outcome = AtomicReference<Result<InetAddress>?>(null)
        val worker = Thread({ outcome.set(runCatching { sender.awaitTransfer(60_000) }) }, "lan-sender").apply {
            isDaemon = true
            start()
        }
        val peer = Socket()
        try {
            peer.receiveBufferSize = 4096
            peer.connect(InetSocketAddress(loopback, sender.port), 2_000)
            Thread.sleep(1_500)
            sender.close()
            worker.join(3_000)
            assertTrue("awaitTransfer still blocked writing to a stalled peer 3s after close()", !worker.isAlive)
        } finally {
            runCatching { peer.close() }
            worker.join(5_000)
        }
    }

    @Test
    fun pairingUriParserNeverThrowsAndOnlyReturnsWellFormedPairings() {
        val random = Random(Fuzz.SEED + 2)
        val valid = LanTransfer.pairingUri("192.168.1.20", 4444, LanTransfer.newCode())
        repeat(5_000) { iteration ->
            val text = when (random.nextInt(4)) {
                0 -> Fuzz.string(random, 80)
                1 -> valid.substring(0, random.nextInt(valid.length + 1)) + Fuzz.string(random, 8)
                2 -> buildString {
                    append(valid)
                    val at = random.nextInt(length + 1)
                    insert(at, Fuzz.string(random, 6))
                }
                else -> "sshpeaches-lan://${Fuzz.string(random, 12)}:${random.nextInt(-5, 99999)}/${Fuzz.string(random, 16)}"
            }
            val pairing = try {
                Fuzz.withinMillis(2_000, "parsePairingUri#$iteration") { LanTransfer.parsePairingUri(text) }
            } catch (error: AssertionError) {
                throw error
            } catch (error: Throwable) {
                fail("parsePairingUri threw ${error.javaClass.name} for ${text.quoted()}")
                return
            }
            if (pairing != null) {
                assertTrue(text.quoted(), pairing.port in 1..65535)
                assertEquals(text.quoted(), LanTransfer.CODE_LENGTH, pairing.code.length)
                assertEquals(text.quoted(), pairing.code, LanTransfer.normalizeCode(pairing.code))
                assertTrue(text.quoted(), pairing.host.isNotEmpty() && pairing.host.none { it == '/' || it == ':' })
            }
        }
    }

    private fun String.quoted(): String = buildString {
        append('"')
        this@quoted.forEach { c -> if (c.code < 0x20 || c.code > 0x7e) append("\\u%04x".format(c.code)) else append(c) }
        append('"')
    }
}
