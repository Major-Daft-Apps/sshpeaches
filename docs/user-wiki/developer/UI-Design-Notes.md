# SSHPeaches UI Design Notes

> **Last updated:** 2026-01-30.  Pair this with `Product-Blueprint.md` for product context.

This document captures first-pass layouts for key SSHPeaches screens. Use it as a blueprint when building mockups (Figma/Penpot) and implementing Compose UI.

---

## 1. Favorites (Landing) Screen
```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ App Bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ â˜°  SSHPeaches                         â¦¿â”‚  (â¦¿ = Quick Connect icon)
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```
- `Quick Connect` icon opens ad-hoc bottom sheet. Drawer also includes Help (external site via Custom Tab) and About (modal moved out of Settings; shows icon, version, build info, license links). Global theme uses Carbon Black `#191919` backgrounds with Blazing Flame `#F15025` highlight accents.
- Body uses vertically stacked sections with sticky headers:
  - **Hosts** â€“ list of favorited hosts. Each card mirrors Hosts layout (OS icon, name, tags, buttons). Include inline â˜… toggle to unfavorite.
  - **Identities** â€“ compact rows showing key alias, fingerprint, tag, and actions (Edit/Delete/Use).
  - **Port Forwards** â€“ cards summarizing local â†” remote mapping, protocol, associated host; inline â˜… toggle.
- Empty state copy if a section has no favorites.

## 2. Hosts Screen
- Same app bar.
- Secondary toolbar directly beneath header:
  ```
  â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Toolbar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
  â”‚ Edit / Done           New        â”‚
  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
  ```
  - Left button toggles edit mode (reveals pencil/dash overlays).
  - Right button opens New Connection form.
- Content:
  - Search bar with filter pill for Groups and triple-dot menu for sorting (Last Used / Aâ†’Z).
- Card layout mirrors Favorites view. OS icon: white glyph on colored background tied to distro (e.g., Ubuntu orange, Debian red, Fedora blue, SUSE green, Mint teal, macOS gray, BSD maroon, Arch cyan). Default desktop icon used until detection runs.

### Port Forwarding UI â†” SSH flags
- Each forward is a card with a Local (`-L`) badge, label, summary of bind/destination, enable toggle, edit/delete icons.
- â€œAdd port forwardâ€ dialog fields:
  - Type: Local only (`-L`)
  - Bind address + Port (local bind) â†’ `[bind_address:]port`
  - Destination host + port â†’ `dstHost:dstPort`
  - Toggle â€œFail if bind canâ€™t startâ€ â†’ `-o ExitOnForwardFailure=yes`
  - Enabled switch (applies immediately when wired to backend)
- Notes shown in dialog:
  - Remote (`-R`) and Dynamic (`-D`) forwarding are intentionally out of scope.
  - These are SSH TCP forwards, not VPN; only apps pointed at the bind port use them.

