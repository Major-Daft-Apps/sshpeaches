package com.majordaftapps.sshpeaches.app.navigation

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.launchMainActivityThroughFramework
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInfoNavigationTest {

    @get:Rule(order = 0)
    val appStateResetRule = AppStateResetRule()

    @get:Rule(order = 1)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun startupRoute_canOpenLicenseNoticesScreen() {
        launchMainActivityThroughFramework(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(
                    "com.majordaftapps.sshpeaches.debug",
                    MainActivity::class.java.name
                )
                putExtra(MainActivity.EXTRA_START_ROUTE, Routes.OPEN_SOURCE_LICENSES)
            }
        )

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(
            device.wait(Until.hasObject(By.text("Open Source License Notices")), 5_000)
        ) {
            "Open Source License Notices screen did not appear."
        }
    }
}
