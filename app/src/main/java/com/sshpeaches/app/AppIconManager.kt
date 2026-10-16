package com.majordaftapps.sshpeaches.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption

object AppIconManager {
    fun aliasName(option: AppIconOption): String = "com.majordaftapps.sshpeaches.app." + when (option) {
        // The 3D icon's alias, kept so launchers swap its icon in place instead of dropping it.
        AppIconOption.ORANGE_ON_CREAM -> "DefaultLauncherAlias"
        AppIconOption.PEACH_LIGHT -> "PeachLightLauncherAlias"
        AppIconOption.PEACH_DARK -> "PeachDarkLauncherAlias"
        AppIconOption.WHITE_ON_ORANGE -> "WhiteOnOrangeLauncherAlias"
        AppIconOption.CORAL_ON_BLUSH -> "CoralOnBlushLauncherAlias"
        AppIconOption.PEACH_ON_PLUM -> "PeachOnPlumLauncherAlias"
        AppIconOption.BLACK_ON_PEACH -> "BlackOnPeachLauncherAlias"
        AppIconOption.MINT_ON_CHARCOAL -> "MintOnCharcoalLauncherAlias"
        AppIconOption.ORANGE_ON_BLACK -> "OrangeOnBlackLauncherAlias"
        AppIconOption.TERMINAL_GREEN -> "TerminalGreenLauncherAlias"
    }

    fun apply(context: Context, option: AppIconOption) {
        val packageManager = context.packageManager
        // The chosen alias first, so the app always has a launcher entry.
        val options = listOf(option) + AppIconOption.entries.filter { it != option }
        options.forEach { candidate ->
            val component = ComponentName(context, aliasName(candidate))
            val desiredState = if (candidate == option) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (packageManager.getComponentEnabledSetting(component) != desiredState) {
                packageManager.setComponentEnabledSetting(
                    component,
                    desiredState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
