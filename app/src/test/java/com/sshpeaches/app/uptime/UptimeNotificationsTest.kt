package com.majordaftapps.sshpeaches.app.uptime

import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UptimeNotificationsTest {

    @Test
    fun shouldNotify_onlyForVerifiedTransitions() {
        assertFalse(UptimeNotifications.shouldNotify(null, UptimeStatus.DOWN))
        assertFalse(UptimeNotifications.shouldNotify(UptimeStatus.UP, UptimeStatus.UP))
        assertFalse(UptimeNotifications.shouldNotify(UptimeStatus.UNVERIFIED, UptimeStatus.DOWN))
        assertFalse(UptimeNotifications.shouldNotify(UptimeStatus.DOWN, UptimeStatus.UNVERIFIED))
        assertTrue(UptimeNotifications.shouldNotify(UptimeStatus.UP, UptimeStatus.DOWN))
        assertTrue(UptimeNotifications.shouldNotify(UptimeStatus.DOWN, UptimeStatus.UP))
    }
}
