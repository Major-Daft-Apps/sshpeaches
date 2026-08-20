Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_write.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
describe this project (what does it do?)

Objective:
task-3 - Inspect test coverage signals

Plan task:
{
  "id": "task-3",
  "title": "Inspect test coverage signals",
  "description": "Review unit and Android test names to describe which behaviors the project appears to validate, especially connection screens, live transport, SSH client provider, keyboard defaults, terminal input routing, and bounded mosh input queue behavior.",
  "dependencies": [
    "task-2"
  ],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "Confirm test files exist for core SSH/session, UI, keyboard, and terminal input concerns.",
    "Avoid drawing conclusions about passing status unless tests are actually run."
  ]
}

Required verification:
Confirm test files exist for core SSH/session, UI, keyboard, and terminal input concerns.
Avoid drawing conclusions about passing status unless tests are actually run.

Known constraints:
Confirm test files exist for core SSH/session, UI, keyboard, and terminal input concerns.
Avoid drawing conclusions about passing status unless tests are actually run.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-2

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
