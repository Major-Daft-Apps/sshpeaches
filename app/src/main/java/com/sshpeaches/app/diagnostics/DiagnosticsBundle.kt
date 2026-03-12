package com.majordaftapps.sshpeaches.app.diagnostics

import android.os.Build
import com.majordaftapps.sshpeaches.app.BuildConfig
import org.json.JSONObject
import java.util.UUID

data class DiagnosticsBundle(
    val id: String,
    val generatedAt: Long,
    val app: AppInfo,
    val device: DeviceInfo,
    val settings: SettingsSnapshot,
    val dataCounts: DataCounts
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("generatedAt", generatedAt)
        put("app", app.toJson())
        put("device", device.toJson())
        put("settings", settings.toJson())
        put("dataCounts", dataCounts.toJson())
    }

    data class AppInfo(
        val versionName: String,
        val versionCode: Int,
        val buildType: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("buildType", buildType)
        }
    }

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val sdkInt: Int,
        val release: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("manufacturer", manufacturer)
            put("model", model)
            put("sdkInt", sdkInt)
            put("release", release)
        }
    }

    data class SettingsSnapshot(
        val analyticsEnabled: Boolean,
        val crashReportsEnabled: Boolean,
        val diagnosticsEnabled: Boolean,
        val usageReportsEnabled: Boolean,
        val allowBackgroundSessions: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("analyticsEnabled", analyticsEnabled)
            put("crashReportsEnabled", crashReportsEnabled)
            put("diagnosticsEnabled", diagnosticsEnabled)
            put("usageReportsEnabled", usageReportsEnabled)
            put("allowBackgroundSessions", allowBackgroundSessions)
        }
    }

    data class DataCounts(
        val hosts: Int,
        val identities: Int,
        val portForwards: Int,
        val snippets: Int
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("hosts", hosts)
            put("identities", identities)
            put("portForwards", portForwards)
            put("snippets", snippets)
        }
    }

    companion object {
        fun basic(
            settings: SettingsSnapshot,
            counts: DataCounts
        ): DiagnosticsBundle = DiagnosticsBundle(
            id = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
            app = AppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE
            ),
            device = DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE ?: "unknown"
            ),
            settings = settings,
            dataCounts = counts
        )
    }
}
