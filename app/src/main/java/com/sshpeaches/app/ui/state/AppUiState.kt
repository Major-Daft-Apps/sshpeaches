package com.sshpeaches.app.ui.state

import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import com.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults

data class AppUiState(
    val favorites: FavoritesSection = FavoritesSection(),
    val hosts: List<HostConnection> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val portForwards: List<PortForward> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val sortMode: SortMode = SortMode.LAST_USED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val allowBackgroundSessions: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.FIVE_MIN,
    val customLockTimeoutMinutes: Int = 30,
    val crashReportsEnabled: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val diagnosticsLoggingEnabled: Boolean = false,
    val includeIdentitiesInQr: Boolean = true,
    val includeSettingsInQr: Boolean = true,
    val autoStartForwards: Boolean = true,
    val hostKeyPromptEnabled: Boolean = true,
    val autoTrustHostKey: Boolean = true,
    val usageReportsEnabled: Boolean = false,
    val pinConfigured: Boolean = false,
    val isLocked: Boolean = false,
    val keyboardSlots: List<String> = KeyboardLayoutDefaults.DEFAULT_SLOTS
)

data class FavoritesSection(
    val hostFavorites: List<HostConnection> = emptyList(),
    val identityFavorites: List<Identity> = emptyList(),
    val portFavorites: List<PortForward> = emptyList()
)

enum class SortMode { LAST_USED, ALPHABETICAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LockTimeout(val label: String) {
    IMMEDIATE("Immediate"),
    ONE_MIN("1 minute"),
    FIVE_MIN("5 minutes"),
    FIFTEEN_MIN("15 minutes"),
    CUSTOM("Custom")
}
