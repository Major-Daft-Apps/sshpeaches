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