## 3. Quick Connect Sheet
```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Quick Connect â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ Host/IP        [_______________________]  â”‚
â”‚ Port (22)      [__]   Username [______]   â”‚
â”‚ Auth           (â€¢) Password ( ) Identity  â”‚
â”‚                ( ) Both (identity first)  â”‚
â”‚ Password/Input field or Identity picker   â”‚
â”‚ Forwarded Port [Dropdown of saved forwards]â”‚
â”‚ Optional Script [Inline editor + file btn]â”‚
â”‚ Toggles: [ ] Mosh   [ ] Pin to Favorites  â”‚
â”‚ Sticky chips for â€œLast used hosts/usersâ€  â”‚
â”‚ [Cancel]                  [Connect]       â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

## 4. Snippet Manager
- List shows snippet title/description/command with Run / Edit / Delete actions.
- â€œAdd snippetâ€ button opens dialog for Title, Description, Command. Edit reuses the same dialog; Delete available when editing.

## 4. New Connection Flow
- Modal or dedicated screen with tabs **Basic** / **Advanced**.
- **Basic**: Name, Host, Username, Password field, Identity picker (allow both). â€œAdd to Groupâ€ button.
- **Advanced**:
  - Port input (default 22).
  - Forwarded port selector (dropdown of configured forwards).
  - Optional script editor (multi-line text field) + â€œChoose fileâ€ button.
  - Checkbox for Mosh fallback.
  - Background behavior toggle (inherit global / always allow / never allow).
  - Info panel preset link (â€œEdit Info Commandsâ€).
- Footer buttons: Cancel / Save / Connect Now.

## 5. Info Panel & Editor
### Info Panel Modal
```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Host Info â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ Command label   [Run on demand â–·]   â”‚
â”‚ Output (monospace box, collapsible) â”‚
â”‚ ... (one block per command)         â”‚
â”‚ [Edit Commands]                     â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```
### Editor Sheet
- Reorderable list of cards, each containing:
  - Label text field.
  - Command textarea.
  - Switch: Auto-run on open.
  - Drag handle (â‰¡) and delete icon.
- Buttons: `+ Add Command`, `Reset to defaults`, `Preview`.

## 6. Identities Screen
- Toolbar with search.
- Rows show key alias, short fingerprint (truncate SHA-256 to 8â€“10 chars or colon-separated hex), last used, and icons for edit, export, delete.
- FAB (`+`) opens menu: â€œEnter manuallyâ€, â€œImport from fileâ€, â€œGenerate keypairâ€.
- Each identity row has a star icon for Favorites.

## 7. Snippet Manager
- Accessible from drawer and from session toolbar.
- Main view:
  - Filter chips for tags (Diagnostics, Deploy, Maintenance, etc.).
  - Snippet cards show title, short description, favorite icon, edit/delete buttons, and quick â€œInsertâ€ action.
  - Toggle â€œAuto-run on connectâ€ per snippet; indicator showing attached hosts/groups.
- Editor view:
  - Fields: Name, Description, Command (multi-line with syntax highlighting), Tags, Variables (placeholder chips like `{host}`, `{user}`, `{custom}`).
  - Options: â€œRequire confirmation before run,â€ â€œAuto-run when host connects,â€ â€œShare via QR.â€
  - Buttons: Save, Run Now (select target session), Share.
- In-session UI:
  - â€œSnippetsâ€ button in toolbar opens bottom sheet listing favorites + recents with search, variable prompts, and quick run buttons.
  - Auto-run snippets display inline confirmation chips when triggered.

## 8. Port Forwards Screen
- Mirrors JuiceSSH Pro behavior.
- Local forwarding list only (no Remote/Dynamic tabs).
- Each forward entry includes:
  - Title (e.g., â€œLocal 9000 â†’ 10.0.0.5:5432â€).
  - Bind address input (default 127.0.0.1 for local/dynamic, remote host for remote).
  - Associated host dropdown (multi-select) to auto-start when specified hosts connect.
  - Manual enable/disable toggle (works even when not connected).
  - Icons: star (favorite), edit (pencil), delete (trash).
- FAB opens â€œAdd Port Forwardâ€ sheet with fields:
  - Type selector fixed to Local (`-L`).
  - Source port + bind address.
  - Destination host/port.
  - Host association multi-select.
  - Notes/description field.
- During sessions, toolbar indicator shows active forwards with toggles/status badges; disabling stops the tunnel but keeps SSH session alive. Toast/notification confirms success/failure.

## 9. Keyboard Editor
```
 â”Œâ”€â”€â”€â”€â”€â”€â”€â”€ Key Palette (scrollable) â”€â”€â”€â”€â”€â”€â”€â”€â”
 â”‚ Row 1: core toggles (ESC, TAB, CTRL, ALT,â”‚
 â”‚        SHIFT, Swipeâ†’Arrow, Swipeâ†’Scroll) â”‚
 â”‚ Dropdown groups: [Fn â–¾] [a â–¾] [A â–¾] [1 â–¾]â”‚
 â”‚ [# â–¾] for F1â€“F12, lowercase, uppercase,  â”‚
 â”‚ numbers, and symbols. Drag options out   â”‚
 â”‚ of the dropdown onto rows.               â”‚
 â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
â”Œâ”€â”€â”€â”€â”€â”€ Row Controls â”€â”€â”€â”€â”€â”   â”Œâ”€â”€â”€â”€â”€â”€ Row 1 â”€â”€â”€â”€â”€â”€â”€â”
â”‚ Row 1  [ + Add Row ]    â”‚   â”‚ ESC TAB CTRL ...    â”‚
â”‚ Compact keys [ ]        â”‚   â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜   (drag destinations)
```
- Users drag from palette to any row; keys behave like movable blocks within each row (free placement along the rowâ€™s width, no strict grid).
- Sticky swipe-to-arrow toggle is always present (cannot remove, but can reposition).
- Keyboard Editor shortcut key available in palette.

## 10. QR Share / Export UI
- **Share Host**: bottom sheet shows QR preview with pagination dots, â€œFrame X/Y,â€ checksum badge, `Pause`, `Resume`, `Save as PNG`.
- **Export All**: similar but includes progress bar and â€œInclude identities / settingsâ€ checkboxes. Scanner UI for import shows live feed with progress counter and error recovery panel.

## 11. Security & Background Settings
- Section includes:
  - Toggle: â€œAllow shells to run in background.â€
  - Dropdown: â€œDefault per-connection behaviorâ€ (Inherit / Always allow / Always stop).
  - Toggle: â€œRequire biometric unlockâ€ with timeout selector (Immediate, 1 min, 5 min, 15 min, Custom). Shows preview of biometric prompt + fallback PIN flow.
  - Info copy about battery/network usage and privacy.
- Connection-specific override (background behavior) appears in Advanced tab as described above.

## 12. App Lock Screen
```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ SSHPeaches Lock â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚   (Peach logo)                                â”‚
â”‚   SSHPeaches is locked                        â”‚
â”‚   Last unlocked: 2m ago                       â”‚
â”‚                                               â”‚
â”‚ [Fingerprint icon] Touch to unlock            â”‚
â”‚ [PIN fallback button]                         â”‚
â”‚                                               â”‚
â”‚ â€œUse different accountâ€ / â€œSwitch identityâ€   â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```
- Triggered after inactivity timeout or manual lock.
- Shows branding, last-unlocked timestamp, and optional message if a session is running in background.
- Primary action: biometric prompt (fingerprint/face). If unavailable/fails, fallback button opens PIN/password entry sheet.
- Deviceâ€™s back button exits app (sessions paused until unlock). If background sessions allowed, notification indicates â€œLocked â€“ session runningâ€.
- Settings link (gear icon) appears if user needs to adjust timeout once authenticated.

---

### Next Steps
- Create Figma frames for each screen with the black-and-orange palette.
- Define shared components (cards, buttons, toggles) as reusable variants.
- Link frames to blueprint requirements and note interactions (Edit mode, Info editor, QR scanner).


