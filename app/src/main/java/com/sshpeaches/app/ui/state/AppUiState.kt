package com.majordaftapps.sshpeaches.app.ui.state

import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.HostUptimeSummary
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction

data class AppUiState(
    val home: HomeSection = HomeSection(),
    val hosts: List<HostConnection> = emptyList(),
    val uptimeSummaries: List<HostUptimeSummary> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val portForwards: List<PortForward> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val sortMode: SortMode = SortMode.LAST_USED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appIcon: AppIconOption = AppIconOption.DEFAULT,
    val allowBackgroundSessions: Boolean = true,
    val backgroundSessionTimeout: BackgroundSessionTimeout = BackgroundSessionTimeout.FOREVER,
    val biometricLockEnabled: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.FIVE_MIN,
    val customLockTimeoutMinutes: Int = 30,
    val terminalEmulation: TerminalEmulation = TerminalEmulation.XTERM,
    val terminalSelectionMode: TerminalSelectionMode = TerminalSelectionMode.NATURAL,
    val terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
    val terminalVolumeButtonsAdjustFontSize: Boolean = false,
    val terminalMarginPx: Int = 0,
    val moshServerCommand: String = DEFAULT_MOSH_SERVER_COMMAND,
    val terminalProfiles: List<TerminalProfile> = TerminalProfileDefaults.builtInProfiles,
    val defaultTerminalProfileId: String = TerminalProfileDefaults.DEFAULT_PROFILE_ID,
    val crashReportsEnabled: Boolean = SettingsStore.defaultCrashReportsEnabled,
    val analyticsEnabled: Boolean = SettingsStore.defaultAnalyticsEnabled,
    val diagnosticsLoggingEnabled: Boolean = SettingsStore.defaultDiagnosticsEnabled,
    val includeSecretsInQr: Boolean = false,
    val autoStartForwards: Boolean = true,
    val hostKeyPromptEnabled: Boolean = true,
    val autoTrustHostKey: Boolean = false,
    val usageReportsEnabled: Boolean = SettingsStore.defaultUsageReportsEnabled,
    val snippetRunTimeoutSeconds: Int = 10,
    val pinConfigured: Boolean = false,
    val isLocked: Boolean = false,
    val keyboardSlots: List<KeyboardSlotAction> = KeyboardLayoutDefaults.DEFAULT_SLOTS
)

data class HomeSection(
    val favorites: FavoritesSection = FavoritesSection(),
    val recents: List<HomeRecentItem> = emptyList()
)

data class FavoritesSection(
    val hostFavorites: List<HostConnection> = emptyList(),
    val identityFavorites: List<Identity> = emptyList(),
    val portFavorites: List<PortForward> = emptyList(),
    val snippetFavorites: List<Snippet> = emptyList()
)

data class HomeRecentItem(
    val key: String,
    val entityId: String,
    val type: HomeRecentType,
    val title: String,
    val subtitle: String,
    val sortEpochMillis: Long,
    val favorite: Boolean
)

enum class HomeRecentType { HOST, IDENTITY, PORT_FORWARD, SNIPPET }

enum class SortMode { LAST_USED, ALPHABETICAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LockTimeout(val label: String) {
    IMMEDIATE("Immediate"),
    ONE_MIN("1 minute"),
    FIVE_MIN("5 minutes"),
    FIFTEEN_MIN("15 minutes"),
    CUSTOM("Custom")
}

enum class BackgroundSessionTimeout(val label: String, val durationMillis: Long?) {
    ONE_MIN("1 minute", 60_000L),
    FIVE_MIN("5 minutes", 300_000L),
    TEN_MIN("10 minutes", 600_000L),
    THIRTY_MIN("30 minutes", 1_800_000L),
    ONE_HOUR("1 hour", 3_600_000L),
    FOREVER("Forever", null)
}
