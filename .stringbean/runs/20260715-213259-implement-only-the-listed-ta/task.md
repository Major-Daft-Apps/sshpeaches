Implement only the listed task and avoid unrelated changes.

Task:
Implement the saved plan in docs/plans/ctrl-ime-copy-paste-fix.md for the Ctrl/IME modifier-loss regression after terminal Copy or Paste. Work directly in the current repository and do not stop at analysis: make the implementation and test changes, then validate them. Follow the plan's regression-first phases, including reliable forced selection dismissal, IME bridge focus restoration without changing the user's keyboard visibility choice, modifier-complete unified Android key routing, standalone-emulator codepoint routing, and the focused exact-byte/selection/focus/router tests. Preserve all existing dirty-worktree changes and carefully integrate with overlapping edits; do not discard or overwrite unrelated user work. Keep the hidden-editor sentinel cleanup separate/optional if it raises patch risk. Run the strongest relevant focused tests available in this environment and report exact results, remaining limitations, modified artifacts, task count, and review rounds.

Objective:
task-7 - Route standalone-emulator codepoints to the client

Plan task:
{
  "id": "task-7",
  "title": "Route standalone-emulator codepoints to the client",
  "description": "Update third_party/termux-playstore/terminal-view/src/main/java/com/termux/view/TerminalView.java inputCodePoint() so it computes effective Ctrl and Alt state before the TerminalSession null check, offers the codepoint to mClient.onCodePoint() even when mTermSession is null, returns when the client handles it, and only requires a real TerminalSession for the default Termux writeCodePoint fallback. Add a focused terminal-view or instrumentation test proving commitText(\"a\") reaches the client with a standalone TerminalEmulator attached and no TerminalSession.",
  "dependencies": [
    "task-6"
  ],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "Run the closest focused terminal-view test task available.",
    "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'"
  ]
}

Required verification:
Run the closest focused terminal-view test task available.
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'

Known constraints:
Run the closest focused terminal-view test task available.
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-6

Return JSON:
{
  "status": "completed",
  "summary": "...",
  "files_changed": [],
  "commands_run": [],
  "tests": [],
  "remaining_issues": [],
  "handoff_notes": []
}