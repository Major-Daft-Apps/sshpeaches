# SSHPeaches UI Design Notes

> **Last updated:** 2026-03-20. Pair this with `Product-Blueprint` for product context.

This document captures first-pass layouts for key SSHPeaches screens. Use it as a blueprint when building mockups (Figma/Penpot) and implementing Compose UI.

---

## 1. Home Screen
```text
+---------------- App Bar ----------------+
| [Menu] SSHPeaches         [Quick Connect] |
+-----------------------------------------+
```
- `Quick Connect` icon opens ad-hoc bottom sheet. Drawer also includes Help (external site via Custom Tab) and About (modal moved out of Settings; shows icon, version, build info, license links). Global theme uses Carbon Black `#191919` backgrounds with Blazing Flame `#F15025` highlight accents.
- Body uses vertically stacked sections:
  - **Open Sessions** appears only when at least one session exists. Each row has icon-only reopen and disconnect actions.
  - **Favorites** stays grouped by type: Hosts, Identities, Port Forwards, Snippets.
  - **Recents** is a single mixed list ordered newest-first.
- Empty app state uses a branded welcome panel with the `sshpeaches` artwork, a short onboarding line, and four stacked buttons for adding the first resource.

## 2. Hosts Screen
- Same app bar.
- Content:
  - Search bar with group-aware filtering and sort menu for Last Used / A->Z.
  - Add and Import QR actions live in the top app bar.
  - Hosts are rendered inside collapsible group sections.
- Card layout keeps the existing host action buttons. Empty space on the card is not clickable. Edit and delete sit in the overflow menu. OS icon: white glyph on colored background tied to distro (e.g., Ubuntu orange, Debian red, Fedora blue, SUSE green, Mint teal, macOS gray, BSD maroon, Arch cyan). Default desktop icon used until detection runs.

### Port Forwarding UI <-> SSH flags
- Each forward is a card with a Local (`-L`) badge, label, summary of bind/destination, enable toggle, edit/delete icons.
- "Add port forward" dialog fields:
  - Type: Local only (`-L`)
  - Bind address + Port (local bind) -> `[bind_address:]port`
  - Destination host + port -> `dstHost:dstPort`
  - Toggle "Fail if bind can't start" -> `-o ExitOnForwardFailure=yes`
  - Enabled switch (applies immediately when wired to backend)
- Notes shown in dialog:
  - Remote (`-R`) and Dynamic (`-D`) forwarding are intentionally out of scope.
  - These are SSH TCP forwards, not VPN; only apps pointed at the bind port use them.

## 3. Quick Connect Sheet
```text
+-------------- Quick Connect ---------------+
| Host/IP        [_______________________]   |
| Port (22)      [__]   Username [______]    |
| Auth           (*) Password ( ) Identity   |
|                ( ) Both (identity first)   |
| Password field or identity picker          |
| Forwarded Port [saved forward dropdown]    |
| Optional Script [inline editor + file btn] |
| Toggles: [ ] Mosh   [ ] Pin to Favorites   |
| Last used host/user chips                  |
| [Cancel]                    [Connect]      |
+--------------------------------------------+
```

## 4. Snippet Manager
- List shows snippet title/description/command with Run / Edit / Delete actions.
- Snippets are grouped in collapsible sections.
- Run stays visible on the card; Edit and Delete live in the overflow menu.
- Add/import actions live in the top app bar.

## 4. New Connection Flow
- Modal or dedicated screen with tabs **Basic** / **Advanced**.
- **Basic**: Name, Host, Port, Username. Directly below Username: auth selector, password controls, and identity picker. Group and notes follow after the authentication block.
- **Advanced**:
  - Forwarded port selector (dropdown of configured forwards).
  - Optional script editor (multi-line text field) + "Choose file" button.
  - Checkbox for Mosh fallback.
  - Background behavior toggle (inherit global / always allow / never allow).
  - Info panel preset link ("Edit Info Commands").
- Footer buttons: Cancel / Save / Connect Now.

## 5. Info Panel & Editor
### Info Panel Modal
```text
+---------------- Host Info -----------------+
| Command label   [Run on demand ->]         |
| Output (monospace box, collapsible)        |
| ... (one block per command)                |
| [Edit Commands]                            |
+--------------------------------------------+
```
### Editor Sheet
- Reorderable list of cards, each containing:
  - Label text field.
  - Command textarea.
  - Switch: Auto-run on open.
  - Drag handle (`=`) and delete icon.
- Buttons: `+ Add Command`, `Reset to defaults`, `Preview`.

## 6. Identities Screen
- Toolbar with search.
- Rows show key alias, short fingerprint (truncate SHA-256 to 8-10 chars or colon-separated hex), last used, favorite, and share action.
- Add/import actions live in the top app bar.
- Identities are grouped in collapsible sections.
- Edit/delete live in the overflow menu.

