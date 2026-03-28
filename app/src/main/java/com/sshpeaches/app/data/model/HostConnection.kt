package com.majordaftapps.sshpeaches.app.data.model

data class HostConnection(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val preferredAuth: AuthMethod,
    val group: String? = null,
    val createdEpochMillis: Long? = null,
    val updatedEpochMillis: Long? = null,
    val lastUsedEpochMillis: Long? = null,
    val favorite: Boolean = false,
    val osMetadata: OsMetadata = OsMetadata.Undetected,
    val notes: String = "",
    val defaultMode: ConnectionMode = ConnectionMode.SSH,
    val attachedForwards: List<String> = emptyList(),
    val snippets: List<String> = emptyList(),
    val hasPassword: Boolean = false,
    val useMosh: Boolean = false,
    val preferredIdentityId: String? = null,
    val preferredForwardId: String? = null,
    val startupScript: String = "",
    val backgroundBehavior: BackgroundBehavior = BackgroundBehavior.INHERIT,
    val terminalProfileId: String? = null,
    val infoCommands: List<String> = emptyList()
)

data class Identity(
    val id: String,
    val label: String,
    val fingerprint: String,
    val username: String? = null,
    val group: String? = null,
    val createdEpochMillis: Long,
    val updatedEpochMillis: Long? = null,
    val lastUsedEpochMillis: Long? = null,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val hasPrivateKey: Boolean = false,
    val keyImportEpochMillis: Long? = null
)

data class PortForward(
    val id: String,
    val label: String,
    val group: String? = null,
    val type: PortForwardType,
    val sourceHost: String = "127.0.0.1",
    val sourcePort: Int,
    val destinationHost: String = "",
    val destinationPort: Int = 0,
    val associatedHosts: List<String> = emptyList(),
    val favorite: Boolean = false,
    val enabled: Boolean = false,
    val createdEpochMillis: Long? = null,
    val updatedEpochMillis: Long? = null,
    val lastUsedEpochMillis: Long? = null
)

data class Snippet(
    val id: String,
    val title: String,
    val group: String? = null,
    val description: String = "",
    val command: String,
    val tags: List<String> = emptyList(),
    val autoRunHostIds: List<String> = emptyList(),
    val requireConfirmation: Boolean = true,
    val favorite: Boolean = false,
    val createdEpochMillis: Long? = null,
    val updatedEpochMillis: Long? = null,
    val lastUsedEpochMillis: Long? = null
)

enum class AuthMethod { PASSWORD, IDENTITY, PASSWORD_AND_IDENTITY }

enum class ConnectionMode { SSH, SFTP, SCP }

enum class TerminalEmulation(val ptyName: String, val label: String) {
    XTERM("xterm", "xterm"),
    VT100("vt100", "vt100")
}

enum class PortForwardType { LOCAL }

enum class BackgroundBehavior { INHERIT, ALWAYS_ALLOW, ALWAYS_STOP }

sealed class OsMetadata {
    data object Undetected : OsMetadata()
    data class Known(val family: OsFamily, val versionLabel: String? = null) : OsMetadata()
    data class Custom(val label: String) : OsMetadata()
}

enum class OsFamily(val displayName: String, val colorHex: String) {
    UBUNTU("Ubuntu", "#E95420"),
    DEBIAN("Debian", "#A80030"),
    FEDORA("Fedora", "#51A2DA"),
    SUSE("SUSE", "#62B92B"),
    MINT("Linux Mint", "#69B53F"),
    ARCH("Arch Linux", "#1793D1"),
    REDHAT("Red Hat", "#EE0000"),
    MAC("macOS", "#1D1D1F"),
    WINDOWS("Windows", "#0078D4"),
    BSD("BSD", "#AB2B28"),
    GENERIC("Linux", "#111111"),
    UNKNOWN("Unknown", "#888888")
}
