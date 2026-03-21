package com.majordaftapps.sshpeaches.app.data.local

import androidx.room.TypeConverter
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.UnverifiedReason
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
import com.majordaftapps.sshpeaches.app.data.model.UptimeStatus

/**
 * Centralized Room converters for enums and small aggregates.
 * Lists are stored as comma-separated strings; IDs are UUIDs so commas are safe.
 */
object Converters {
    @TypeConverter
    @JvmStatic
    fun fromStringList(list: List<String>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else value.split(",")

    @TypeConverter
    @JvmStatic
    fun fromAuthMethod(value: AuthMethod?): String = value?.name ?: AuthMethod.PASSWORD.name

    @TypeConverter
    @JvmStatic
    fun toAuthMethod(value: String?): AuthMethod =
        runCatching { AuthMethod.valueOf(value ?: AuthMethod.PASSWORD.name) }.getOrDefault(AuthMethod.PASSWORD)

    @TypeConverter
    @JvmStatic
    fun fromMode(value: ConnectionMode?): String = value?.name ?: ConnectionMode.SSH.name

    @TypeConverter
    @JvmStatic
    fun toMode(value: String?): ConnectionMode =
        runCatching { ConnectionMode.valueOf(value ?: ConnectionMode.SSH.name) }.getOrDefault(ConnectionMode.SSH)

    @TypeConverter
    @JvmStatic
    fun fromBackgroundBehavior(value: BackgroundBehavior?): String =
        value?.name ?: BackgroundBehavior.INHERIT.name

    @TypeConverter
    @JvmStatic
    fun toBackgroundBehavior(value: String?): BackgroundBehavior =
        runCatching { BackgroundBehavior.valueOf(value ?: BackgroundBehavior.INHERIT.name) }
            .getOrDefault(BackgroundBehavior.INHERIT)

    @TypeConverter
    @JvmStatic
    fun fromPortType(value: PortForwardType?): String = value?.name ?: PortForwardType.LOCAL.name

    @TypeConverter
    @JvmStatic
    fun toPortType(value: String?): PortForwardType =
        runCatching { PortForwardType.valueOf(value ?: PortForwardType.LOCAL.name) }.getOrDefault(PortForwardType.LOCAL)

    @TypeConverter
    @JvmStatic
    fun fromUptimeCheckMethod(value: UptimeCheckMethod?): String =
        value?.name ?: UptimeCheckMethod.TCP.name

    @TypeConverter
    @JvmStatic
    fun toUptimeCheckMethod(value: String?): UptimeCheckMethod =
        runCatching { UptimeCheckMethod.valueOf(value ?: UptimeCheckMethod.TCP.name) }
            .getOrDefault(UptimeCheckMethod.TCP)

    @TypeConverter
    @JvmStatic
    fun fromUptimeStatus(value: UptimeStatus?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toUptimeStatus(value: String?): UptimeStatus? =
        value?.let { runCatching { UptimeStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    @JvmStatic
    fun fromUnverifiedReason(value: UnverifiedReason?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toUnverifiedReason(value: String?): UnverifiedReason? =
        value?.let { runCatching { UnverifiedReason.valueOf(it) }.getOrNull() }

    @TypeConverter
    @JvmStatic
    fun fromOsMetadata(meta: OsMetadata?): String = when (meta) {
        null, OsMetadata.Undetected -> "Undetected"
        is OsMetadata.Known -> "Known:${meta.family.name}:${meta.versionLabel.orEmpty()}"
        is OsMetadata.Custom -> "Custom:${meta.label}"
    }

    @TypeConverter
    @JvmStatic
    fun toOsMetadata(value: String?): OsMetadata {
        val raw = value ?: return OsMetadata.Undetected
        return when {
            raw == "Undetected" -> OsMetadata.Undetected
            raw.startsWith("Known:") -> {
                val parts = raw.split(":")
                val family = parts.getOrNull(1)?.let { runCatching { OsFamily.valueOf(it) }.getOrNull() }
                val ver = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (family != null) OsMetadata.Known(family, ver) else OsMetadata.Undetected
            }
            raw.startsWith("Custom:") -> OsMetadata.Custom(raw.removePrefix("Custom:"))
            else -> OsMetadata.Undetected
        }
    }
}
