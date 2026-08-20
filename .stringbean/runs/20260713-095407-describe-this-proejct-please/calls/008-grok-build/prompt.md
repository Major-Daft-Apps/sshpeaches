Stringbean execution policy:
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
describe this proejct, please

Objective:
task-5 - Prepare concise project description

Plan task:
{
  "id": "task-5",
  "title": "Prepare concise project description",
  "description": "Produce a human-readable project description suitable for onboarding, with sections covering purpose, main capabilities, repository layout, architecture overview, tests/docs, license or project metadata if present, and current worktree caveat.",
  "dependencies": [
    "task-2",
    "task-3",
    "task-4"
  ],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "Description is clear for a new contributor.",
    "Description is concise enough to be useful in a README, handoff note, or project summary.",
    "Facts are traceable to repository files or explicitly framed as inferences.",
    "No file creation, edit, build, test, or other write-producing operation is performed."
  ]
}

Required verification:
Description is clear for a new contributor.
Description is concise enough to be useful in a README, handoff note, or project summary.
Facts are traceable to repository files or explicitly framed as inferences.
No file creation, edit, build, test, or other write-producing operation is performed.

Known constraints:
Description is clear for a new contributor.
Description is concise enough to be useful in a README, handoff note, or project summary.
Facts are traceable to repository files or explicitly framed as inferences.
No file creation, edit, build, test, or other write-producing operation is performed.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-2, task-3, task-4

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
