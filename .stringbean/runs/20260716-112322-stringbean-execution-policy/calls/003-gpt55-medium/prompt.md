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

You are the orchestrator. Advisor requested changes.

Task:
Stringbean execution policy:
- Ordinary remote processing of task text and non-excluded, in-scope workspace context by Stringbean's configured hosted providers is inherent to this requested run; do not pause for separate provider-use approval.
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.
- Default workspace boundary: /home/zenulabidin/Documents/sshpeaches. Do not inspect unrelated parent or sibling paths. A path explicitly named by the user's task is in scope unless an excluded-path rule protects it.
- Ordered excluded-path rules (`!` means an allowed exception): .stringbean/runs, .stringbean/runs/**, **/.stringbean/runs, **/.stringbean/runs/**, .env*, **/.env*, !.env.example, !**/.env.example, !.env.sample, !**/.env.sample, !.env.template, !**/.env.template, .secrets, .secrets/**, **/.secrets, **/.secrets/**, secrets, secrets/**, **/secrets, **/secrets/**, credentials, credentials/**, **/credentials, **/credentials/**, credentials.json, **/credentials.json, service-account*.json, **/service-account*.json, *.pem, **/*.pem, *.key, **/*.key, *.p12, **/*.p12, *.pfx, **/*.pfx. Built-in credential exclusions are mandatory: a project or configured `!` rule can only re-include paths excluded by other project-added rules.
- Never read, list, search, summarize, modify, or transmit content from paths excluded by those rules.
- If an excluded path appears relevant, skip it and continue with the rest of the task. Do not retry access, ask the user for permission to inspect it, or ask another agent to inspect it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
Review only the existing Ctrl/IME changes using Opus 4.8; do not modify files.

Objective:
task-2 - Review Input Handling Semantics

Plan task:
{
  "id": "task-2",
  "title": "Review Input Handling Semantics",
  "description": "Analyze changed keyboard and IME code paths for correctness across hardware Ctrl combinations, soft keyboard composition, delete/backspace, enter, escape, meta-state handling, Unicode text input, and terminal control sequence emission.",
  "dependencies": [
    "task-1"
  ],
  "recommended_role": "reviewer",
  "permissions": "read_only",
  "verification": [
    "Check whether Ctrl combinations map to expected terminal control bytes.",
    "Check whether composed IME text is preserved and not double-sent.",
    "Check whether key down/up and repeat behavior avoid duplicate terminal input."
  ]
}

Required verification:
Check whether Ctrl combinations map to expected terminal control bytes.
Check whether composed IME text is preserved and not double-sent.
Check whether key down/up and repeat behavior avoid duplicate terminal input.

Known constraints:
Check whether Ctrl combinations map to expected terminal control bytes.
Check whether composed IME text is preserved and not double-sent.
Check whether key down/up and repeat behavior avoid duplicate terminal input.

Prior constraints/advisor notes:
reviewer

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

Objective:
task-2.1 - Identify changed input paths

Plan task:
{
  "id": "task-2.1",
  "title": "Identify changed input paths",
  "description": "Inspect git metadata and changed files from task-1 to locate keyboard event handling, IME input connection, terminal writer, and control-sequence emission code paths while respecting excluded paths.",
  "dependencies": [],
  "recommended_role": "reviewer",
  "permissions": "read_only",
  "verification": [
    "List only in-scope changed files relevant to Ctrl/IME behavior.",
    "Confirm no excluded or credential-like paths are read."
  ]
}

Required verification:
List only in-scope changed files relevant to Ctrl/IME behavior.
Confirm no excluded or credential-like paths are read.

Known constraints:
List only in-scope changed files relevant to Ctrl/IME behavior.
Confirm no excluded or credential-like paths are read.

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

