Run summary for this task.

Task:
Implement the saved plan in docs/plans/ctrl-ime-copy-paste-fix.md for the Ctrl/IME modifier-loss regression after terminal Copy or Paste. Work directly in the current repository and do not stop at analysis: make the implementation and test changes, then validate them. Follow the plan's regression-first phases, including reliable forced selection dismissal, IME bridge focus restoration without changing the user's keyboard visibility choice, modifier-complete unified Android key routing, standalone-emulator codepoint routing, and the focused exact-byte/selection/focus/router tests. Preserve all existing dirty-worktree changes and carefully integrate with overlapping edits; do not discard or overwrite unrelated user work. Keep the hidden-editor sentinel cleanup separate/optional if it raises patch risk. Run the strongest relevant focused tests available in this environment and report exact results, remaining limitations, modified artifacts, task count, and review rounds.

Result:
{
  "status": "FAILED",
  "result": "Implementation completed, but the Stringbean workflow failed during review/finalization because the selected reviewer model was unavailable. Task-7 behavior is implemented: TerminalView.inputCodePoint() computes effective Ctrl/Alt before the TerminalSession null fallback, offers codepoints to the client with a null session, returns when handled, and only requires TerminalSession for writeCodePoint fallback. Added focused standalone-emulator commitText(\"a\") instrumentation coverage.",
  "implemented": [
    "task-1",
    "task-2",
    "task-3",
    "task-4",
    "task-5",
    "task-6"
  ],
  "review_round": 0,
  "run_id": "20260715-194450-implement-the-saved-plan-in",
  "errors": "implementer incomplete: Connected instrumentation tests could not execute because no Android device/emulator is connected.; Stringbean workflow status is FAILED because reviewer claude-opus-4-8 exited with unavailable-model/access error.; Hidden-editor sentinel cleanup was left untouched.",
  "event_log": "/home/zenulabidin/Documents/sshpeaches/.stringbean/runs/20260715-194450-implement-the-saved-plan-in/events.jsonl"
}
