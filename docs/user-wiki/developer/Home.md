# SSHPeaches Developer Wiki

This wiki is for contributors and maintainers working on the app, product direction, and release process.

## Core Documents

- [Product Blueprint](Product-Blueprint.md) - product scope, roadmap, and feature expectations
- [UI Design Notes](UI-Design-Notes.md) - screen layouts, interaction notes, and visual direction
- [Google Play Publish Checklist](Google-Play-Publish-Checklist.md) - release readiness, code-submission gate, and publish tasks

## Intended Use

Use this wiki for implementation guidance, design alignment, and release operations. End-user help belongs in the user wiki instead of this section.

## Submission Workflow

Before submitting changes for review, use the release checklist as the source of truth for required validation:

- `:app:connectedDebugAndroidTest`
- `:app:liveAndroidTest`
- release-lane and benchmark tasks when the change touches release-sensitive or performance-sensitive areas

Before cutting a release candidate, run the full submission gate documented in [Google Play Publish Checklist](Google-Play-Publish-Checklist.md).