Original plan:
{
  "summary": "Plan a read-only Opus 4.8 review of the existing Ctrl/IME changes from task-1, focused on identifying the changed input paths and validating terminal input semantics without modifying repository files.",
  "assumptions": [
    "task-1 changes are already present in the working tree or git metadata.",
    "The review can use read-only git inspection commands that do not invoke denied operations.",
    "Excluded paths and credential-like paths must be skipped entirely, including during search and changed-file enumeration."
  ],
  "tasks": [
    {
      "id": "task-2.1",
      "title": "Identify changed input paths",
      "description": "Inspect read-only git metadata and changed-file listings to locate task-1 changes related to keyboard event handling, IME input connection behavior, terminal writer paths, Unicode text handling, and control-sequence emission. Filter results to in-scope repository files only and skip all excluded or credential-like paths.",
      "dependencies": [],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "List only changed files relevant to Ctrl/IME behavior.",
        "Confirm no excluded path, credential path, key file, certificate file, or secret-like path was read, listed for content, searched, or summarized.",
        "Confirm no files were modified."
      ]
    },
    {
      "id": "task-2",
      "title": "Review input handling semantics",
      "description": "Review the changed Ctrl/IME code paths using Opus 4.8 for correctness across hardware Ctrl combinations, Android meta-state handling, soft keyboard composition, delete/backspace, enter, escape, Unicode text input, key down/up handling, repeat handling, and terminal control sequence emission. Produce findings only, with file and line references where applicable, and do not modify files.",
      "dependencies": [
        "task-2.1"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Check whether Ctrl combinations map to expected terminal control bytes, including Ctrl+A through Ctrl+Z, bracket/backslash/right-bracket/caret/underscore, and ambiguous platform meta-state cases.",
        "Check whether composed IME text is preserved and not double-sent through both commitText/composing paths and key event fallback paths.",
        "Check whether delete, backspace, enter, and escape produce expected terminal bytes or sequences for hardware and soft keyboard paths.",
        "Check whether key down/up and repeat behavior avoid duplicate terminal input, especially for printable keys, Ctrl-modified keys, and long-press repeat events.",
        "Check whether Unicode text input outside simple ASCII is forwarded without truncation, lossy keyCode conversion, or duplicate emission.",
        "Check whether terminal writer calls preserve ordering and do not mix text commits with control sequence writes incorrectly."
      ]
    }
  ],
  "risks": [
    "The relevant task-1 changes may be mixed with unrelated working-tree changes, so changed-file identification must avoid reviewing out-of-scope edits.",
    "Android keyboard behavior varies by hardware keyboard, soft keyboard, and IME, so the review should distinguish source-level confidence from cases that require device/emulator validation.",
    "Ctrl/meta handling can be affected by KeyEvent normalization and modifier masks, making regressions possible even when simple ASCII cases look correct."
  ],
  "advisor_questions": [
    "What is the intended terminal compatibility target for Ctrl mappings: POSIX control bytes only, xterm-style sequences for extended keys, or existing app behavior?",
    "Should the review treat any changed tests or fixtures from task-1 as authoritative expected behavior, or should it validate against terminal conventions independently?"
  ]
}

Advisor feedback:
{
  "verdict": "revise",
  "severity": "medium",
  "summary": "The plan is directionally sound for a read-only Ctrl/IME semantics review, but it needs tighter handling of excluded paths and clearer scoping around task-1 changes before approval.",
  "blockers": [],
  "concerns": [
    "Changed-file enumeration can itself violate the exclusion rules if a broad git command lists excluded or credential-like paths. The plan should require pathspec exclusions or another approach that avoids listing excluded paths at all.",
    "Allowed file scope is ambiguous: task-2 says allowed file scope is task-1, while task-2.1 has an empty allowed scope. The reviewer needs a concrete way to identify task-1 files without scanning unrelated changes.",
    "The instruction to use Opus 4.8 should be treated as an execution requirement for the downstream reviewer. If that model is unavailable, the task should stop or report the mismatch rather than silently using another model.",
    "The plan says no files are modified, which matches the effective read-only task, but the surrounding policy also says create-only. The stricter interpretation should be explicit: do not create, modify, delete, rename, or move repository paths."
  ],
  "recommendations": [
    "Revise task-2.1 to enumerate only known task-1 changed paths from an in-scope source, applying exclusion filters before any path is listed, searched, opened, or summarized.",
    "Define the exact read-only commands permitted for inspection, such as git diff/status variants with explicit excluded pathspecs, and prohibit broad filesystem search over the whole workspace.",
    "Add a verification item confirming that no repository files or new artifacts were created during the review.",
    "Keep the semantic review checks as written; they cover the important Ctrl mapping, IME composition, Unicode, key repeat, delete/backspace, enter, escape, and writer-ordering risks."
  ]
}

Revise the plan and output ONLY a JSON object in this schema:
{
  "summary": "...",
  "assumptions": [],
  "tasks": [],
  "risks": [],
  "advisor_questions": []
}
