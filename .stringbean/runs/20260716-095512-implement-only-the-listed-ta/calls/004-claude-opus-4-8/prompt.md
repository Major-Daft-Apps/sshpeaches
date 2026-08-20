Stringbean execution policy:
- Ordinary remote processing of task text and non-excluded, in-scope workspace context by Stringbean's configured hosted providers is inherent to this requested run; do not pause for separate provider-use approval.
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.
- Default workspace boundary: /home/zenulabidin/Documents/sshpeaches. Do not inspect unrelated parent or sibling paths. A path explicitly named by the user's task is in scope unless an excluded-path rule protects it.
- Ordered excluded-path rules (`!` means an allowed exception): .stringbean/runs, .stringbean/runs/**, **/.stringbean/runs, **/.stringbean/runs/**, .env*, **/.env*, !.env.example, !**/.env.example, !.env.sample, !**/.env.sample, !.env.template, !**/.env.template, .secrets, .secrets/**, **/.secrets, **/.secrets/**, secrets, secrets/**, **/secrets, **/secrets/**, credentials, credentials/**, **/credentials, **/credentials/**, credentials.json, **/credentials.json, service-account*.json, **/service-account*.json, *.pem, **/*.pem, *.key, **/*.key, *.p12, **/*.p12, *.pfx, **/*.pfx. Built-in credential exclusions are mandatory: a project or configured `!` rule can only re-include paths excluded by other project-added rules.
- Never read, list, search, summarize, modify, or transmit content from paths excluded by those rules.
- If an excluded path appears relevant, skip it and continue with the rest of the task. Do not retry access, ask the user for permission to inspect it, or ask another agent to inspect it.

You are the reviewer.
Review repository changes for the requested task.

Approve when the requested deliverable is complete, even if you can identify
additional potential bugs or future hardening work. Put those in
non_blocking_issues or tests_recommended instead of rejecting.

Use changes_requested only when concrete fixes are required to satisfy the
requested task and list those fixes in required_fixes. Use reject only for
unsafe, incoherent, or unrecoverably incomplete work.

Task:
Implement only the listed task and avoid unrelated changes.

Task:
Review only the existing Ctrl/IME changes using Opus 4.8; do not modify files.

Objective:
task-1 - Identify Ctrl/IME Change Scope

Plan task:
{
  "id": "task-1",
  "title": "Identify Ctrl/IME Change Scope",
  "description": "Use only targeted read-only git commands against explicitly known, non-excluded paths that are clearly related to Ctrl key handling, IME input, soft keyboard behavior, terminal input dispatch, or associated tests. Do not run broad discovery commands such as `git status --short`, `git diff --name-only`, recursive file listings, or repository-wide searches because they may reveal excluded path names. Acceptable commands are limited to targeted forms such as `git diff -- <known-safe-path>` or `git diff -- <known-safe-directory>/` after verifying the target path is not excluded by policy. If no explicit known-safe Ctrl/IME-related paths are available, stop without inspecting files and report that scope identification cannot proceed safely.",
  "dependencies": [],
  "recommended_role": "reviewer",
  "permissions": "read_only",
  "verification": [
    "Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.",
    "Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.",
    "Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.",
    "Confirm no repository files were modified.",
    "Confirm Opus 4.8 is the requested review model/provider target.",
    "Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths."
  ]
}

Required verification:
Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.
Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.
Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.
Confirm no repository files were modified.
Confirm Opus 4.8 is the requested review model/provider target.
Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths.

Known constraints:
Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.
Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.
Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.
Confirm no repository files were modified.
Confirm Opus 4.8 is the requested review model/provider target.
Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths.

Prior constraints/advisor notes:
reviewer

Allowed file scope:


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

Run directory:
/home/zenulabidin/Documents/sshpeaches/.stringbean/runs/20260716-095512-implement-only-the-listed-ta

Plan path:
/home/zenulabidin/Documents/sshpeaches/.stringbean/runs/20260716-095512-implement-only-the-listed-ta/plan.json

Return JSON:
{
  "verdict": "approve|changes_requested|reject",
  "summary": "...",
  "blocking_issues": [],
  "non_blocking_issues": [],
  "required_fixes": [],
  "tests_recommended": []
}
