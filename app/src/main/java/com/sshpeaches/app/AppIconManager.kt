package com.majordaftapps.sshpeaches.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption

object AppIconManager {
    private const val DEFAULT_ALIAS = "com.majordaftapps.sshpeaches.app.DefaultLauncherAlias"
    private const val PEACH_LIGHT_ALIAS = "com.majordaftapps.sshpeaches.app.PeachLightLauncherAlias"
    private const val PEACH_DARK_ALIAS = "com.majordaftapps.sshpeaches.app.PeachDarkLauncherAlias"

    fun apply(context: Context, option: AppIconOption) {
        val packageManager = context.packageManager
        val enabledAlias = when (option) {
            AppIconOption.DEFAULT -> DEFAULT_ALIAS
            AppIconOption.PEACH_LIGHT -> PEACH_LIGHT_ALIAS
            AppIconOption.PEACH_DARK -> PEACH_DARK_ALIAS
        }
        listOf(DEFAULT_ALIAS, PEACH_LIGHT_ALIAS, PEACH_DARK_ALIAS).forEach { alias ->
            val component = ComponentName(context, alias)
            val desiredState = if (alias == enabledAlias) {
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
