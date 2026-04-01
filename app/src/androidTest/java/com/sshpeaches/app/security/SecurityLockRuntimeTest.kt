package com.majordaftapps.sshpeaches.app.security

import android.Manifest
import androidx.compose.runtime.MutableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class SecurityLockRuntimeTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lockAppClearsRuntimeSessionPasswordsInBoundService() {
        AppStateSeeder.configurePin(pin = "2468", locked = false)
        composeRule.activityRule.scenario.recreate()

        val service = waitForSessionService()
        val runtimePasswordsField = SessionService::class.java.getDeclaredField("runtimeSessionPasswords").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val runtimePasswords = runtimePasswordsField.get(service) as ConcurrentHashMap<String, String>
        runtimePasswords["runtime-lock-test"] = "transient-secret"
        assertEquals("transient-secret", service.getRuntimeSessionPassword("runtime-lock-test"))

        val viewModelGetter = MainActivity::class.java.getDeclaredMethod("getAppViewModel").apply {
            isAccessible = true
        }
        val viewModel = viewModelGetter.invoke(composeRule.activity) as com.majordaftapps.sshpeaches.app.ui.state.AppViewModel
        composeRule.runOnUiThread {
            viewModel.lockApp()
        }

        composeRule.waitUntil(5_000) {
            service.getRuntimeSessionPassword("runtime-lock-test") == null && SecurityManager.isLocked()
        }

        assertNull(service.getRuntimeSessionPassword("runtime-lock-test"))
    }

    private fun waitForSessionService(): SessionService {
        val stateField = MainActivity::class.java.getDeclaredField("sessionServiceState").apply {
            isAccessible = true
        }
        var service: SessionService? = null
        composeRule.waitUntil(10_000) {
            @Suppress("UNCHECKED_CAST")
            val state = stateField.get(composeRule.activity) as MutableState<SessionService?>
            service = state.value
            service != null
        }
        return requireNotNull(service)
    }
}
