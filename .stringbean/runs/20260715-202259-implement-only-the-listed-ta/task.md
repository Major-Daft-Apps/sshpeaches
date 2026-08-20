Implement only the listed task and avoid unrelated changes.

Task:
Implement the saved plan in docs/plans/ctrl-ime-copy-paste-fix.md for the Ctrl/IME modifier-loss regression after terminal Copy or Paste. Work directly in the current repository and do not stop at analysis: make the implementation and test changes, then validate them. Follow the plan's regression-first phases, including reliable forced selection dismissal, IME bridge focus restoration without changing the user's keyboard visibility choice, modifier-complete unified Android key routing, standalone-emulator codepoint routing, and the focused exact-byte/selection/focus/router tests. Preserve all existing dirty-worktree changes and carefully integrate with overlapping edits; do not discard or overwrite unrelated user work. Keep the hidden-editor sentinel cleanup separate/optional if it raises patch risk. Run the strongest relevant focused tests available in this environment and report exact results, remaining limitations, modified artifacts, task count, and review rounds.

Objective:
task-2 - Add focus restoration and IME context-action instrumentation tests

Plan task:
{
  "id": "task-2",
  "title": "Add focus restoration and IME context-action instrumentation tests",
  "description": "Add Copy and Paste focus contract tests in ConnectingScreenTest.kt. The tests should show the system keyboard, assert TerminalImeBridgeEditText focus, start selection and assert TerminalView focus, invoke the action immediately, wait for selection to become inactive without fixed sleeps, wait for the hidden bridge to regain focus, assert keyboardVisibleRequested semantics did not change, then verify compact Ctrl plus commitText(\"a\", 1) sends 0x01 and a second committed A sends 0x61. Add separate tests for InputConnection.performContextMenuAction(android.R.id.paste), paste-as-plain-text where supported, and IME copy using selected shadow text followed by Ctrl plus committed A.",
  "dependencies": [
    "task-1"
  ],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'",
    "Assertions must avoid fixed timing sleeps and use Compose/test waitUntil style conditions for selection inactive and bridge focus restored."
  ]
}

Required verification:
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'
Assertions must avoid fixed timing sleeps and use Compose/test waitUntil style conditions for selection inactive and bridge focus restored.

Known constraints:
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'
Assertions must avoid fixed timing sleeps and use Compose/test waitUntil style conditions for selection inactive and bridge focus restored.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-1

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