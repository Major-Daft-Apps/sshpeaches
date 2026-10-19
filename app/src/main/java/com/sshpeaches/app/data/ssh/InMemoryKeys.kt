package com.majordaftapps.sshpeaches.app.data.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils

/**
 * Loads a private key for sshj straight from memory. The app used to write the decrypted key to a temporary file
 * for sshj to read, which put it on disk (briefly, but in plain text) every time it logged in.
 */
internal fun SSHClient.loadPrivateKeyFromMemory(privateKey: String, passphrase: String?): KeyProvider {
    val normalized = privateKey.trim().let { if (it.endsWith("\n")) it else "$it\n" }
    val passwordFinder = passphrase?.takeIf { it.isNotBlank() }?.let { PasswordUtils.createOneOff(it.toCharArray()) }
    return loadKeys(normalized, null, passwordFinder)
}
