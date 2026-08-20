# Ctrl/IME Modifier Loss After Copy or Paste

## Objective

Fix the terminal input regression where selecting Copy or Paste can cause an app-latched Ctrl modifier to be lost, so input such as Ctrl-A is sent as plain `a` (`0x61`) instead of Ctrl-A (`0x01`).

The implementation must preserve physical keyboard modifiers, software IME input, one-shot compact-key modifiers, terminal selection, bracketed paste, and the user's keyboard visibility choice.

## Current diagnosis

This is primarily a focus and input-routing defect, not a Ctrl encoding defect.

```text
Terminal Copy/Paste selection
  -> TerminalView requests focus
  -> hidden TerminalImeBridgeEditText loses focus
  -> selection completion does not restore the bridge
  -> an unmodified IME KEYCODE_A enters TerminalView.onKeyDown()
  -> TerminalViewClient consumes it through TerminalInputRouter
  -> pendingModifiers is never merged into the event
  -> 0x61 (plain a) is written instead of 0x01 (Ctrl-A)
```

### Primary evidence

- `third_party/termux-playstore/terminal-view/src/main/java/com/termux/view/TerminalView.java:1319` calls `requestFocus()` when text selection begins.
- `app/src/main/java/com/sshpeaches/app/ui/screens/ConnectingScreen.kt:669` implements `copyModeChanged()` as a no-op, so the separate hidden IME bridge is not refocused.
- `ConnectingScreen.kt:671` delegates `TerminalViewClient.onKeyDown()` directly to `TerminalInputRouter.onAndroidKeyDown()`.
- `app/src/main/java/com/sshpeaches/app/ui/terminal/TerminalInputRouter.kt:131` derives modifiers only from the Android event's `metaState`.
- Once that client returns `true`, `TerminalView` does not reach its own later `readControlKey()` merge.
- `TerminalInputRouter.normalizeCodePoint()` already converts Ctrl-A correctly when it actually receives `ctrlDown = true`.

### Contributing selection race

`third_party/termux-playstore/terminal-view/src/main/java/com/termux/view/TextSelectionCursorController.java:53` rejects every selection-dismiss request made within 300 ms of selection starting. Explicit Copy and Paste use that guarded dismissal, so a fast action can leave the selection handles, ActionMode, and focus state active.

### Standalone-emulator input gap

SSHPeaches attaches a standalone `TerminalEmulator`, which leaves `TerminalView.mTermSession` null. `TerminalView.inputCodePoint()` currently returns for a null session before calling `mClient.onCodePoint()`. If focus remains on `TerminalView`, IMEs using `commitText()` may therefore drop input instead of producing plain input.

### Likely provenance

- `05f8140` imported the Termux selection controller and its 300 ms guard.
- `86757f7` is the likely first exposing commit: it introduced the focusable `TerminalView`/separate input-editor arrangement, standalone emulator attachment, the consuming client key handler, and the no-op `copyModeChanged()` implementation.
- `adad146` replaced the Compose text field with the hidden sentinel EditText but retained modifier-incomplete raw key routing.
- `7199e24` expanded Paste/context-menu support and likely made the problem more visible, but it did not create the core focus/modifier mismatch.

## Existing workaround gap

The dirty worktree currently attempts to stop selection before handling a compact key and adds `compactModifierWorksAfterTerminalSelectionCopyAndPaste()`.

That is insufficient because:

- The stop request still obeys the 300 ms rejection guard.
- Stopping selection does not restore focus to the hidden IME bridge.
- The test sleeps for 350 ms, removing the timing race.
- It presses compact Ctrl followed by compact C, which bypasses the affected IME/Android key-event path.
- Its broad byte assertions can pass because of unrelated Copy/Paste payloads.

Preserve the existing dirty worktree, but replace or extend this test before treating the bug as fixed.

## Execution plan

### Phase 1: Lock in the failure with regression tests

Add focused instrumentation tests to:

`app/src/androidTest/java/com/sshpeaches/app/session_ui/ConnectingScreenTest.kt`

#### 1. Raw virtual-key path after terminal Copy

1. Configure a compact Ctrl key.
2. Start terminal selection and invoke Copy immediately, without sleeping.
3. Clear any setup bytes from the captured output sink.
4. Tap compact Ctrl.
5. Dispatch an unmodified virtual-keyboard `KEYCODE_A` through the `TerminalView` path.
6. Dispatch a second identical A.
7. Assert the exact ordered bytes are `[0x01, 0x61]`.

The current implementation is expected to produce `[0x61, 0x61]`; the first A loses Ctrl and the latch remains unused.

