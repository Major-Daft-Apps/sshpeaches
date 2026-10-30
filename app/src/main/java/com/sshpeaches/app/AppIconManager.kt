package com.majordaftapps.sshpeaches.app

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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

    /** The icon [option]'s launcher alias shows; the app shows it in recent apps too. */
    fun iconRes(option: AppIconOption): Int = when (option) {
        AppIconOption.ORANGE_ON_CREAM -> R.mipmap.ic_launcher_peach_orange_on_cream
        AppIconOption.PEACH_LIGHT -> R.mipmap.ic_launcher_peach_light
        AppIconOption.PEACH_DARK -> R.mipmap.ic_launcher_peach_dark
        AppIconOption.WHITE_ON_ORANGE -> R.mipmap.ic_launcher_peach_white_on_orange
        AppIconOption.CORAL_ON_BLUSH -> R.mipmap.ic_launcher_peach_coral_on_blush
        AppIconOption.PEACH_ON_PLUM -> R.mipmap.ic_launcher_peach_peach_on_plum
        AppIconOption.BLACK_ON_PEACH -> R.mipmap.ic_launcher_peach_black_on_peach
        AppIconOption.MINT_ON_CHARCOAL -> R.mipmap.ic_launcher_peach_mint_on_charcoal
        AppIconOption.ORANGE_ON_BLACK -> R.mipmap.ic_launcher_peach_orange_on_black
        AppIconOption.TERMINAL_GREEN -> R.mipmap.ic_launcher_peach_terminal_green
    }

    /**
     * Shows [option] for the app's task in recent apps: the task starts at MainActivity (see LauncherActivity), not
     * at an icon's alias, so it would otherwise show the default icon.
     */
    fun applyToTask(activity: Activity, option: AppIconOption) {
        val label = activity.getString(R.string.app_name)
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityManager.TaskDescription.Builder().setLabel(label).setIcon(iconRes(option)).build()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            ActivityManager.TaskDescription(label, iconRes(option))
        } else {
            val icon = ContextCompat.getDrawable(activity, iconRes(option))?.toBitmap() ?: return
            @Suppress("DEPRECATION")
            ActivityManager.TaskDescription(label, icon)
        }
        activity.setTaskDescription(description)
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