## 7. Snippet Manager
- Accessible from drawer and from session toolbar.
- Main view:
  - Filter chips for tags (Diagnostics, Deploy, Maintenance, etc.).
  - Snippet cards show title, short description, favorite icon, edit/delete buttons, and quick "Insert" action.
  - Toggle "Auto-run on connect" per snippet; indicator showing attached hosts/groups.
- Editor view:
  - Fields: Name, Description, Command (multi-line with syntax highlighting), Tags, Variables (placeholder chips like `{host}`, `{user}`, `{custom}`).
  - Options: "Require confirmation before run," "Auto-run when host connects," "Share via QR."
  - Buttons: Save, Run Now (select target session), Share.
- In-session UI:
  - "Snippets" button in toolbar opens bottom sheet listing favorites + recents with search, variable prompts, and quick run buttons.
  - Auto-run snippets display inline confirmation chips when triggered.

## 8. Port Forwards Screen
- Mirrors JuiceSSH Pro behavior.
- Local forwarding list only (no Remote/Dynamic tabs).
- Each forward entry includes:
  - Title (e.g., "Local 9000 -> 10.0.0.5:5432").
  - Bind address input (default 127.0.0.1 for local/dynamic, remote host for remote).
  - Associated host dropdown (multi-select) to auto-start when specified hosts connect.
  - Manual enable/disable toggle (works even when not connected).
  - Favorite/share controls plus overflow edit/delete.
- Entries are grouped in collapsible sections.
- Top app bar icons open add/import actions. The old edit mode is gone.
- "Add Port Forward" sheet fields:
  - Type selector fixed to Local (`-L`).
  - Source port + bind address.
  - Destination host/port.
  - Host association multi-select.
  - Notes/description field.
- During sessions, toolbar indicator shows active forwards with toggles/status badges; disabling stops the tunnel but keeps SSH session alive. Toast/notification confirms success/failure.

## 9. Keyboard Editor
```text
+----------- Key Palette (scrollable) -----------+
| Row 1: ESC, TAB, CTRL, ALT, SHIFT,             |
|        Swipe->Arrow, Swipe->Scroll             |
| Groups: [Fn v] [a v] [A v] [1 v] [# v]         |
| F1-F12, lowercase, uppercase, numbers, symbols |
| Drag items from the palette onto rows          |
+------------------------------------------------+

+-------- Row Controls --------+   +------- Row 1 -------+
| Row 1  [ + Add Row ]         |   | ESC TAB CTRL ...    |
| Compact keys [ ]             |   +---------------------+
+------------------------------+   (drag destinations)
```
- Users drag from palette to any row; keys behave like movable blocks within each row (free placement along the row's width, no strict grid).
- Sticky swipe-to-arrow toggle is always present (cannot remove, but can reposition).
- Keyboard Editor shortcut key available in palette.

## 10. QR Share / Export UI
- **Share Host**: bottom sheet shows QR preview with pagination dots, "Frame X/Y," checksum badge, `Pause`, `Resume`, `Save as PNG`.
- **Export All**: similar but includes progress bar and "Include identities / settings" checkboxes. Scanner UI for import shows live feed with progress counter and error recovery panel.

## 11. Security & Background Settings
- Section includes:
  - Toggle: "Allow shells to run in background."
  - Dropdown: "Default per-connection behavior" (Inherit / Always allow / Always stop).
  - Toggle: "Require biometric unlock" with timeout selector (Immediate, 1 min, 5 min, 15 min, Custom). Shows preview of biometric prompt + fallback PIN flow.
  - Info copy about battery/network usage and privacy.
- Connection-specific override (background behavior) appears in Advanced tab as described above.

## 12. App Lock Screen
```text
+---------------- SSHPeaches Lock ----------------+
|                  (Peach logo)                  |
|              SSHPeaches is locked              |
|              Last unlocked: 2m ago             |
|                                                |
|        [Fingerprint icon] Touch to unlock      |
|             [PIN fallback button]              |
|                                                |
|       "Use different account" / "Switch        |
|                    identity"                   |
+------------------------------------------------+
```
- Triggered after inactivity timeout or manual lock.
- Shows branding, last-unlocked timestamp, and optional message if a session is running in background.
- Primary action: biometric prompt (fingerprint/face). If unavailable/fails, fallback button opens PIN/password entry sheet.
- Device's back button exits app (sessions paused until unlock). If background sessions allowed, notification indicates "Locked - session running".
- Settings link (gear icon) appears if user needs to adjust timeout once authenticated.

---

### Next Steps
- Create Figma frames for each screen with the black-and-orange palette.
- Define shared components (cards, buttons, toggles) as reusable variants.
- Link frames to blueprint requirements and note interactions (group collapse, overflow actions, info editor, QR scanner).


