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

You are the architecture advisor.
Review this plan and provide one structured response.

Task:
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

Plan:
{
  "summary": "Read-only review plan for task-2: inspect the existing Ctrl/IME input handling changes, verify terminal byte emission semantics, IME composition behavior, and duplicate-input risks without modifying repository files.",
  "assumptions": [
    "The relevant Ctrl/IME changes are already present in the working tree from task-1.",
    "Review scope is limited to files changed by task-1 and related keyboard, IME, terminal input, and control-sequence paths.",
    "No repository files may be modified, renamed, deleted, or type-changed during this review."
  ],
  "tasks": [
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
    },
    {
      "id": "task-2.2",
      "title": "Review Ctrl mapping semantics",
      "description": "Analyze hardware Ctrl combinations, meta-state normalization, shifted/control variants, letter mappings, bracket/backslash/caret/underscore mappings, and interactions with Alt/Meta to ensure expected terminal control bytes are emitted.",
      "dependencies": [
        "task-2.1"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Check whether Ctrl+A through Ctrl+Z map to bytes 0x01 through 0x1A.",
        "Check whether Ctrl+[ emits ESC, Ctrl+\\ emits FS, Ctrl+] emits GS, Ctrl+^ emits RS, and Ctrl+_ emits US when supported by the implementation.",
        "Check whether non-text navigation keys still emit terminal escape sequences rather than Unicode text."
      ]
    },
    {
      "id": "task-2.3",
      "title": "Review IME composition semantics",
      "description": "Inspect soft keyboard input handling for commitText, setComposingText, finishComposingText, deleteSurroundingText, sendKeyEvent, enter, escape, and Unicode text input to verify composed text is preserved and sent exactly once.",
      "dependencies": [
        "task-2.1"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Check whether composing text is not prematurely written to the terminal before commit.",
        "Check whether committed Unicode text, including multibyte characters, is sent intact.",
        "Check whether IME-generated enter, delete/backspace, and escape paths produce expected terminal bytes or sequences."
      ]
    },
    {
      "id": "task-2.4",
      "title": "Review key repeat and up/down behavior",
      "description": "Analyze key down, key up, repeat count, long press, and IME sendKeyEvent flows to find duplicate terminal input, missed input, or conflicting handling between hardware and soft keyboard paths.",
      "dependencies": [
        "task-2.1"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Check whether key-up events avoid writing duplicate terminal input.",
        "Check whether repeated key-down events intentionally repeat only repeatable keys.",
        "Check whether IME sendKeyEvent does not duplicate commitText output for the same user action."
      ]
    },
    {
      "id": "task-2.5",
      "title": "Report findings",
      "description": "Produce a review summary with severity-ranked findings, file and line references where available, verification coverage, and residual risks. Do not patch files.",
      "dependencies": [
        "task-2.2",
        "task-2.3",
        "task-2.4"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Include whether all three required verification areas passed or failed.",
        "Include any remaining issues with actionable references.",
        "Confirm files_changed is empty."
      ]
    }
  ],
  "risks": [
    "Android IME behavior varies across keyboard apps, so static review may miss device-specific composition edge cases.",
    "Without running instrumentation tests on a device or emulator, repeat and meta-state behavior can only be verified by code inspection unless existing tests cover it.",
    "Terminal control sequence expectations may depend on the terminal library's established behavior, so local conventions should be compared before treating differences as defects."
  ],
  "advisor_questions": [
    "Which files or commit range correspond exactly to task-1 if git status alone is ambiguous?",
    "Should the review treat unsupported Ctrl punctuation mappings as defects or acceptable limitations?"
  ]
}

Return JSON:
{
  "verdict": "approve|revise|block",
  "severity": "none",
  "summary": "...",
  "blockers": [],
  "concerns": [],
  "recommendations": []
}
