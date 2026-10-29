# SSHPeaches Troubleshooting

## Connection fails immediately

Check:

- Host/IP and port are correct.
- Device network can reach the server.
- Username and auth method match the server.
- If using identity auth, an identity key is selected and imported.

If the app asks for password repeatedly, verify server-side SSH settings (`PasswordAuthentication`, `PubkeyAuthentication`, and allowed users).

## Host key warning or mismatch

If SSHPeaches reports a changed host key:

1. Stop and verify server fingerprint through an out-of-band method.
2. Do not trust automatically unless you confirmed the change.
3. In host edit dialog, use **Clear host key** when rotation is expected and trusted.

## Identity import fails

Common causes:

- App is locked (unlock first).
- Invalid key format in file.
- Wrong passphrase when importing encrypted QR key payload.

Try importing a standard OpenSSH private key file again from **Identities**.

## QR import says duplicate or invalid

- Host QR import rejects duplicates by host name.
- Identity QR may request overwrite if fingerprint already exists.
- Port forward import supports only local forwarding type.
- Export passphrase must match exactly when encrypted payload is included.

## Snippet run does nothing

Snippets require an active session.

- Open/connect at least one host first.
- Re-run snippet from **Snippets**.
- If still failing, check connection logs in the active session screen.

## SFTP/SCP transfer issues

Check:

- Remote path exists and permissions allow access.
- Local destination path is writable.
- Session is still active.
- For SCP, select source and destination before copy actions.

Transfers log in to the server again, on up to four extra connections (**Settings** -> **Advanced** -> **Connections per transfer**), so browsing never waits for them. If the server refuses a second login (it allows one connection per user, or asks for a one-time code), transfers share the session's own connection instead, and are slower. The session log says when that happens.

## Boost isn't used

The session log says why when Boost can't start ("Boost isn't available: ...") and the app uses SFTP or SSH:

- "the server's firewall doesn't let UDP through" or "lets neither UDP nor TCP through": open the **Boost ports**
  range (**Settings** -> **Advanced**, default `60000-61000` UDP, plus TCP for the fallback) in the server's firewall,
  or set the range to ports it already allows.
- "the server isn't Linux" or "the server's processor isn't supported": the helper only runs on Linux, x86_64 or ARM64.
- "no free UDP port": every port in the range is in use; widen it.
- "Boost is still being set up on this server": the first session copies the helper (about 6 MB); on a slow link the
  terminal opens over SSH meanwhile and the next session uses Boost.

After a host's Boost terminal fails to start, terminals to that host use SSH for 30 minutes without trying again.

## Background sessions not staying alive

Verify:

- **Settings** -> **Run shells in background** is enabled.
- Android battery optimization is not aggressively stopping the app.
- App notifications are allowed (required for foreground session service behavior on modern Android).

## Locked out by PIN

- Use **Unlock with PIN** on lock screen.
- If biometric is configured and available, use biometric unlock.
- If PIN is forgotten, app reinstall may be required to reset secure local secrets.

## Data restore/import expectations

QR transfer supports:

- Host definitions
- Identity metadata (and encrypted private keys when exported that way)
- Port forwards
- Optional app settings (when selected at export time)

It is local-device transfer, not cloud sync.

## Need more help

- In-app: Drawer -> **Help**
- Support page: <https://majordaftapps.com/sshpeaches-support>
- GitHub issues in this repository
