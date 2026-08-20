Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are the architecture advisor.
Review this plan and provide one structured response.

Task:
describe this project (what does it do?)

Plan:
{
  "summary": "SSHPeaches is an Android SSH client. It lets users connect to servers through SSH terminal sessions, manage saved hosts and SSH identities, transfer files with SFTP/SCP, configure local port forwards, store reusable command snippets, and share/import connection data through QR codes. The repository is a Kotlin/Gradle Android app with Compose UI, app state/view-model layers, SSH/session service code, terminal input routing, settings persistence, and Android/unit tests around live transport, connection UI, keyboard layout, SSH provider behavior, and service/input queue behavior.",
  "assumptions": [
    "The README is the primary source of truth for the user-facing project description.",
    "Current uncommitted changes may be part of ongoing work and should not be modified for a description-only task.",
    "No implementation changes are required unless the project description needs to be added to documentation or another artifact."
  ],
  "tasks": [
    {
      "id": "task-1",
      "title": "Review project documentation",
      "description": "Read README.md and the docs index/user wiki entries to confirm the user-facing purpose, supported features, target Android version, and support/security notes.",
      "dependencies": [],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Confirm README.md describes SSHPeaches as an Android SSH client.",
        "Confirm feature list includes SSH terminal sessions, SFTP, SCP, saved hosts, identities, port forwards, QR import/export, and snippets."
      ]
    },
    {
      "id": "task-2",
      "title": "Inspect application structure",
      "description": "Review the Gradle configuration and main app packages to summarize the technical shape of the project, including UI, state management, settings, SSH provider, session service, keyboard, and terminal routing components.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Identify the Android app module and Kotlin package namespace.",
        "Map key source folders to their responsibilities without changing files."
      ]
    },
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
    },
    {
      "id": "task-4",
      "title": "Produce project description",
      "description": "Write a concise project description covering what SSHPeaches does for users and how the codebase is organized technically.",
      "dependencies": [
        "task-1",
        "task-2",
        "task-3"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Description is accurate against repository documentation.",
        "Description distinguishes user-facing capabilities from inferred implementation details.",
        "No files are modified."
      ]
    }
  ],
  "risks": [
    "The working tree contains many modified and untracked files, so repository behavior may differ from the last committed version.",
    "The README may be more polished than the actual implemented feature set; code inspection should be used before claiming feature completeness.",
    "Live SSH/Mosh behavior may depend on external servers or credentials and cannot be fully verified from static inspection alone."
  ],
  "advisor_questions": [
    "Should the final description be user-facing, developer-facing, or both?",
    "Should this description be written into a specific file such as README.md, docs/README.md, or release notes?"
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
