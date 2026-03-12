# Google Play Publish Checklist (SSHPeaches)

Last updated: 2026-03-11

## Status Legend
- `DONE`: Completed and verified
- `PARTIAL`: Started, but not fully ready
- `BLOCKED`: Cannot proceed until blocker is fixed
- `TODO`: Not done yet
- `N/A`: Not applicable (confirm before publish)

## Readiness Snapshot
- Engineering/build readiness: `~65%`
- Play Console/policy/listing readiness: `~25%`
- Overall first-publish readiness: `~40%`

## Tracked Checklist
| ID | Area | Checklist Item | Owner | Status | Evidence | Next Action |
|---|---|---|---|---|---|---|
| GP-01 | Build | Target API level meets Play requirement | Engineering | DONE | `app/build.gradle.kts` (`targetSdk = 36`) | Keep target API current each cycle |
| GP-02 | Build | App ID and versioning set | Engineering | DONE | `applicationId`, `versionCode`, `versionName` in `app/build.gradle.kts` | Increment `versionCode` per release |
| GP-03 | Build | Produce release AAB (`:app:bundleRelease`) | Engineering | DONE | `app\\release\\app-release.aab` exists | Verify signature + upload to internal test track |
| GP-04 | Build | Release signing/upload key configured | Engineering | PARTIAL | `.keystore\\sshpeaches` exists; signing config wired in `app/build.gradle.kts` | Set `SSHPEACHES_*` keystore properties and verify signed AAB |
| GP-05 | Build | Manifest export rules valid | Engineering | DONE | `MainActivity exported=true`, `SessionService exported=false` in `app/src/main/AndroidManifest.xml` | Re-check after manifest changes |
| GP-06 | Permissions | Runtime notification permission flow present | Engineering | DONE | Request logic in `app/src/main/java/com/sshpeaches/app/MainActivity.kt` | Keep Android version guards intact |
| GP-07 | Permissions | Permission set is least-privilege and justified | Engineering | PARTIAL | AD ID + AdServices permissions removed in `app/src/main/AndroidManifest.xml` | Re-validate remaining permissions before release cut |
| GP-08 | Privacy | Privacy policy URL exists in app | Policy | DONE | `privacy_policy_url` in `app/src/main/res/values/strings.xml` | Ensure Play listing uses same URL |
| GP-09 | Privacy | Telemetry defaults to opt-in | Engineering | DONE | Defaults false in `SettingsStore`; gated init in release telemetry | Keep defaults false unless policy changes |
| GP-10 | Privacy | Data safety form completed in Play Console | Policy | TODO | Console-only | Fill data types, handling, sharing, and security sections |
| GP-11 | Privacy | Ads declaration completed | Policy | TODO | Console-only; Firebase Analytics present | Set Ads declaration accurately in App content |
| GP-12 | Privacy | App access declaration completed | Policy | TODO | Console-only | Declare that app is fully usable without account, if true |
| GP-13 | Policy | Content rating questionnaire completed | Policy | TODO | Console-only | Complete questionnaire and review result |
| GP-14 | Policy | Target audience & content completed | Policy | TODO | Console-only | Complete target age group and related declarations |
| GP-15 | Policy | Account deletion requirement review | Policy | N/A | No user account system evident in code | Confirm no account creation exists; otherwise add deletion flow/URL |
| GP-16 | Quality | Unit tests green before release | Engineering | DONE | `:app:testDebugUnitTest` passed (22 tests) | Keep tests green in CI for release branch |
| GP-17 | Quality | Instrumented UI smoke tests run on release candidate | Engineering | PARTIAL | Tests exist under `app/src/androidTest` | Run on managed/physical devices before publish |
| GP-18 | Quality | Lint/pre-launch issues resolved | Engineering | TODO | Not fully validated in this pass | Run `lint` + Play pre-launch report and fix findings |
| GP-19 | Listing | Store listing text prepared (title, short/full desc) | Marketing | TODO | Not tracked in repo | Draft copy and review for policy-safe claims |
| GP-20 | Listing | Required screenshots and graphics prepared | Marketing | TODO | Some assets exist; Play set not finalized | Produce final phone screenshots + icon/feature graphics |
| GP-21 | Listing | Support contact details configured in Play Console | Marketing | TODO | URL strings exist in app | Add support email, website, and policy URL in listing |
| GP-22 | Release | Testing track strategy set (internal/closed/open/prod) | Release | TODO | Console-only | Choose rollout path and configure testers |
| GP-23 | Release | Personal-account testing requirement checked | Release | TODO | Console-only | Confirm whether mandatory closed testing applies to this account |
| GP-24 | Release | Country availability and pricing configured | Release | TODO | Console-only | Configure availability and pricing before submit |
| GP-25 | Release | Release notes + staged rollout plan ready | Release | TODO | Not tracked in repo | Prepare notes and set initial rollout percentage |
| GP-26 | Ops | Post-release monitoring owner assigned | Engineering | PARTIAL | Crashlytics wired for release | Define on-call owner and response thresholds |

## Priority Next Actions
1. Configure release signing/upload key and verify Play App Signing workflow.
2. Make sure the release `.aab` is signed and uploadable (current path: `app\\release\\app-release.aab`).
3. Complete Play Console App content: Data safety, Ads, App access, Content rating, Target audience.
4. Finalize listing assets/text and support contact details.
5. Run release-candidate validation: instrumented tests, lint, pre-launch report.
