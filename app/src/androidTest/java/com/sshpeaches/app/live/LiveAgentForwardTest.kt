package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Agent forwarding against a real OpenSSH server and its own tools, given with the same
 * -e liveMoshHost/liveMoshPort/liveMoshUsername/liveMoshPassword as [LiveMoshTest]. In the terminal, `ssh-add -L`
 * must list the phone's keys, and `ssh` from the server back to itself must get in with each kind of key, signed on
 * the phone. Without the setting the server has no agent to talk to.
 */
@OptIn(DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveAgentForwardTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val sshHost: String? get() = arguments.getString("liveMoshHost")
    private val sshPort: Int get() = arguments.getString("liveMoshPort")?.toIntOrNull() ?: 22
    private val sshUser: String get() = arguments.getString("liveMoshUsername") ?: "mosher"
    private val sshPassword: String? get() = arguments.getString("liveMoshPassword")

    private fun host(id: String, forwardAgent: Boolean) = HostConnection(
        id = id, name = id, host = sshHost!!, port = sshPort, username = sshUser,
        preferredAuth = AuthMethod.PASSWORD, forwardAgent = forwardAgent
    )

    private class Seeded(val label: String, val publicKey: String) {
        /** The key itself, without its type and comment. */
        val body: String get() = publicKey.trim().split(' ')[1]
    }

    private fun seedKey(algorithm: IdentityKeyAlgorithm): Seeded {
        val label = "agent ${algorithm.name.lowercase()}"
        val generated = SshKeyGenerator.generate(IdentityKeyGenerationSpec(algorithm = algorithm, rsaBits = 2048, comment = label))
        AppStateSeeder.seedIdentity(
            identity = Identity(
                id = UUID.randomUUID().toString(), label = label, fingerprint = generated.fingerprint,
                createdEpochMillis = System.currentTimeMillis(), hasPrivateKey = true
            ),
            privateKey = generated.privateKey,
            publicKey = generated.publicKey
        )
        return Seeded(label, generated.publicKey)
    }

    @Test
    fun theServerListsThePhonesKeysAndLogsInWithEachOfThem() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        val keys = listOf(IdentityKeyAlgorithm.ED25519, IdentityKeyAlgorithm.RSA, IdentityKeyAlgorithm.ECDSA).map(::seedKey)
        val marker = "sshpeaches-agent-test-${UUID.randomUUID().toString().take(8)}"
        withSession(host("agent-on", forwardAgent = true)) { service, id, log ->
            check(log.any { it.startsWith("Agent forwarding is on") }) { "$log" }
            try {
                type(service, id, "ssh-add -L; echo LISTED-\$((40+2))")
                waitForOutput(service, id, "LISTED-42")
                val listed = service.shellOutputFlow().value[id].orEmpty().filterNot { it == '\r' || it == '\n' }
                keys.forEach { key ->
                    check(listed.contains(key.body)) { "ssh-add -L doesn't list ${key.label}: ${service.shellOutputFlow().value[id]}" }
                }
                type(service, id, "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys")
                keys.forEachIndexed { index, key ->
                    // Only this key is let in, so getting in means the phone signed with it.
                    type(service, id, "sed -i '/$marker/d' ~/.ssh/authorized_keys; echo '${key.publicKey.trim().split(' ').take(2).joinToString(" ")} $marker' >> ~/.ssh/authorized_keys")
                    type(
                        service, id,
                        "ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null " +
                            "-o PreferredAuthentications=publickey -o LogLevel=ERROR -p $sshPort $sshUser@127.0.0.1 " +
                            "'echo IN-$index-\$((40+2))'"
                    )
                    waitForOutput(service, id, "IN-$index-42")
                    waitFor("the session log to name ${key.label}") {
                        log.any { it.contains("with your key \"${key.label}\"") }
                    }
                }
            } finally {
                type(service, id, "sed -i '/$marker/d' ~/.ssh/authorized_keys")
                Thread.sleep(500)
            }
        }
    }

    @Test
    fun withoutTheSettingTheServerHasNoAgent() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        seedKey(IdentityKeyAlgorithm.ED25519)
        withSession(host("agent-off", forwardAgent = false)) { service, id, log ->
            type(service, id, "echo SOCK=[\$SSH_AUTH_SOCK]-\$((40+2))")
            waitForOutput(service, id, "SOCK=[]-42")
            check(log.none { it.contains("Agent forwarding") }) { "$log" }
        }
    }

    private fun type(service: SessionService, id: String, command: String) {
        service.sendShellInput(id, "$command\n")
        Thread.sleep(300)
    }

    private fun waitForOutput(service: SessionService, id: String, expected: String) {
        waitFor("$expected in the terminal") {
            service.shellOutputFlow().value[id].orEmpty().filterNot { it == '\r' || it == '\n' }.contains(expected)
        }
    }

    private fun withSession(host: HostConnection, block: (SessionService, String, List<String>) -> Unit) {
        val id = host.id
        val log = CopyOnWriteArrayList<String>()
        val collector = GlobalScope.launch(Dispatchers.Default) {
            SessionLogBus.entries.collect { if (it.hostId == id) log += it.message }
        }
        try {
            withSessionService { service ->
                service.startSession(
                    requestedSessionId = id, host = host, mode = ConnectionMode.SSH, passwordOverride = sshPassword,
                    autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
                )
                try {
                    waitFor("the session to open") {
                        val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == id }
                        check(snapshot?.status != SessionStatus.ERROR) { "Session failed: ${snapshot?.statusMessage}" }
                        snapshot?.status == SessionStatus.ACTIVE
                    }
                    block(service, id, log)
                } finally {
                    service.stopSession(id)
                }
            }
        } finally {
            collector.cancel()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 60_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(50)
        }
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService()
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(10, TimeUnit.SECONDS))
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }
}
