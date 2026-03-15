package com.majordaftapps.sshpeaches.app.testutil

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyPairGenerator
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts

object KnownHostsSeeder {
    fun seedMismatchedHostKey(host: String, port: Int) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val knownHostsFile = File(appContext.filesDir, "known_hosts").apply {
            parentFile?.mkdirs()
            if (!exists()) {
                createNewFile()
            }
        }
        val generatedKey = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair().public
        val keyType = KeyType.fromKey(generatedKey)
        val knownHosts = OpenSSHKnownHosts(knownHostsFile)
        listOf(host.trim(), "[${host.trim()}]:$port")
            .filter { it.isNotBlank() }
            .forEach { hostname ->
                knownHosts.entries().removeAll { entry ->
                    runCatching { entry.appliesTo(keyType, hostname) }.getOrDefault(false)
                }
                knownHosts.entries().add(
                    OpenSSHKnownHosts.HostEntry(
                        null,
                        hostname,
                        keyType,
                        generatedKey
                    )
                )
            }
        knownHosts.write()
    }
}
