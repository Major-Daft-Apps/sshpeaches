package com.majordaftapps.sshpeaches.app.ui.help

/** Where a help topic's button takes you. */
enum class HelpDestination(val buttonLabel: String) {
    QUICK_CONNECT("Open Quick Connect"),
    HOSTS("Open Hosts"),
    IDENTITIES("Open Identities"),
    FORWARDS("Open Port Forwards"),
    SNIPPETS("Open Snippets"),
    KEYBOARD("Open Keyboard Editor"),
    THEMES("Open Theme Editor"),
    SETTINGS("Open Settings"),
    SHORTCUTS("Show shortcuts")
}

data class HelpTopic(
    val id: String,
    val question: String,
    val steps: List<String>,
    val destination: HelpDestination? = null,
    /** Extra words people search for that aren't in the question or steps. */
    val keywords: List<String> = emptyList()
) {
    fun matches(query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = (listOf(question) + steps + keywords).joinToString(" ").lowercase()
        return words.all { it in haystack }
    }
}

/**
 * Task-based help. Steps name buttons and fields exactly as the app shows them; keep them in step
 * with the UI when it changes (HelpContentTest checks the destinations).
 */
val helpTopics: List<HelpTopic> = listOf(
    HelpTopic(
        id = "connect",
        question = "How do I connect to a server?",
        steps = listOf(
            "Tap Quick Connect (▶) in the top bar, or Hosts → + to save the server for later.",
            "Enter Host / IP, Port (usually 22), and Username, then choose Authentication.",
            "On a saved host, tap the terminal icon for SSH, or the upload and download icons for files."
        ),
        destination = HelpDestination.QUICK_CONNECT,
        keywords = listOf("add host", "new", "ssh", "login", "start")
    ),
    HelpTopic(
        id = "key-login",
        question = "How do I log in with a key instead of a password?",
        steps = listOf(
            "Identities → + → Generate keypair (Ed25519 is a good default).",
            "Tap Install Key To Host on the key and choose the host. It uses the host's password once.",
            "Edit the host: set Authentication to Identity and pick the Identity key."
        ),
        destination = HelpDestination.IDENTITIES,
        keywords = listOf("public key", "private key", "authorized_keys", "ed25519", "rsa", "identity", "passwordless")
    ),
    HelpTopic(
        id = "special-keys",
        question = "How do I type Ctrl, Alt, Esc, Tab, or F1-F12?",
        steps = listOf(
            "Use the extra keys under the terminal. Ctrl and Alt apply to the next key you press, so Ctrl then c sends Ctrl-C.",
            "Tap Fn for F1-F12. Back returns to the main keys.",
            "Keyboard Editor changes which keys are on the two main rows."
        ),
        destination = HelpDestination.KEYBOARD,
        keywords = listOf("control", "escape", "function keys", "arrows", "extra keys", "ctrl-c", "fn")
    ),
    HelpTopic(
        id = "copy-paste",
        question = "How do I copy and paste in the terminal?",
        steps = listOf(
            "Long-press the terminal to select text, drag the handles, then tap Copy.",
            "Long-press again and tap Paste to paste."
        ),
        keywords = listOf("clipboard", "select", "selection")
    ),
    HelpTopic(
        id = "scroll-find-zoom",
        question = "How do I scroll back, find text, or change the text size?",
        steps = listOf(
            "Drag up or down on the terminal to scroll through earlier output.",
            "In a session, ⋮ → Find searches the output.",
            "Pinch the terminal to zoom, or turn on Settings → Terminal → Use volume buttons to adjust font size."
        ),
        keywords = listOf("scrollback", "search", "font", "zoom", "bigger", "smaller")
    ),
    HelpTopic(
        id = "session-menu",
        question = "What's in the three-dot menu (⋮) during a session?",
        steps = listOf(
            "Swipe for arrow keys: while it's checked, swiping on the terminal sends arrow keys, handy in editors and shell history.",
            "Insert password: types this host's saved password, after asking you first.",
            "Change theme: switches colors for this session only.",
            "Find, Snippets (run a saved command), and Reset (sends reset to fix a garbled screen)."
        ),
        keywords = listOf("three dots", "overflow", "menu", "swipe", "password", "theme", "reset")
    ),
    HelpTopic(
        id = "background",
        question = "How do I keep a session running when I leave the app?",
        steps = listOf(
            "Turn on Settings → Background Sessions → Run shells in background.",
            "Background connection timeout sets how long sessions stay open while you're away.",
            "A notification shows while sessions run. Tap it to return to the session."
        ),
        destination = HelpDestination.SETTINGS,
        keywords = listOf("disconnect", "keep alive", "background", "screen off", "sleep")
    ),
    HelpTopic(
        id = "network-drop",
        question = "What happens when my network drops or changes?",
        steps = listOf(
            "SSH terminals reconnect on their own and keep the screen. This is Settings → Background Sessions → Reconnect automatically.",
            "The shell itself restarts on the server, so turn on Attach to tmux in the host's settings to land back in the same shell.",
            "Mosh sessions and the file browser don't reconnect; open them again."
        ),
        destination = HelpDestination.SETTINGS,
        keywords = listOf("reconnect", "reconnecting", "wifi", "mobile data", "tmux", "disconnected", "dropped")
    ),
    HelpTopic(
        id = "two-factor",
        question = "My server asks for a verification code. How do I log in?",
        steps = listOf(
            "Connect as usual. When the server asks a question such as a verification code, SSHPeaches shows it in a dialog.",
            "Type the code from your authenticator app and tap Continue. The saved password is still sent for the password question."
        ),
        keywords = listOf("2fa", "otp", "totp", "duo", "google authenticator", "keyboard-interactive", "pam", "code")
    ),
    HelpTopic(
        id = "file-tools",
        question = "How do I edit a file or change its permissions on the server?",
        steps = listOf(
            "Open the host with the upload or download icon to get the file browser. Tap the path above the list to jump to a parent folder.",
            "Select a file, then the ⋮ actions menu: Edit opens small text files in an editor that saves back to the server; Permissions changes who can read, write, or run it.",
            "A download that fails partway picks up where it stopped when you download the same file again."
        ),
        keywords = listOf("sftp", "chmod", "edit", "text editor", "resume", "download", "breadcrumb", "rwx")
    ),
    HelpTopic(
        id = "port-forward",
        question = "How do I open a web page or database on my server through SSH?",
        steps = listOf(
            "Port Forwards → +. Local port is the port on this phone. Destination host and Destination port " +
                "are as the server sees them, often localhost and the service's port.",
            "Set Associated host and leave Enable now on. The forward starts when you connect to that host.",
            "While connected, open localhost and the Local port on this phone, for example http://localhost:8080."
        ),
        destination = HelpDestination.FORWARDS,
        keywords = listOf("tunnel", "port forwarding", "local forward", "web ui", "dashboard", "postgres", "mysql")
    ),
    HelpTopic(
        id = "transfer",
        question = "How do I move my hosts and keys to a new phone?",
        steps = listOf(
            "On the old phone: Settings → Export, then choose Wi-Fi (both phones on the same network), QR code, or File.",
            "Turn on Include passwords and private keys to bring secrets too, and set an Export passphrase.",
            "On the new phone: Settings → Import and choose the same method. For Wi-Fi, scan the code the old phone shows."
        ),
        destination = HelpDestination.SETTINGS,
        keywords = listOf("backup", "export", "import", "restore", "new phone", "qr", "wifi", "wi-fi", "local network")
    ),
    HelpTopic(
        id = "openssh-import",
        question = "Can I import hosts from ~/.ssh/config?",
        steps = listOf(
            "Copy the config file to this phone.",
            "Hosts → Import hosts (top bar) → OpenSSH config, then pick the file and review the import.",
            "Hosts and LocalForward entries are imported. IdentityFile keys aren't, so add them in Identities."
        ),
        destination = HelpDestination.HOSTS,
        keywords = listOf("openssh", "config", "import", "ssh_config")
    ),
    HelpTopic(
        id = "snippets",
        question = "How do I save and run commands I use often?",
        steps = listOf(
            "Snippets → + to save a command.",
            "In a session, ⋮ → Snippets runs one in the terminal.",
            "A host's Startup snippet runs every time you connect to it."
        ),
        destination = HelpDestination.SNIPPETS,
        keywords = listOf("commands", "script", "macro", "startup")
    ),
    HelpTopic(
        id = "forgot-pin",
        question = "What if I forget my PIN?",
        steps = listOf(
            "There is no PIN recovery. Your saved passwords and keys are encrypted with it.",
            "If biometric unlock is on, unlock with your fingerprint instead.",
            "Otherwise the only way back in is Android Settings → Apps → SSHPeaches → Storage → Clear storage, " +
                "which deletes all hosts, keys, and passwords. Keep an export as a backup."
        ),
        destination = HelpDestination.SETTINGS,
        keywords = listOf("pin", "lock", "locked out", "fingerprint", "biometric", "reset")
    ),
    HelpTopic(
        id = "themes",
        question = "How do I change the terminal colors or font?",
        steps = listOf(
            "Theme Editor creates and edits terminal themes: colors, Font, and Font Size.",
            "Pick a host's Terminal profile to give it its own theme.",
            "In a session, ⋮ → Change theme switches only that session."
        ),
        destination = HelpDestination.THEMES,
        keywords = listOf("colour", "color", "font", "dark", "light", "profile", "appearance")
    ),
    HelpTopic(
        id = "hardware-keyboard",
        question = "Are there shortcuts for a hardware keyboard?",
        steps = listOf(
            "Ctrl+K opens Quick Connect, F1 opens Help, and Alt+1 to Alt+8 jump between screens.",
            "Ctrl+/ shows the full list."
        ),
        destination = HelpDestination.SHORTCUTS,
        keywords = listOf("bluetooth", "keyboard", "shortcuts", "hotkeys", "chromebook", "dex")
    ),
    HelpTopic(
        id = "wont-connect",
        question = "Why won't it connect?",
        steps = listOf(
            "When a connection fails, the connection screen says what went wrong and what to try, " +
                "with buttons to edit the host or copy the log.",
            "Home or office addresses (192.168.x.x, 10.x.x.x) only work on that network or through a VPN.",
            "If the server turns off password logins, log in with a key (see above)."
        ),
        keywords = listOf("error", "failed", "timeout", "refused", "denied", "network", "host key")
    )
)
