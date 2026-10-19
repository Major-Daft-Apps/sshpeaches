package com.majordaftapps.sshpeaches.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PinLockoutTest {

    private val lockout = PinLockout.arm(nowElapsedMs = 100_000L, bootCount = 7, durationMs = 60_000L)

    @Test
    fun countsDownOnTheMonotonicClock() {
        assertEquals(60_000L, checkPinLockout(lockout, 100_000L, 7).remainingMs)
        assertEquals(15_000L, checkPinLockout(lockout, 145_000L, 7).remainingMs)
        assertEquals(0L, checkPinLockout(lockout, 200_000L, 7).remainingMs)
        assertNull(checkPinLockout(lockout, 145_000L, 7).rearmed)
    }

    @Test
    fun aRebootRestartsTheLockoutInsteadOfEndingIt() {
        // The monotonic clock starts again at boot, so its value can't say how long the lockout has run.
        val byBootCount = checkPinLockout(lockout, 500_000L, 8)
        assertEquals(60_000L, byBootCount.remainingMs)
        assertNotNull(byBootCount.rearmed)
        assertEquals(560_000L, byBootCount.rearmed!!.untilElapsedMs)

        val byClockGoingBack = checkPinLockout(lockout, 5_000L, -1)
        assertEquals(60_000L, byClockGoingBack.remainingMs)
        assertEquals(65_000L, byClockGoingBack.rearmed!!.untilElapsedMs)
    }

    @Test
    fun noLockoutMeansNoWait() {
        assertEquals(0L, checkPinLockout(null, 1L, 1).remainingMs)
    }
}
