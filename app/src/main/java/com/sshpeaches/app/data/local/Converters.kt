package com.majordaftapps.sshpeaches.app.data.local

import androidx.room.TypeConverter
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.json.JSONArray

/**
 * Centralized Room converters for enums and small aggregates.
 * Lists are stored as comma-separated strings. A list with a comma inside an item (a tag or an info command such as
 * `ps -eo pid,comm`, from an import) is stored as a JSON array after [LIST_JSON_PREFIX] instead, so it reads back
 * whole; both forms are read, so existing rows need no migration.
 */
object Converters {
    private const val LIST_JSON_PREFIX = "\u0002"

    @TypeConverter
    @JvmStatic
    fun fromStringList(list: List<String>?): String {
        if (list == null) return ""
        val commaSafe = list.none { it.contains(',') } && list.firstOrNull()?.startsWith(LIST_JSON_PREFIX) != true
        return if (commaSafe) list.joinToString(",") else LIST_JSON_PREFIX + JSONArray(list).toString()
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String> = when {
        value.isNullOrBlank() -> emptyList()
        value.startsWith(LIST_JSON_PREFIX) -> runCatching {
            val array = JSONArray(value.substring(LIST_JSON_PREFIX.length))
            List(array.length()) { index -> array.getString(index) }
        }.getOrElse { value.substring(LIST_JSON_PREFIX.length).split(",") }
        else -> value.split(",")
    }

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
                val parts = raw.split(":", limit = 3)
                val family = parts.getOrNull(1)?.let { runCatching { OsFamily.valueOf(it) }.getOrNull() }
                val ver = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (family != null) OsMetadata.Known(family, ver) else OsMetadata.Undetected
            }
            raw.startsWith("Custom:") -> OsMetadata.Custom(raw.removePrefix("Custom:"))
            else -> OsMetadata.Undetected
        }
    }
}
