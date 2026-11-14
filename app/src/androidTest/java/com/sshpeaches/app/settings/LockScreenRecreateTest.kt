package com.majordaftapps.sshpeaches.app.settings

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.NotificationPermissionHelper
import com.majordaftapps.sshpeaches.app.ui.state.AppViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The lock screen after the activity is recreated while locked, driven through the real windows (UiAutomator), not
 * the Compose test rule. Under the rule this flow failed about one run in three on a busy emulator although the app
 * was right: after `ActivityScenario.recreate()` either the tap on Unlock never reached the app, or the PIN was
 * accepted and the rule's composition kept the lock dialog. The same steps through the real windows passed 36 of 36.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenRecreateTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule = NotificationPermissionHelper.grantRule()

    @Test
    fun lockedAppShowsTheLockScreenAndUnlocksWithThePin() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AppStateSeeder.configurePin(pin = "2468", locked = true)
            scenario.recreate()

            checkNotNull(device.wait(Until.findObject(By.text("Unlock with PIN")), 20_000)) {
                "The lock screen didn't appear."
            }
            checkNotNull(device.findObject(By.clazz("android.widget.EditText"))) { "No PIN field." }.text = "2468"
            device.findObject(By.text("Unlock with PIN")).click()

            if (device.wait(Until.gone(By.textStartsWith("Unlock")), 30_000) != true) {
                var viewModelLocked: Boolean? = null
                scenario.onActivity {
                    viewModelLocked = ViewModelProvider(it)[AppViewModel::class.java].uiState.value.isLocked
                }
                throw AssertionError(
                    "Still showing the lock screen: viewModel.isLocked=$viewModelLocked, " +
                        "SecurityManager.isLocked=${SecurityManager.isLocked()}"
                )
            }
        }
    }
}
