package com.majordaftapps.sshpeaches.app.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardLayoutMigrationTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val swipeNav = KeyboardLayoutDefaults.textAction(label = "Swipe Nav", text = "").copy(iconId = "swipe_nav")

    @Before
    fun initStore() {
        SettingsStore.init(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    }

    @Test
    fun layoutSavedBeforeVersioningSwapsSwipeNavForFn() = runBlocking {
        val previousDefault = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.fnKeyAction()
            this[6] = swipeNav
        }
        SettingsStore.setKeyboardLayoutWithoutVersionForTesting(previousDefault)

        assertEquals(KeyboardLayoutDefaults.DEFAULT_SLOTS, SettingsStore.keyboardLayout.first())
    }

    @Test
    fun swipeNavAddedBackInTheEditorIsKept() = runBlocking {
        val chosen = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply { this[13] = swipeNav }
        SettingsStore.setKeyboardLayout(chosen)

        assertEquals(chosen, SettingsStore.keyboardLayout.first())
    }
}
