Run summary for this task.

Task:
Diagnose and fix this bug in the sshpeaches Android app: If an OpenSSH terminal session is open, the SSH connection closes in the background while the app is not foregrounded, and the app is later foregrounded via a notification, Activity Manager, launcher, or another resume path, it shows the SSH Terminal Open pane with a completely empty and unusable terminal because no backing terminal/session exists. The user currently has to press Back. On foreground/resume, automatically return to the previous screen before the stale terminal pane is drawn, with no fade, animation, brief flicker, or empty terminal frame. Inspect the existing architecture and lifecycle/navigation handling, implement the smallest robust fix across all relevant foreground entry paths, preserve valid live-session behavior, add or update regression tests, and run appropriate tests. Do not merely report a diagnosis: make the code changes and verify them.

Result:
{
  "status": "COMPLETED",
  "result": "Targeted verification completed. Compile and stale-session unit tests passed. Connected Android tests were attempted but could not run because no device/emulator was connected.",
  "implemented": [
    "task-1",
    "task-2",
    "task-3",
    "task-4",
    "task-5",
    "task-6",
    "task-7",
    "task-8",
    "task-9"
  ],
  "review_round": 1,
  "run_id": "20260713-194312-diagnose-and-fix-this-bug-in",
  "errors": null,
  "event_log": "/home/zenulabidin/Documents/sshpeaches/.stringbean/runs/20260713-194312-diagnose-and-fix-this-bug-in/events.jsonl"
}
