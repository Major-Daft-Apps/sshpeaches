Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_write.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
inspect the codebase to see the description

Objective:
task-3 - Classify current worktree changes

Plan task:
{
  "id": "task-3",
  "title": "Classify current worktree changes",
  "description": "Inspect git status and relevant touched files without modifying anything. Group modified and untracked files by functional area, such as Mosh input buffering, terminal input routing, keyboard layout or icon behavior, settings/session UI, SSH provider behavior, and test coverage.",
  "dependencies": [
    "task-1",
    "task-2"
  ],
  "recommended_role": "advisor",
  "permissions": "read_only",
  "verification": [
    "List modified and untracked files by functional area.",
    "Explicitly note dirty worktree state.",
    "Identify which changes appear to be in-progress rather than committed product behavior.",
    "Do not treat untracked .stringbean, .codex, or generated summary files as committed project state."
  ]
}

Required verification:
List modified and untracked files by functional area.
Explicitly note dirty worktree state.
Identify which changes appear to be in-progress rather than committed product behavior.
Do not treat untracked .stringbean, .codex, or generated summary files as committed project state.

Known constraints:
List modified and untracked files by functional area.
Explicitly note dirty worktree state.
Identify which changes appear to be in-progress rather than committed product behavior.
Do not treat untracked .stringbean, .codex, or generated summary files as committed project state.

Prior constraints/advisor notes:
advisor

Allowed file scope:
task-1, task-2

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
