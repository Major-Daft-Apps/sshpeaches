package com.majordaftapps.sshpeaches.app.util

/**
 * Quotes [value] as one word for a POSIX shell: single quotes, with each `'` written as `'\''`.
 * Nothing inside single quotes is special, so the result can't run anything (sshj's own SingleQuote
 * mode writes `\'`, which closes the quote instead).
 */
fun posixShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * A remote path for the `scp -f`/`scp -t` command line, quoted for the remote shell. Paths that
 * would read as an option get a `./` prefix. Throws for names SCP can't carry safely.
 */
fun scpRemotePathArgument(path: String): String {
    require(path.isNotEmpty()) { "Remote path is required." }
    require(path.none { it == '\u0000' || it == '\n' || it == '\r' }) {
        "SCP can't transfer a file whose name contains a line break. Use SFTP for this file."
    }
    return posixShellQuote(if (path.startsWith("-")) "./$path" else path)
}
