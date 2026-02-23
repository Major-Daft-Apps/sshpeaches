package com.sshpeaches.app.data.model

data class HostConnection(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val preferredAuth: AuthMethod,
    val group: String? = null,
    val lastUsedEpochMillis: Long? = null,
    val favorite: Boolean = false,
    val osMetadata: OsMetadata = OsMetadata.Undetected,
    val notes: String = "",
    val defaultMode: ConnectionMode = ConnectionMode.SSH,
    val attachedForwards: List<String> = emptyList(),
    val snippets: List<String> = emptyList(),
    val hasPassword: Boolean = false,
    val useMosh: Boolean = false,
    val preferredForwardId: String? = null,
    val startupScript: String = "",
    val backgroundBehavior: BackgroundBehavior = BackgroundBehavior.INHERIT
)

data class Identity(
    val id: String,
    val label: String,
    val fingerprint: String,
    val username: String? = null,
    val createdEpochMillis: Long,
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
    val type: PortForwardType,
    val sourceHost: String = "127.0.0.1",
    val sourcePort: Int,
    val destinationHost: String = "",
    val destinationPort: Int = 0,
    val associatedHosts: List<String> = emptyList(),
    val favorite: Boolean = false,
    val enabled: Boolean = false
)

data class Snippet(
    val id: String,
    val title: String,
    val description: String = "",
    val command: String,
    val tags: List<String> = emptyList(),
    val autoRunHostIds: List<String> = emptyList(),
    val requireConfirmation: Boolean = true,
    val favorite: Boolean = false
)

enum class AuthMethod { PASSWORD, IDENTITY, PASSWORD_AND_IDENTITY }

enum class ConnectionMode { SSH, SFTP, SCP }

enum class PortForwardType { LOCAL, REMOTE, DYNAMIC }

enum class BackgroundBehavior { INHERIT, ALWAYS_ALLOW, ALWAYS_STOP }

sealed class OsMetadata {
    data object Undetected : OsMetadata()
    data class Known(val family: OsFamily, val versionLabel: String? = null) : OsMetadata()
    data class Custom(val label: String) : OsMetadata()
}

enum class OsFamily(val displayName: String, val colorHex: String) {
    UBUNTU("Ubuntu", "#E95420"),
    DEBIAN("Debian", "#A81D33"),
    FEDORA("Fedora", "#294172"),
    CENTOS("CentOS", "#9C1A8C"),
    SUSE("SUSE", "#73BA25"),
    MINT("Linux Mint", "#87CF3E"),
    ARCH("Arch Linux", "#1793D1"),
    REDHAT("Red Hat", "#EE0000"),
    GENTOO("Gentoo", "#54487A"),
    POP_OS("Pop!_OS", "#48B9C7"),
    MANJARO("Manjaro", "#35BF5C"),
    ELEMENTARY("elementary OS", "#5DA3E7"),
    PEPPERMINT("Peppermint", "#D7443E"),
    LITE("Linux Lite", "#8D8E91"),
    ZORIN("Zorin", "#0CC1F3"),
    ROCKY("Rocky Linux", "#0CB177"),
    ALMA("AlmaLinux", "#15A1E2"),
    ASAHI("Asahi", "#ED6A5A"),
    NIXOS("NixOS", "#5277C3"),
    MAC("macOS", "#A3AAAE"),
    WINDOWS("Windows", "#0078D4"),
    BSD("BSD", "#AB2B28"),
    GENERIC("Linux", "#000000"),
    UNKNOWN("Unknown", "#888888")
}
