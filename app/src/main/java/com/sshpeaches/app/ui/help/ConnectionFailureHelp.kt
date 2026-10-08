package com.majordaftapps.sshpeaches.app.ui.help

import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind

/** Buttons the failure screen can offer next to the tips. */
enum class FailureHelpAction {
    /** Open the saved host's editor (port, username, Authentication, Clear stored host key). */
    EDIT_HOST,
    /** Open Identities (Generate keypair, Install Key To Host). */
    OPEN_IDENTITIES
}

data class ConnectionFailureHelp(
    val title: String,
    val tips: List<String>,
    val actions: List<FailureHelpAction>
)

/**
 * What to try after a failed connection, worded for the specific failure. Labels in the tips must
 * match the app's screens; [HelpContentTest] checks the ones it can.
 */
fun connectionFailureHelp(
    kind: ConnectionFailureKind?,
    host: String,
    port: Int,
    username: String,
    isSavedHost: Boolean
): ConnectionFailureHelp {
    val editHost = if (isSavedHost) listOf(FailureHelpAction.EDIT_HOST) else emptyList()
    val privateAddress = isPrivateAddress(host)
    return when (kind) {
        ConnectionFailureKind.UNKNOWN_HOST -> ConnectionFailureHelp(
            title = "Can't find $host",
            tips = listOf(
                "Check the spelling of the host name.",
                "Names that only work on your home or office network (such as .local or .lan names) " +
                    "won't work elsewhere. Use the IP address, or join that network or its VPN first.",
                "Check that this phone is online."
            ),
            actions = editHost
        )
        ConnectionFailureKind.REFUSED -> ConnectionFailureHelp(
            title = "Nothing is accepting connections on port $port",
            tips = listOf(
                "The SSH server isn't running, or it listens on a different port. The usual port is 22.",
                "On the server, check it with: sudo systemctl status ssh (on some systems: sshd).",
                "A firewall on the server may be blocking port $port."
            ),
            actions = editHost
        )
        ConnectionFailureKind.UNREACHABLE -> ConnectionFailureHelp(
            title = "Can't reach $host",
            tips = listOfNotNull(
                if (privateAddress) {
                    "$host is a private address. It only works on the same network, or through a " +
                        "VPN such as Tailscale or WireGuard. It won't work over mobile data."
                } else {
                    null
                },
                "Check that this phone is online.",
                "Check that the server is switched on and the address and port are right.",
                if (privateAddress) {
                    null
                } else {
                    "A firewall or router may be blocking port $port. Home servers also need port forwarding on the router."
                }
            ),
            actions = editHost
        )
        ConnectionFailureKind.NETWORK -> ConnectionFailureHelp(
            title = "The connection dropped",
            tips = listOf(
                "The network dropped or changed, for example from Wi-Fi to mobile data. Tap Retry.",
                "If this keeps happening while the app is in the background, turn on " +
                    "Settings → Background Sessions → Run shells in background."
            ),
            actions = emptyList()
        )
        ConnectionFailureKind.HOST_KEY_REJECTED -> ConnectionFailureHelp(
            title = "Server identity not accepted",
            tips = listOf(
                "SSHPeaches stopped before logging in because the server's host key wasn't accepted.",
                "If the server was reinstalled or its keys were regenerated, edit the host, tap " +
                    "Clear stored host key, connect again, and check the fingerprint.",
                "If nothing changed on the server, don't connect. Someone may be intercepting the connection."
            ),
            actions = editHost
        )
        ConnectionFailureKind.AUTH_PASSWORD -> ConnectionFailureHelp(
            title = "The server rejected the password",
            tips = listOf(
                "Check the username ($username). It is case-sensitive.",
                "Many servers turn off password logins. Use a key instead: Identities → + → " +
                    "Generate keypair, then Install Key To Host, then set the host's Authentication to Identity.",
                "Servers often block logging in as root. Log in as a normal user."
            ),
            actions = editHost + FailureHelpAction.OPEN_IDENTITIES
        )
        ConnectionFailureKind.AUTH_KEY -> ConnectionFailureHelp(
            title = "The server rejected the key",
            tips = listOf(
                "The public key must be in ~/.ssh/authorized_keys for $username on the server. " +
                    "Identities → Install Key To Host adds it, using the host's password once.",
                "Check the username ($username) and that the host uses the right Identity key.",
                "On the server, ~/.ssh must be mode 700 and authorized_keys mode 600, or SSH ignores them."
            ),
            actions = editHost + FailureHelpAction.OPEN_IDENTITIES
        )
        ConnectionFailureKind.KEY_MISSING -> ConnectionFailureHelp(
            title = "No key to log in with",
            tips = listOf(
                "This host logs in with a key, but none is selected or the key was deleted.",
                "Edit the host and pick an Identity key, or set Authentication to Password."
            ),
            actions = editHost + FailureHelpAction.OPEN_IDENTITIES
        )
        ConnectionFailureKind.AUTH -> ConnectionFailureHelp(
            title = "Login rejected",
            tips = listOf(
                "Check the username ($username), password, and Identity key.",
                "If the server doesn't allow passwords, use a key: Identities → Install Key To Host."
            ),
            actions = editHost + FailureHelpAction.OPEN_IDENTITIES
        )
        ConnectionFailureKind.MOSH_SERVER -> ConnectionFailureHelp(
            title = "Mosh couldn't start on the server",
            tips = listOf(
                "Install mosh on the server, for example: sudo apt install mosh.",
                "If mosh-server isn't on the PATH, set its full path in Settings → Advanced settings → Mosh server command.",
                "Mosh also needs UDP ports 60000-61000 open on the server's firewall."
            ),
            actions = editHost
        )
        null -> ConnectionFailureHelp(
            title = "What to try",
            tips = listOf(
                "Check the address, port ($port), and username, then tap Retry.",
                "The log below has the details. Copy log is useful if you report a problem."
            ),
            actions = editHost
        )
    }
}

/** RFC 1918, CGNAT (Tailscale), and link-local IPv4 addresses, plus local-only names. */
internal fun isPrivateAddress(host: String): Boolean {
    val value = host.trim().lowercase()
    if (value.endsWith(".local") || value.endsWith(".lan") || value.endsWith(".home.arpa")) return true
    val octets = value.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (a, b) = octets
    return a == 10 ||
        (a == 172 && b in 16..31) ||
        (a == 192 && b == 168) ||
        (a == 100 && b in 64..127) ||
        (a == 169 && b == 254)
}
