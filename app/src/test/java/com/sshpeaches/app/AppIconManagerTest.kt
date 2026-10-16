package com.majordaftapps.sshpeaches.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.ui.screens.appIconVariants
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppIconManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun theRetired3dIconMovesToOrangeOnCreamAndTheOthersStay() {
        assertEquals(AppIconOption.ORANGE_ON_CREAM, AppIconOption.fromStoredValue("DEFAULT"))
        assertEquals(AppIconOption.ORANGE_ON_CREAM, AppIconOption.fromStoredValue(null))
        assertEquals(AppIconOption.PEACH_LIGHT, AppIconOption.fromStoredValue("PEACH_LIGHT"))
        assertEquals(AppIconOption.PEACH_DARK, AppIconOption.fromStoredValue("PEACH_DARK"))
    }

    @Test
    fun everyIconHasItsOwnLauncherAliasAndOnlyTheDefaultStartsEnabled() {
        val icons = AppIconOption.entries.map { option ->
            val info = context.packageManager.getActivityInfo(
                ComponentName(context, AppIconManager.aliasName(option)),
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            assertEquals(option.name, option == AppIconOption.ORANGE_ON_CREAM, info.enabled)
            context.resources.getResourceEntryName(info.icon)
        }
        assertEquals(
            listOf(
                "ic_launcher_peach_orange_on_cream", "ic_launcher_peach_light", "ic_launcher_peach_dark",
                "ic_launcher_peach_white_on_orange", "ic_launcher_peach_coral_on_blush",
                "ic_launcher_peach_peach_on_plum", "ic_launcher_peach_black_on_peach",
                "ic_launcher_peach_mint_on_charcoal", "ic_launcher_peach_orange_on_black",
                "ic_launcher_peach_terminal_green"
            ),
            icons
        )
    }

    @Test
    fun applyEnablesOnlyTheChosenAlias() {
        AppIconOption.entries.forEach { chosen ->
            AppIconManager.apply(context, chosen)
            AppIconOption.entries.forEach { option ->
                val state = context.packageManager.getComponentEnabledSetting(
                    ComponentName(context, AppIconManager.aliasName(option))
                )
                val expected = if (option == chosen) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                assertEquals("$chosen: $option", expected, state)
            }
        }
    }

    @Test
    fun theGridShowsEveryIconOnceInOrder() {
        assertEquals(AppIconOption.entries, appIconVariants.map { it.option })
        assertEquals(
            listOf(
                "Orange on cream", "Orange Peach", "White Peach", "White on orange", "Coral on blush",
                "Peach on plum", "Black on peach", "Mint on charcoal", "Orange on black", "Terminal green"
            ),
            appIconVariants.map { it.name }
        )
    }
}
