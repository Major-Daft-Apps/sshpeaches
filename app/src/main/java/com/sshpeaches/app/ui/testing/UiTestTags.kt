package com.majordaftapps.sshpeaches.app.ui.testing

object UiTestTags {
    const val SCREEN_FAVORITES = "screen_favorites"
    const val SCREEN_HOSTS = "screen_hosts"
    const val SCREEN_IDENTITIES = "screen_identities"
    const val SCREEN_FORWARDS = "screen_forwards"
    const val SCREEN_SNIPPETS = "screen_snippets"
    const val SCREEN_SETTINGS = "screen_settings"

    const val DRAWER_QUICK_CONNECT = "drawer_quick_connect"
    fun drawerItem(route: String): String = "drawer_item_$route"

    const val HOST_ADD_BUTTON = "host_add_button"
    const val HOST_DIALOG_NAME_INPUT = "host_dialog_name_input"
    const val HOST_DIALOG_HOST_INPUT = "host_dialog_host_input"
    const val HOST_DIALOG_PORT_INPUT = "host_dialog_port_input"
    const val HOST_DIALOG_USERNAME_INPUT = "host_dialog_username_input"
    const val HOST_DIALOG_SCROLL = "host_dialog_scroll"
    const val HOST_DIALOG_AUTH_FIELD = "host_dialog_auth_field"
    fun hostDialogAuthOption(authMethodName: String): String = "host_dialog_auth_option_$authMethodName"
    const val HOST_DIALOG_CONFIRM_BUTTON = "host_dialog_confirm_button"
    const val HOST_DIALOG_CANCEL_BUTTON = "host_dialog_cancel_button"
    const val HOST_DIALOG_ERROR = "host_dialog_error"

    const val SETTINGS_THEME_MODE_FIELD = "settings_theme_mode_field"
    const val SETTINGS_BACKGROUND_SWITCH = "settings_background_switch"
    const val SETTINGS_TERMINAL_EMULATION_FIELD = "settings_terminal_emulation_field"
    const val SETTINGS_DIAGNOSTICS_SWITCH = "settings_diagnostics_switch"
    const val SETTINGS_INCLUDE_IDENTITIES_SWITCH = "settings_include_identities_switch"
    const val SETTINGS_INCLUDE_SETTINGS_SWITCH = "settings_include_settings_switch"
}
