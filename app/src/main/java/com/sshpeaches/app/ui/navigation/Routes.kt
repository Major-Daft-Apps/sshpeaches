package com.majordaftapps.sshpeaches.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val FAVORITES = HOME
    const val CONNECTING = "connecting"
    const val SESSION = "session"
    const val HOSTS = "hosts"
    const val UPTIME = "uptime"
    const val IDENTITIES = "identities"
    const val FORWARDS = "forwards"
    const val SNIPPETS = "snippets"
    const val SNIPPET_EDITOR = "snippet_editor"
    const val SNIPPET_EDITOR_ROUTE = "$SNIPPET_EDITOR?snippetId={snippetId}"
    const val KEYBOARD = "keyboard"
    const val THEME_EDITOR = "theme_editor"
    const val THEME_EDITOR_EDIT = "theme_editor_edit"
    const val THEME_EDITOR_EDIT_ROUTE = "$THEME_EDITOR_EDIT?profileId={profileId}&duplicate={duplicate}"
    const val SETTINGS = "settings"
    const val OPEN_SOURCE_LICENSES = "open_source_licenses"
    const val HELP = "help"
    const val QUICK_CONNECT = "quick_connect"
    const val ABOUT = "about"

    fun themeEditorEdit(profileId: String? = null, duplicate: Boolean = false): String =
        "$THEME_EDITOR_EDIT?profileId=${profileId.orEmpty()}&duplicate=$duplicate"

    fun snippetEditor(snippetId: String? = null): String =
        "$SNIPPET_EDITOR?snippetId=${snippetId.orEmpty()}"
}
