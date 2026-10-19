package com.majordaftapps.sshpeaches.app.security

/**
 * A PIN lockout measured on the monotonic clock (elapsedRealtime), so moving the wall clock forward can't end it.
 * The monotonic clock restarts at boot, so a lockout seen after a reboot runs its full duration again.
 */
internal data class PinLockout(
    val untilElapsedMs: Long,
    val armedAtElapsedMs: Long,
    /** Settings.Global.BOOT_COUNT when armed, or -1 when it couldn't be read. */
    val bootCount: Int,
    val durationMs: Long
) {
    companion object {
        fun arm(nowElapsedMs: Long, bootCount: Int, durationMs: Long) = PinLockout(
            untilElapsedMs = nowElapsedMs + durationMs,
            armedAtElapsedMs = nowElapsedMs,
            bootCount = bootCount,
            durationMs = durationMs
        )
    }
}

internal data class PinLockoutCheck(val remainingMs: Long, val rearmed: PinLockout?)

/** Time left on [lockout] now; [PinLockoutCheck.rearmed] is set when a reboot restarted it and must be saved. */
internal fun checkPinLockout(lockout: PinLockout?, nowElapsedMs: Long, bootCount: Int): PinLockoutCheck {
    if (lockout == null) return PinLockoutCheck(0L, null)
    val rebooted = nowElapsedMs < lockout.armedAtElapsedMs ||
        (bootCount >= 0 && lockout.bootCount >= 0 && bootCount != lockout.bootCount)
    if (rebooted) {
        val rearmed = PinLockout.arm(nowElapsedMs, bootCount, lockout.durationMs)
        return PinLockoutCheck(lockout.durationMs, rearmed)
    }
    return PinLockoutCheck((lockout.untilElapsedMs - nowElapsedMs).coerceIn(0L, lockout.durationMs), null)
}
