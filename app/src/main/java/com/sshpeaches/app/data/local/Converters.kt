package com.sshpeaches.app.data.local

import androidx.room.TypeConverter
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.OsFamily
import com.sshpeaches.app.data.model.OsMetadata
import com.sshpeaches.app.data.model.PortForwardType

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
    fun fromPortType(value: PortForwardType?): String = value?.name ?: PortForwardType.LOCAL.name

    @TypeConverter
    @JvmStatic
    fun toPortType(value: String?): PortForwardType =
        runCatching { PortForwardType.valueOf(value ?: PortForwardType.LOCAL.name) }.getOrDefault(PortForwardType.LOCAL)

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
