package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SshRouteTest {
    private fun host(id: String, jump: String? = null) = HostConnection(
        id = id, name = "name-$id", host = "$id.example", username = "u", preferredAuth = AuthMethod.PASSWORD, jumpHostId = jump
    )

    private fun finder(vararg hosts: HostConnection): (String) -> HostConnection? = { id -> hosts.firstOrNull { it.id == id } }

    @Test
    fun aHostWithoutAJumpHostConnectsDirectly() {
        assertEquals(emptyList<HostConnection>(), SshRoute.jumpsFor(host("a"), finder()))
        assertEquals(emptyList<HostConnection>(), SshRoute.jumpsFor(host("a", jump = " "), finder()))
    }

    @Test
    fun aChainIsReturnedFirstHopFirst() {
        val outer = host("outer")
        val inner = host("inner", jump = "outer")
        val target = host("target", jump = "inner")
        assertEquals(listOf("outer", "inner"), SshRoute.jumpsFor(target, finder(outer, inner)).map { it.id })
    }

    @Test
    fun aLoopIsRefused() {
        val a = host("a", jump = "b")
        val b = host("b", jump = "a")
        try {
            SshRoute.jumpsFor(a, finder(a, b))
            fail("A loop was accepted")
        } catch (expected: SshRouteException) {
            assertTrue(expected.message!!.contains("lead back"))
        }
        val self = host("s", jump = "s")
        assertTrue(runCatching { SshRoute.jumpsFor(self, finder(self)) }.exceptionOrNull() is SshRouteException)
    }

    @Test
    fun aJumpHostThatIsGoneIsNamed() {
        val error = runCatching { SshRoute.jumpsFor(host("a", jump = "gone"), finder()) }.exceptionOrNull()
        assertTrue(error is SshRouteException)
        assertTrue(error!!.message!!.contains("no longer saved"))
    }

    @Test
    fun aChainHasALimit() {
        val hosts = (0..SshRoute.MAX_JUMPS + 1).map { host("h$it", jump = "h${it + 1}") } + host("h${SshRoute.MAX_JUMPS + 2}")
        val error = runCatching { SshRoute.jumpsFor(hosts.first(), finder(*hosts.toTypedArray())) }.exceptionOrNull()
        assertTrue(error is SshRouteException)
        val fits = (0 until SshRoute.MAX_JUMPS).map { host("f$it", jump = "f${it + 1}") } + host("f${SshRoute.MAX_JUMPS}")
        assertEquals(SshRoute.MAX_JUMPS, SshRoute.jumpsFor(fits.first(), finder(*fits.toTypedArray())).size)
    }

    @Test
    fun theEditorOffersOnlyHostsThatMakeNoLoop() {
        val bastion = host("bastion")
        val middle = host("middle", jump = "bastion")
        val target = host("target", jump = "middle")
        val find = finder(bastion, middle, target)
        // Editing the bastion: itself and the hosts that already go through it are out.
        assertTrue(SshRoute.wouldLoop(bastion, bastion, find))
        assertTrue(SshRoute.wouldLoop(bastion, middle, find))
        assertTrue(SshRoute.wouldLoop(bastion, target, find))
        // Editing the target: anything upstream is fine.
        assertFalse(SshRoute.wouldLoop(target, bastion, find))
        assertFalse(SshRoute.wouldLoop(target, middle, find))
    }
}
