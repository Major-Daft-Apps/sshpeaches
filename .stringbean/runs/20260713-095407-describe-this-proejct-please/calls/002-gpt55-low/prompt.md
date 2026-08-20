Stringbean execution policy:
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are the architecture advisor.
Review this plan and provide one structured response.

Task:
describe this proejct, please

Plan:
{
  "summary": "SSHPeaches is an Android SSH client project, implemented with Gradle/Kotlin, for connecting to servers and managing SSH workflows. The app supports terminal SSH sessions, saved hosts, SSH identities and private keys, SFTP/SCP file transfer, local port forwards, QR-based import/export, reusable command snippets, settings, and user documentation. The current repository appears to be mid-change, with modified app code, tests, Gradle configuration, changelog entries, and several new files related to Mosh input queueing and keyboard icon tests.",
  "assumptions": [
    "The request is informational: describe the project and propose a plan for producing a fuller project description, not to modify repository files during this read-only run.",
    "Existing modified and untracked files belong to prior work and should not be changed, reverted, moved, or deleted.",
    "The project is an Android application targeting at least Android 8.0 API 26, based on the README.",
    "A future implementation pass may create documentation artifacts if requested, but this run should only return the plan."
  ],
  "tasks": [
    {
      "id": "task-1",
      "title": "Inventory repository structure",
      "description": "Map the top-level Gradle project, Android app module, source sets, documentation folders, test folders, and configuration files to understand the project layout and ownership boundaries.",
      "dependencies": [],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Confirm root Gradle files and app module layout are identified.",
        "Confirm docs, Android source, unit tests, and instrumentation tests are separately described."
      ]
    },
    {
      "id": "task-2",
      "title": "Summarize product purpose and user-facing capabilities",
      "description": "Use README and documentation index content to describe what SSHPeaches does for end users: SSH terminal sessions, saved hosts, key management, file transfers, port forwards, snippets, settings, and help resources.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Summary matches README claims.",
        "No unsupported features are invented."
      ]
    },
    {
      "id": "task-3",
      "title": "Summarize technical architecture",
      "description": "Review app source package names and major files to describe the likely architecture: Android app entry point, UI screens, ViewModel/state layer, settings persistence, SSH client provider, session service, keyboard input routing, and test utilities.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Architecture summary references real repository paths.",
        "Description distinguishes confirmed facts from inferred architecture."
      ]
    },
    {
      "id": "task-4",
      "title": "Summarize current work-in-progress state",
      "description": "Use git status to identify the active change areas without modifying them. Highlight that the worktree contains changes across session service, SSH client provider, settings, UI screens, keyboard layout/icon handling, terminal input routing, tests, and new bounded Mosh input queue files.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Every major modified/untracked area from git status is represented.",
        "No claim is made that changes are complete or tested unless verified."
      ]
    },
    {
      "id": "task-5",
      "title": "Prepare concise project description",
      "description": "Produce a human-readable project description suitable for onboarding, including purpose, main features, repository layout, architecture overview, testing surface, documentation, license, and current change context.",
      "dependencies": [
        "task-2",
        "task-3",
        "task-4"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Description is clear for a new contributor.",
        "Description is concise enough to be useful in README, handoff notes, or project summary.",
        "Facts are traceable to repository files or provided context."
      ]
    },
    {
      "id": "task-6",
      "title": "Optionally create documentation artifact",
      "description": "If a future write-enabled run is approved, create a new documentation file such as docs/project-overview.md containing the finalized project description. Because this run is create-only/read-only, do not modify existing README or docs files.",
      "dependencies": [
        "task-5"
      ],
      "recommended_role": "implementer",
      "permissions": "read_write",
      "verification": [
        "Only a new file is created.",
        "No existing repository paths are modified, deleted, renamed, moved, or type-changed.",
        "Generated documentation matches the reviewed repository state."
      ]
    }
  ],
  "risks": [
    "README may be more complete than the current implementation, so feature descriptions should be framed as project goals or documented capabilities unless source verification confirms them.",
    "The worktree is dirty, so current behavior may differ from the last committed state.",
    "Untracked .codex and .stringbean directories may be tooling state and should not be treated as product source without further inspection.",
    "A read-only execution policy prevents validating the project through builds or tests if they would write generated outputs."
  ],
  "advisor_questions": [
    "Should the final project description be optimized for users, contributors, app store metadata, or technical onboarding?",
    "Do you want the description to cover the current dirty worktree, or only the stable committed project?",
    "If documentation creation is later allowed, should it be a new standalone file under docs/ or a proposed replacement section for README?"
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
