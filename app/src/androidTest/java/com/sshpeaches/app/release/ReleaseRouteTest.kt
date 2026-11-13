package com.majordaftapps.sshpeaches.app.release

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
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the minified release build, a session goes through a jump host and a forwarded agent signs a login. Both lean
 * on the SSH library's insides (a subclass of its client, a channel opener and a hand-written channel request found
 * by name), which R8 may rename, strip or refuse to build: the first release build with them failed in R8. Against a
 * real OpenSSH server given with the same -e boostHost/boostPort/boostUser/boostPassword as [ReleaseBoostTest];
 * skipped without them. The fuller checks are LiveJumpHostTest and LiveAgentForwardTest on the debug build.
 */
@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class ReleaseRouteTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sshHost: String? get() = args.getString("boostHost")
    private val sshPort: Int get() = args.getString("boostPort")!!.toInt()
    private val sshUser: String get() = args.getString("boostUser")!!
    private val sshPassword: String? get() = args.getString("boostPassword")

    @Test
    fun aTerminalRunsThroughAJumpHost() {
        assumeTrue("needs -e boostHost", sshHost != null)
        AppStateSeeder.seedHost(
            HostConnection(
                id = "release-jump", name = "Gate", host = sshHost!!, port = sshPort, username = sshUser,
                preferredAuth = AuthMethod.PASSWORD, hasPassword = true
            ),
            password = sshPassword
        )
        // 127.0.0.1 is this device from here, so a session that opens went through the jump host.
        val target = HostConnection(
            id = "release-jump-target", name = "behind the gate", host = "127.0.0.1", port = sshPort,
            username = sshUser, preferredAuth = AuthMethod.PASSWORD, jumpHostId = "release-jump"
        )
        withSession(target) { service, id, log ->
            type(service, id, "echo J-\$((40+2))")
            waitForOutput(service, id, "J-42")
            check(log.any { it == "Connected through Gate." }) { "$log" }
        }
    }

    @Test
    fun aForwardedAgentListsAndSignsWithAKeyOnThePhone() {
        assumeTrue("needs -e boostHost", sshHost != null)
        val label = "release agent key"
        val generated = SshKeyGenerator.generate(
            IdentityKeyGenerationSpec(algorithm = IdentityKeyAlgorithm.ED25519, rsaBits = 2048, comment = label)
        )
        AppStateSeeder.seedIdentity(
            identity = Identity(
                id = UUID.randomUUID().toString(), label = label, fingerprint = generated.fingerprint,
                createdEpochMillis = System.currentTimeMillis(), hasPrivateKey = true
            ),
            privateKey = generated.privateKey,
            publicKey = generated.publicKey
        )
        val key = generated.publicKey.trim().split(' ').take(2)
        val marker = "sshpeaches-release-agent-${UUID.randomUUID().toString().take(8)}"
        val host = HostConnection(
            id = "release-agent", name = "release-agent", host = sshHost!!, port = sshPort, username = sshUser,
            preferredAuth = AuthMethod.PASSWORD, forwardAgent = true
        )
        withSession(host) { service, id, log ->
            check(log.any { it.startsWith("Agent forwarding is on") }) { "$log" }
            try {
                type(service, id, "ssh-add -L; echo LISTED-\$((40+2))")
                waitForOutput(service, id, "LISTED-42")
                check(output(service, id).contains(key[1])) { "ssh-add -L doesn't list the key: ${output(service, id)}" }
                type(service, id, "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys")
                // Only this key is let in, so getting in means the phone signed with it.
                type(service, id, "echo '${key.joinToString(" ")} $marker' >> ~/.ssh/authorized_keys")
                type(
                    service, id,
                    "ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null " +
                        "-o PreferredAuthentications=publickey -o LogLevel=ERROR -p $sshPort $sshUser@127.0.0.1 " +
                        "'echo IN-\$((40+2))'"
                )
                waitForOutput(service, id, "IN-42")
                waitFor("the session log to name the key") { log.any { it.contains("with your key \"$label\"") } }
            } finally {
                type(service, id, "sed -i '/$marker/d' ~/.ssh/authorized_keys")
                Thread.sleep(500)
            }
        }
    }

    private fun type(service: SessionService, id: String, command: String) {
        service.sendShellInput(id, "$command\n")
        Thread.sleep(300)
    }

    private fun output(service: SessionService, id: String): String =
        service.shellOutputFlow().value[id].orEmpty().filterNot { it == '\r' || it == '\n' }

    private fun waitForOutput(service: SessionService, id: String, expected: String) {
        waitFor("$expected in the terminal") { output(service, id).contains(expected) }
    }

    private fun withSession(host: HostConnection, block: (SessionService, String, List<String>) -> Unit) {
        val id = host.id
        val log = CopyOnWriteArrayList<String>()
        val logScope = CoroutineScope(Dispatchers.Default)
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) log += it.message } }
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
            logScope.cancel()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 90_000
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
