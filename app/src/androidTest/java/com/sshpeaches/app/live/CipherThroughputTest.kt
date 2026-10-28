package com.majordaftapps.sshpeaches.app.live

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.security.SecureRandom
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.transport.cipher.Cipher
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How fast this device encrypts and decrypts SSH packets with each cipher the app offers, through sshj's own cipher
 * code: the CPU cost of moving file data, without the network. Run with -e cipherBenchmark 1; results are logged with
 * tag NetScenario.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class CipherThroughputTest {

    @Test
    fun cipherThroughput() {
        assumeTrue("needs -e cipherBenchmark 1", InstrumentationRegistry.getArguments().getString("cipherBenchmark") != null)
        // The ciphers a real connection offers, taken from its configuration (sshj's classes are renamed in
        // release builds, so they can't be named here).
        val client = SshClientProvider.createClient(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            host = HostConnection(id = "bench", name = "bench", host = "localhost", username = "bench", preferredAuth = AuthMethod.PASSWORD),
            autoTrustUnknownHostKey = false
        )
        val keySizes = mapOf("chacha20-poly1305@openssh.com" to 64, "aes128-gcm@openssh.com" to 16, "aes256-gcm@openssh.com" to 32)
        val ciphers: List<Pair<Factory.Named<Cipher>, Int>> = client.transport.config.cipherFactories
            .mapNotNull { factory -> keySizes[factory.name]?.let { factory to it } }
        client.close()
        val results = ciphers.map { (factory, keySize) -> "${factory.name}: %.0f MB/s".format(measure(factory, keySize)) }
        val provider = runCatching { javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").provider.name }.getOrNull()
        Log.i("NetScenario", "== cipher-throughput (AES-GCM provider: $provider)\n" + results.joinToString("\n"))
    }

    /** Megabytes a second of packets encrypted and then decrypted again, like one direction of a transfer. */
    private fun measure(factory: Factory.Named<Cipher>, keySize: Int): Double {
        val random = SecureRandom()
        val key = ByteArray(keySize).also(random::nextBytes)
        val encrypt = factory.create()
        val decrypt = factory.create()
        val iv = ByteArray(encrypt.ivSize).also(random::nextBytes)
        encrypt.init(Cipher.Mode.Encrypt, key, iv)
        decrypt.init(Cipher.Mode.Decrypt, key, iv)
        val payload = PACKET_BYTES
        val packet = ByteArray(4 + payload + encrypt.authenticationTagSize).also(random::nextBytes)
        var sequence = 0L
        fun one() {
            encrypt.setSequenceNumber(sequence)
            encrypt.updateWithAAD(packet, 0, 4, payload)
            decrypt.setSequenceNumber(sequence)
            decrypt.updateWithAAD(packet, 0, 4, payload)
            sequence += 1
        }
        repeat(WARM_UP_PACKETS) { one() }
        val start = System.nanoTime()
        repeat(MEASURED_PACKETS) { one() }
        val seconds = (System.nanoTime() - start) / 1e9
        return MEASURED_PACKETS.toDouble() * payload / 1e6 / seconds
    }

    private companion object {
        const val PACKET_BYTES = 32 * 1024
        const val WARM_UP_PACKETS = 2_000
        const val MEASURED_PACKETS = 8_000
    }
}
