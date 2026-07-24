package com.majordaftapps.sshpeaches.app.ui

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FirstRunWelcomeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearWelcomePreferences()
    }

    @After
    fun tearDown() {
        clearWelcomePreferences()
    }

    @Test
    fun welcomeIsOnlyShownUntilItHasBeenCompleted() {
        assertTrue(FirstRunWelcomePreferences.shouldShow(context))

        FirstRunWelcomePreferences.markCompleted(context)

        assertFalse(FirstRunWelcomePreferences.shouldShow(context))
    }

    @Test
    fun welcomeTakesPriorityOverPermissionRemediation() {
        assertEquals(
            StartupOverlay.FIRST_RUN_WELCOME,
            startupOverlay(
                showFirstRunWelcome = true,
                hasMissingCorePermissions = true
            )
        )
        assertEquals(
            StartupOverlay.PERMISSIONS,
            startupOverlay(
                showFirstRunWelcome = false,
                hasMissingCorePermissions = true
            )
        )
        assertEquals(
            StartupOverlay.NONE,
            startupOverlay(
                showFirstRunWelcome = false,
                hasMissingCorePermissions = false
            )
        )
    }

    private fun clearWelcomePreferences() {
        context.getSharedPreferences("first_run_experience", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