Suggested name:

```text
terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce
```

#### 2. Raw virtual-key path after terminal Paste

Repeat the preceding test with a seeded clipboard and the terminal selection Paste action. Clear the paste payload before asserting modifier bytes.

Suggested name:

```text
terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce
```

#### 3. Focus restoration contract

For Copy and Paste separately:

1. Show the system keyboard and assert `TerminalImeBridgeEditText.hasFocus()`.
2. Start selection and assert `TerminalView.hasFocus()`.
3. Invoke the action immediately.
4. Assert selection is inactive without a fixed sleep.
5. Wait until the hidden bridge has focus again.
6. Assert the keyboard-requested state remains unchanged.
7. Tap compact Ctrl and call `commitText("a", 1)` on the bridge connection.
8. Assert `0x01`, then commit another A and assert `0x61`.

#### 4. Selection-controller invariant

Add a direct test for the new forced-close API:

- An explicit Copy/Paste action must always close selection, even during the first 300 ms.
- The ordinary gesture-dismiss debounce should remain testable separately.
- Avoid fixed sleeps and private reflection when a package-visible test seam is practical.

#### 5. Literal IME context actions

Test these separately from terminal selection actions:

- `InputConnection.performContextMenuAction(android.R.id.paste)` followed by Ctrl + committed A.
- IME Copy using selected shadow text, followed by Ctrl + committed A.

This distinction matters because terminal ActionMode Copy/Paste and IME editor Copy/Paste have different code paths.

#### 6. Router baselines

Extend `app/src/test/java/com/sshpeaches/app/ui/terminal/TerminalInputRouterTest.kt` with exact-byte assertions:

- `sendText("a", ctrlDown = true)` produces `0x01`.
- Plain A produces `0x61`.
- If the router API accepts externally latched modifiers, an unmodified `KEYCODE_A` plus external Ctrl produces `0x01`.
- Navigation, Alt, Shift, `ACTION_MULTIPLE`, and paste-shortcut behavior remain unchanged.

### Phase 2: Make explicit selection dismissal reliable

In the vendored terminal-view module:

1. Add an idempotent forced-close path, such as `hide(force)` and `stopTextSelectionMode(force)`.
2. Bypass the 300 ms guard only for explicit user actions and real keyboard input.
3. Use forced close for Copy, Paste, More, and keyboard input that should leave selection mode.
4. Keep the existing debounce for the gesture scenario it was intended to protect.
5. Clear the stored ActionMode reference in `onDestroyActionMode()`.
6. Ensure client notification and invalidation occur exactly once.

Avoid solving this with `postDelayed(300)` or sleeps; those preserve a visible race and make tests flaky.

### Phase 3: Restore the intended IME focus owner

Implement `TerminalViewClient.copyModeChanged(copyMode)` in `ConnectingScreen.kt`.

When `copyMode` becomes false:

- If the system keyboard is supported and `keyboardVisibleRequested` is true, post a focus request back to `TerminalImeBridgeEditText` after ActionMode teardown.
- Preserve the current keyboard-requested state.
- Do not show the IME if the user had hidden it.
- Do not focus or show the bridge in built-in-keyboard mode.
- Guard against a detached or replaced bridge during session/recomposition changes.

### Phase 4: Unify modifier-complete Android key routing

Create one app-level Android terminal key dispatcher and use it from both:

- `TerminalViewClient.onKeyDown()`
- `TerminalImeBridgeEditText`'s `OnKeyListener`

The dispatcher should:

1. Snapshot `pendingModifiers`.
2. OR those values with the event's physical Ctrl/Alt/Shift metadata.
3. Route the effective modifiers through `TerminalInputRouter` without synthesizing a replacement `KeyEvent` when possible.
4. Clear app-latched modifiers only after a data-producing key was handled successfully.
5. Leave the latch intact for ignored system keys, Back, or unsuccessful routing.
6. Apply a consistent policy to known and unknown `ACTION_MULTIPLE` events.

Extend `TerminalInputRouter.onAndroidKeyDown()` with optional external modifier flags or a small modifier value object. Keep encoding and normalization inside the router.

### Phase 5: Complete standalone-emulator codepoint routing

Update `TerminalView.inputCodePoint()` so that it:

1. Computes effective Ctrl and Alt state.
2. Offers the codepoint to `mClient.onCodePoint()` even when `mTermSession` is null.
3. Returns if the client handled it.
4. Requires a real `TerminalSession` only for the default Termux `writeCodePoint()` fallback.

