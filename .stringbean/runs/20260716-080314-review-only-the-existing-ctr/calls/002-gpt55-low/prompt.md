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
Review only the existing Ctrl/IME changes using Opus 4.8; do not modify files.

Plan:
{
  "summary": "Review the existing Ctrl/IME changes with Opus 4.8 in read-only mode, producing findings only and making no repository modifications.",
  "assumptions": [
    "The Ctrl/IME changes are already present in the working tree.",
    "Review scope is limited to the current uncommitted or changed Ctrl/IME-related files.",
    "No files may be modified, moved, deleted, renamed, or type-changed.",
    "Excluded paths and credential-like files must not be read or summarized.",
    "The review should prioritize behavioral regressions, Android IME correctness, terminal keyboard handling, and missing tests."
  ],
  "tasks": [
    {
      "id": "task-1",
      "title": "Identify Ctrl/IME Change Scope",
      "description": "Inspect git status and diffs in read-only mode to locate files and hunks related to Ctrl key handling, IME input, soft keyboard behavior, terminal input dispatch, and associated tests. Skip excluded paths.",
      "dependencies": [],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Confirm the reviewed file list is limited to Ctrl/IME-related changes.",
        "Confirm no excluded paths were inspected."
      ]
    },
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
    },
    {
      "id": "task-3",
      "title": "Review Android IME Lifecycle Behavior",
      "description": "Examine interactions with InputConnection, EditorInfo, composing text, selection, cursor updates, show/hide keyboard behavior, and focus changes to identify Android-version or keyboard-app compatibility issues.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Check behavior assumptions against Android IME contracts.",
        "Identify risks for Gboard, Samsung Keyboard, Hacker's Keyboard, and physical keyboard scenarios where applicable."
      ]
    },
    {
      "id": "task-4",
      "title": "Review Regression Surface",
      "description": "Trace call sites and affected terminal/session components to find regressions in paste, snippets, shortcuts, modifier keys, accessibility input, SFTP/SCP screens if relevant, and saved terminal preferences.",
      "dependencies": [
        "task-1",
        "task-2",
        "task-3"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Confirm whether changed APIs are used outside the terminal screen.",
        "Flag any behavior change that lacks an explicit migration or compatibility path."
      ]
    },
    {
      "id": "task-5",
      "title": "Evaluate Tests",
      "description": "Inspect existing or changed tests covering Ctrl and IME behavior. Identify missing coverage for high-risk paths without adding or editing tests.",
      "dependencies": [
        "task-2",
        "task-3",
        "task-4"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "List tests reviewed.",
        "Call out missing tests for any confirmed or likely bug."
      ]
    },
    {
      "id": "task-6",
      "title": "Produce Review Findings",
      "description": "Return a code-review-style report ordered by severity. Include file and line references, concrete reproduction or reasoning, expected versus actual behavior, and test gaps. If no issues are found, state that clearly with residual risks.",
      "dependencies": [
        "task-5"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Report contains findings first, then open questions, then brief summary.",
        "No file modifications were made.",
        "Findings are grounded in specific changed lines."
      ]
    }
  ],
  "risks": [
    "The phrase 'existing Ctrl/IME changes' may map to multiple unrelated changed files unless the diff is carefully scoped.",
    "IME behavior varies significantly across Android versions and keyboard apps, so static review may miss device-specific issues.",
    "Read-only execution prevents adding targeted regression tests, so verification is limited to inspection and any already-available read-only test metadata.",
    "Large working-tree changes may contain unrelated edits that should not be reviewed or reported unless they affect Ctrl/IME behavior."
  ],
  "advisor_questions": [
    "Which changed files or commits define the intended Ctrl/IME review scope, if it is narrower than all current working-tree changes?",
    "Are there specific devices, Android versions, or keyboard apps where the Ctrl/IME issue was observed?",
    "Should the review include test execution if it can be done without modifying tracked files, or should it remain inspection-only?"
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
