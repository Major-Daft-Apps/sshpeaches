package com.majordaftapps.sshpeaches.app.testutil

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.local.SshPeachesDatabase
import com.majordaftapps.sshpeaches.app.data.local.asEntity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeConfig
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.state.TerminalBellMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.util.UUID
import kotlinx.coroutines.runBlocking

object AppStateSeeder {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    fun seedHost(host: HostConnection, password: String? = null) {
        val appContext = context
        runBlocking {
            SshPeachesDatabase.get(appContext).hostDao().upsert(host.asEntity())
        }
        if (!password.isNullOrBlank()) {
            SecurityManager.init(appContext)
            runCatching { SecurityManager.unlock() }
            SecurityManager.storeHostPassword(host.id, password)
        }
    }

    fun seedIdentity(
        identity: Identity,
        privateKey: String,
        publicKey: String? = null,
        keyPassphrase: String? = null
    ) {
        val appContext = context
        runBlocking {
            SshPeachesDatabase.get(appContext).identityDao().upsert(identity.asEntity())
        }
        SecurityManager.init(appContext)
        runCatching { SecurityManager.unlock() }
        SecurityManager.storeIdentityKey(identity.id, privateKey)
        val exportedPublicKey = publicKey ?: SshKeyGenerator.derivePublicKeyFromPrivate(privateKey, identity.label)
        if (!exportedPublicKey.isNullOrBlank()) {
            SecurityManager.storeIdentityPublicKey(identity.id, exportedPublicKey)
        }
        SecurityManager.storeIdentityKeyPassphrase(identity.id, keyPassphrase)
    }

    fun seedIdentityRecord(identity: Identity) {
        runBlocking {
            SshPeachesDatabase.get(context).identityDao().upsert(identity.asEntity())
        }
    }

    fun seedPortForward(forward: PortForward) {
        runBlocking {
            SshPeachesDatabase.get(context).portForwardDao().upsert(forward.asEntity())
        }
    }

    fun seedSnippet(snippet: Snippet) {
        runBlocking {
            SshPeachesDatabase.get(context).snippetDao().upsert(snippet.asEntity())
        }
    }

    fun seedUptimeConfig(
        hostId: String,
        method: UptimeCheckMethod = UptimeCheckMethod.TCP,
        port: Int = 22,
        intervalMinutes: Int = 15,
        enabled: Boolean = true
    ) {
        runBlocking {
            SshPeachesDatabase.get(context).hostUptimeConfigDao().upsert(
                HostUptimeConfig(
                    hostId = hostId,
                    method = method,
                    port = port,
                    intervalMinutes = intervalMinutes,
                    enabled = enabled
                ).asEntity()
            )
        }
    }

    fun configureSettings(
        themeMode: ThemeMode? = null,
        hostKeyPrompt: Boolean? = null,
        autoTrustHostKey: Boolean? = null,
        diagnostics: Boolean? = null,
        includeSecretsInQr: Boolean? = null,
        allowBackgroundSessions: Boolean? = null,
        biometricLock: Boolean? = null,
        terminalSelectionMode: TerminalSelectionMode? = null,
        terminalBellMode: TerminalBellMode? = null,
        terminalMarginPx: Int? = null,
        moshServerCommand: String? = null
    ) {
        val appContext = context
        runBlocking {
            SettingsStore.init(appContext)
            themeMode?.let { SettingsStore.setThemeMode(it) }
            hostKeyPrompt?.let { SettingsStore.setHostKeyPromptEnabled(it) }
            autoTrustHostKey?.let { SettingsStore.setAutoTrustHostKeyEnabled(it) }
            diagnostics?.let { SettingsStore.setDiagnosticsEnabled(it) }
            includeSecretsInQr?.let { SettingsStore.setIncludeSecretsInQr(it) }
            allowBackgroundSessions?.let { SettingsStore.setAllowBackgroundSessions(it) }
            biometricLock?.let { SettingsStore.setBiometricLockEnabled(it) }
            terminalSelectionMode?.let { SettingsStore.setTerminalSelectionMode(it) }
            terminalBellMode?.let { SettingsStore.setTerminalBellMode(it) }
            terminalMarginPx?.let { SettingsStore.setTerminalMarginPx(it) }
            moshServerCommand?.let { SettingsStore.setMoshServerCommand(it) }
        }
    }

    fun configurePin(pin: String?, locked: Boolean = false) {
        val appContext = context
        SecurityManager.init(appContext)
        if (pin.isNullOrBlank()) {
            runCatching { SecurityManager.clearPin() }
            runCatching { SecurityManager.unlock() }
            return
        }
        SecurityManager.setPin(pin)
        if (locked) {
            SecurityManager.lock()
        } else {
            SecurityManager.unlock()
        }
    }

    fun seedKeyboardLayout(slots: List<KeyboardSlotAction>) {
        val appContext = context
        runBlocking {
            SettingsStore.init(appContext)
            SettingsStore.setKeyboardLayout(slots)
        }
    }

    fun generateIdentityFixture(
        label: String = "Live Test Identity",
        username: String = "tester-key"
    ): GeneratedIdentityFixture {
        val generated = SshKeyGenerator.generate(
            IdentityKeyGenerationSpec(
                algorithm = IdentityKeyAlgorithm.RSA,
                comment = username
            )
        )
        val identity = Identity(
            id = UUID.randomUUID().toString(),
            label = label,
            fingerprint = generated.fingerprint,
            username = username,
            createdEpochMillis = System.currentTimeMillis(),
            hasPrivateKey = true
        )
        return GeneratedIdentityFixture(
            identity = identity,
            privateKey = generated.privateKey,
            publicKey = generated.publicKey
        )
    }

    data class GeneratedIdentityFixture(
        val identity: Identity,
        val privateKey: String,
        val publicKey: String
    )
}