Add a focused terminal-view test proving that `commitText("a")` reaches the client when a standalone emulator is attached.

### Phase 6: Clean up the hidden-editor sentinel

The hidden editor uses a control character as a sentinel while the newer InputConnection also maintains a private shadow editable. Review whether the real EditText sentinel is still required.

Preferred outcomes, in order:

1. Remove the sentinel if the shadow connection fully owns composition and deletion.
2. Otherwise choose state that cannot collide with terminal control input and ensure Copy/Cut/Select-All never exposes it.

This cleanup is not required to prove the primary focus/routing fix, so keep it separate if it increases patch risk.

## Validation matrix

### Required input routes

- Compact Ctrl + IME `commitText("a")`
- Compact Ctrl + unmodified virtual `KEYCODE_A`
- Physical Ctrl+A using `META_CTRL_ON`
- Compact Alt and Shift equivalents
- Known and unknown `ACTION_MULTIPLE`
- Ctrl+Shift+V, Shift+Insert, and `KEYCODE_PASTE`

### Required Copy/Paste surfaces

- Terminal floating ActionMode Copy
- Terminal floating ActionMode Paste
- Hidden IME editor Copy
- Hidden IME editor Paste and Paste-as-plain-text

### Timing

- Immediate action inside 300 ms
- Delayed action after 350 ms as a control

### Keyboard state

- System keyboard visible: bridge focus must be restored
- System keyboard hidden: the fix must not summon it
- Built-in keyboard mode: no hidden-bridge focus request
- Hardware keyboard attached

### Platforms

- API 28 Google APIs
- API 34 phone and tablet managed devices
- API 36 with Gboard/Play image
- Samsung device or stock Samsung keyboard where available
- Optional Chromebook/hardware-keyboard smoke test

### Transport

Use the direct byte sink for authoritative automated assertions. SSH and Mosh are downstream of the input router and should not be required for the regression tests. Add one live SSH smoke test only after deterministic local tests pass.

## Validation commands

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.majordaftapps.sshpeaches.app.ui.terminal.TerminalInputRouterTest'

./gradlew :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce'

./gradlew :app:pixel2Api34DebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'

./gradlew :app:nexus9Api34DebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'
```

Run the full connected suite after the focused tests pass:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Manual byte-level confirmation

1. Open an SSH terminal with the Android keyboard visible.
2. Long-press output and quickly choose Copy; repeat separately with Paste.
3. Tap the app Ctrl key, then type `a` through the IME.
4. Capture one byte remotely in raw mode and inspect it as hexadecimal.
5. Expected fixed result: `01`. Broken result: `61`.
6. Type a second unmodified A and confirm `61`, proving the latch was consumed once.

When diagnosing focus, capture `dumpsys input_method` before selection, during selection, and after the action. When the keyboard was requested, the fixed served view should return to `TerminalImeBridgeEditText`.

## Patch strategy and tradeoffs

### Recommended patch

Implement Phases 1 through 5 together as a defense-in-depth fix:

- Forced explicit selection closure fixes the selection lifecycle.
- Focus restoration preserves the intended primary IME route.
- Modifier-complete key routing makes focus races harmless.
- Standalone codepoint support covers IMEs that use `commitText()` on `TerminalView`.

### Smaller patch

Forced closure plus focus restoration may resolve the common reproduction, but it leaves raw key-event modifier asymmetry and standalone codepoint loss in place. It is lower-diff but not considered complete.

### Larger redesign

Using one input-owning terminal view would eliminate split focus ownership, but it is substantially riskier and should be a separate project after this regression is fixed and covered.

## Definition of done

- Fast and delayed terminal Copy/Paste both end selection deterministically.
- The hidden IME bridge regains focus only when the system keyboard was already requested.
- After either Copy/Paste surface, compact Ctrl + A sends exactly `0x01`.
- The following plain A sends `0x61`, proving one-shot consumption.
- Physical Ctrl, Alt, Shift, navigation keys, multiline/composed text, and bracketed paste retain their existing behavior.
- Standalone-emulator `commitText()` reaches the app client instead of being dropped.
- Focused unit and instrumentation tests pass on the phone and tablet API 34 lanes.
- At least one Gboard and one Samsung/AOSP manual pass succeeds.
- Existing unrelated dirty-worktree changes remain intact.

## Investigation status

This plan is based on static code tracing, local git history, and three independent read-only investigation passes. No Android device was connected during the investigation. A targeted existing unit-test invocation stalled during Gradle daemon startup and was interrupted; it did not cover the reported path in any case.

No production files were changed as part of the investigation.
