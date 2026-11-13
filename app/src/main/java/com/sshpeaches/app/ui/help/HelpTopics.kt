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
            "Tap Fn for F1-F12. Ctrl and Shift are there too, so Ctrl then F5 sends Ctrl+F5. Back returns to the main keys.",
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
            "Long-press again and tap Paste to paste.",
            "Text copied from the terminal is removed from the clipboard after a minute, unless you have copied something else since.",
            "Android only lets an app touch the clipboard while it is on screen. If you switch to another app, the copy stays until you come back to SSHPeaches (Android 13 and later clear it themselves after about an hour)."
        ),
        keywords = listOf("clipboard", "select", "selection", "clear", "password")
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
            "The file browser reconnects too and comes back to the folder you were in. Downloads and uploads carry on: they retry for up to 15 minutes and pick up where they stopped.",
            "Mosh sessions roam between networks by themselves."
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
        id = "port-forward-socks",
        question = "How do I browse through my server (SOCKS proxy)?",
        steps = listOf(
            "Port Forwards → +, choose Dynamic, and pick a Local SOCKS port such as 1080. Set Associated host.",
            "Connect to that host. The proxy runs on this phone at 127.0.0.1 and that port while the session is open.",
            "In the app you want to send through the server, set a SOCKS5 proxy with host 127.0.0.1 and the port. " +
                "Android has no system-wide SOCKS setting, so only apps with their own proxy setting can use it " +
                "(Firefox, for example)."
        ),
        destination = HelpDestination.FORWARDS,
        keywords = listOf("socks", "socks5", "proxy", "dynamic forward", "ssh -D", "tunnel", "browse", "vpn")
    ),
    HelpTopic(
        id = "port-forward-remote",
        question = "How do I let my server reach something on this phone or its network?",
        steps = listOf(
            "Port Forwards → +, choose Remote. Server port is the port the server listens on. Destination host " +
                "and Destination port are as this phone sees them, for example 127.0.0.1 for an app on the phone.",
            "Set Associated host and connect. On the server, connecting to localhost and the Server port now " +
                "reaches the destination, for as long as the session is open.",
            "If it doesn't start, the server refused: check AllowTcpForwarding in its sshd_config, and use a " +
                "port above 1023. Listening on other than the server's localhost also needs GatewayPorts.",
            "A remote forward that came with an import (a file, Wi-Fi transfer, an OpenSSH config) is saved switched " +
                "off and marked for review: check where it leads, then turn it on. A QR code never brings one."
        ),
        destination = HelpDestination.FORWARDS,
        keywords = listOf("remote forward", "review", "imported", "reverse tunnel", "ssh -R", "expose", "gatewayports", "tunnel")
    ),
    HelpTopic(
        id = "jump-host",
        question = "How do I reach a server that is behind another one (a jump host or bastion)?",
        steps = listOf(
            "Save the jump host as a host of its own, with its password or key.",
            "Edit the server behind it and choose that host under Jump through. Connecting now logs in to the jump " +
                "host first and reaches the server from there, like ssh -J. A jump host can have a jump host too.",
            "Each host is checked and logged in separately: the app asks about each one's host key and password, " +
                "and says which host it is asking for. Your key never leaves the phone.",
            "Mosh and Boost need to reach the server directly, so through a jump host the session uses plain SSH. " +
                "Files and port forwards work as usual."
        ),
        destination = HelpDestination.HOSTS,
        keywords = listOf("jump host", "bastion", "proxyjump", "ssh -J", "gateway", "behind", "proxy")
    ),
    HelpTopic(
        id = "agent-forwarding",
        question = "How do I use my keys from a server (git pull, ssh to another server) without copying them there?",
        steps = listOf(
            "Edit the host and turn on Agent forwarding, then open a terminal on it over SSH (not Mosh; Boost steps " +
                "aside for this host).",
            "On the server, ssh, git and scp can now sign in with the keys in Identities. Run ssh-add -L there to " +
                "see them. The keys never leave this phone: the server asks, the phone signs.",
            "The session log says each time a key is used. It works for as long as the terminal is open.",
            "Turn it on only for servers you trust: while you are connected, anyone with root there can sign in to " +
                "other places as you. Keys protected by a passphrase the app hasn't saved aren't offered."
        ),
        destination = HelpDestination.HOSTS,
        keywords = listOf("agent", "ssh -A", "forward agent", "ssh-add", "git", "keys on server", "forwardagent")
    ),
    HelpTopic(
        id = "transfer",
        question = "How do I move my hosts and keys to a new phone?",
        steps = listOf(
            "On the old phone: Settings → Export, then choose Wi-Fi (both phones on the same network), QR code, File, or Share (Bluetooth, Quick Share, email).",
            "Turn on Include passwords and private keys to bring secrets too, and set an Export passphrase.",
            "On the new phone: Settings → Import and choose the same method. For Wi-Fi, scan the code the old phone shows. A shared file opens SSHPeaches when you tap it."
        ),
        destination = HelpDestination.SETTINGS,
        keywords = listOf("backup", "export", "import", "restore", "new phone", "qr", "wifi", "wi-fi", "local network", "bluetooth", "quick share", "nearby share")
    ),
    HelpTopic(
        id = "openssh-import",
        question = "Can I import hosts from ~/.ssh/config?",
        steps = listOf(
            "Copy the config file to this phone.",
            "Hosts → Import hosts (top bar) → OpenSSH config, then pick the file and review the import.",
            "Hosts and LocalForward, RemoteForward and DynamicForward entries are imported, and ProxyJump becomes Jump through when it names a host in the file or one you have saved. IdentityFile keys aren't, so add them in Identities."
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
