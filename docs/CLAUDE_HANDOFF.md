# Guardian Link — engineering and test handoff

## Purpose of this handoff

This repository has had substantial feature work, but reports from real use show that some cross-device flows still fail. This handoff deliberately separates what is proven from what is only implemented in code. The immediate objective is **coherency and evidence**, not another round of UI additions.

## Current baseline

| Item | Status |
| --- | --- |
| Source build | Passes locally with JDK 17, Android API 34, Gradle offline |
| Current Android version | `0.6.4` / code `22` |
| Debug APK path | `app/build/outputs/apk/debug/app-debug.apk` |
| Parent/child physical end-to-end test | Blocked: no ADB devices connected |
| Emulator end-to-end test | Blocked: no installed Android system image |
| New test-account creation | Blocked during last attempt by Supabase email rate limiting |
| Supabase schema/function inspection | Performed read-only; relevant tables/functions exist |
| Chat/inbox synchronization | **Unverified and reported broken by user** |

Never convert a blocked or unverified item to “working” without recorded two-device evidence.

## Security and data handling

This repository intentionally excludes all personal cloud data. Do not add real user credentials, tokens, family IDs, APK signing keys, database data exports, Supabase URLs, or API keys to Git.

To use the existing target environment, an authorized operator must securely provide a local `local.properties` file derived from `local.properties.example`. Use a publishable/anon client key only; never place a service-role key in the Android app. This is not a missing project artifact—it is an essential client-security boundary.

## Project architecture

```
MainActivity
  ├─ ParentModeActivity (parent authentication, child selector, controls)
  │    ├─ ParentApi (parent-authenticated Supabase REST reads/writes)
  │    ├─ ManageApps / TimeRequests / LocationLog / LiveLocation
  │    ├─ QuickMessages / FamilyChat / Notifications
  │    └─ pairing Edge Function
  └─ ChildModeActivity (child pairing and child experience)
       ├─ DeviceSessionStore + SupabaseApi (child-authenticated REST)
       ├─ PolicySynchronizer (poll/download policies and commands)
       ├─ Protection/Usage/Location/SOS foreground services
       └─ QuickMessages / FamilyChat / Notifications

Supabase
  ├─ Auth: distinct parent and child identities
  ├─ public.devices: links child device + auth identity + family
  ├─ family_chat_messages / family_notifications: bidirectional communication
  ├─ child_policies, commands, receipts, installed/reporting tables
  └─ Edge Functions: create-pairing, claim-child-device, retire-device
```

Key source entry points:

- `app/src/main/java/com/guardianlink/MainActivity.kt` — initial routing, sign-in, registration and guest mode.
- `app/src/main/java/com/guardianlink/ui/ParentModeActivity.kt` — dashboard, profile selection, pause/schedules/app-management launches.
- `app/src/main/java/com/guardianlink/ui/ChildModeActivity.kt` — pairing/setup, child actions and permission guidance.
- `app/src/main/java/com/guardianlink/sync/ParentApi.kt` — parent’s device, policy, chat and inbox transport.
- `app/src/main/java/com/guardianlink/sync/SupabaseApi.kt` — child-side transport and session refresh.
- `app/src/main/java/com/guardianlink/ui/FamilyChatActivity.kt` and `QuickMessagesActivity.kt` — free-text, fixed messages and voice attachments.
- `app/src/main/java/com/guardianlink/ui/NotificationsActivity.kt` — parent’s family-wide inbox and child’s device-scoped inbox.
- `app/src/main/java/com/guardianlink/enforcement/` — protection, usage, SOS and location services.

## Recent, relevant implementation state

The working tree includes these changes and must be reviewed as a unit:

1. Parent device queries now exclude unpaired/retired records and de-duplicate visible child names. Devices are ordered by `last_seen_at`.
2. The child updates `last_seen_at` when the shared conversation is resumed.
3. Parent notifications load by `family_id`, not only the selected child, and each item retains the originating child-device ID for the reply destination.
4. The new `20260830_harden_family_delivery.sql` replaces weak chat/notification RLS write policies with checks that tie `device_id`, `family_id`, and the authenticated parent/child identity together.
5. The source builds after these changes; that is a compilation result, not an end-to-end pass.

## Supabase deployment state and safe procedure

The current target project was previously updated manually in the Supabase SQL Editor. Its migration-history table does not safely support a blind `supabase db push --linked --include-all`: current date-based files cause a conflicting migration-version error. Treat migration-history repair as an isolated database-maintenance operation with a backup and review.

Until then, apply SQL manually, in this exact order, only after checking whether the object/policy is already present:

1. `supabase/schema.sql`
2. `supabase/migrations/20260828_parent_dashboard.sql`
3. `supabase/migrations/20260828_sos_alert.sql`
4. `supabase/migrations/20260828_reported_apps.sql`
5. `supabase/migrations/20260829_family_quick_messages.sql`
6. `supabase/migrations/20260829_professional_controls.sql`
7. `supabase/migrations/20260829_sync_integrity.sql`
8. `supabase/migrations/20260829_family_chat_voice.sql`
9. `supabase/migrations/20260829_category_safety_events.sql`
10. `supabase/migrations/20260829_command_delivery_queue.sql`
11. `supabase/migrations/20260829_unified_family_communication.sql`
12. `supabase/migrations/20260829_harden_command_receipts.sql`
13. `supabase/migrations/20260830_family_notifications.sql`
14. `supabase/migrations/20260830_harden_family_delivery.sql`

The first three Edge Functions were previously deployed. Verify their deployed versions and logs before redeploying; configuration and secrets must remain in Supabase, not source control.

## Required test environment

Use two real Android devices, or two independently running API 34 AVDs. One installation must be a fresh parent session and the other a fresh child session paired to it. A single device cannot prove receiver behavior.

Prerequisites:

- Both APKs are built from the same commit and show the same version code.
- Each device uses a different Supabase-authenticated user as intended by the pairing flow.
- Auth email confirmation/rate limits permit test-account setup, or pre-created test accounts are securely supplied.
- The parent and child have network access and all requested permissions are granted deliberately.
- The relevant Supabase migrations, especially the delivery hardening migration, have run successfully.

## Acceptance matrix — execute before further feature expansion

For every row, capture: timestamps, sender identity/device ID, database row or server response, receiver query result, receiver UI screenshot, and Android logcat around the action. A pass requires every listed stage; otherwise mark fail and identify the first divergence.

| ID | Flow | Expected result |
| --- | --- | --- |
| P01 | Generate pairing code on parent | Visible, copyable, time-limited code; one active code is clearly indicated |
| P02 | Pair child twice before expiry | First succeeds; second says already paired or opens existing pairing state—never a misleading generic failure |
| P03 | Parent app inventory refresh | Child sends installed apps; parent sees them under the correct selected child without duplicate child cards |
| P04 | Pause/resume all apps | Parent action persists remotely; child downloads it, visibly applies it, and resumes correctly |
| P05 | Selected-app block/unblock | Parent changes the selected app; child enforcement and parent status agree after sync |
| P06 | New-app approval | Child reports an install; parent sees app name and can allow/block; child outcome is visible |
| P07 | Screen-time/schedule changes | Parent sets time rule; child applies it, including offline behavior after one successful sync |
| P08 | Parent → child chat | Row contains parent sender + selected device/family; child sees exactly one new message, not the sender's local echo alone |
| P09 | Child → parent chat | Row contains child sender + device/family; parent sees it in family-wide inbox and the correct child conversation |
| P10 | Fixed quick messages, both directions | Same as P08/P09, with a stable template key and no duplicated local-only messages |
| P11 | Voice message, both directions | Attachment upload/URL, permissions, message row and playback all succeed or a clear failure state is shown |
| P12 | Notification center | Sender action makes a recipient-facing inbox entry; parent is family-wide, child is device-scoped; opening routes to the right conversation |
| P13 | SOS | Child event creates an urgent parent inbox/alarm with device attribution; acknowledgement state syncs back cleanly |
| P14 | Location and safe place | Child posts opt-in location; parent latest-location card/map/log update; safe-place enter/exit logic is attributable and non-duplicative |
| P15 | Safety categories/keywords | Browser input is evaluated locally and gives an understandable block reason; parent sees only safe event metadata |
| P16 | UI flow/back/theme | Back returns to the immediate role hub, not arbitrary home; active cards/toggles have state feedback; light/dark theme persists |
| P17 | Guest flow | Guest can explore a clearly labelled local demo but cannot silently write/read a real family; registration/login has a clear upgrade path |

## Focused diagnosis of the reported chat/inbox defect

The user reports that messages appear in the sender’s own family space but not in the other role's app. This cannot be dismissed as a profile-selection issue: child-to-parent must still be visible to the sole parent. Treat this as a P08/P09 failure until demonstrated otherwise.

Investigate in this order:

1. On send, log the authenticated subject, `family_id`, `device_id`, `sender_role`, target role and the exact HTTP status/response body (redact tokens).
2. In Supabase, inspect the created row’s values and its RLS-visible result under each role—not only as a privileged dashboard operator.
3. Compare child query filters in `SupabaseApi` against parent query filters in `ParentApi`. Confirm both use the same family/device semantics.
4. Confirm that the receiver actually refreshes on resume/poll and displays a replacement list, rather than appending stale cards.
5. Check that `DeviceSessionStore` and `ParentSessionStore` hold a fresh authenticated client after app relaunch; a stale token must produce a visible retry/sign-in state.
6. Verify `20260830_harden_family_delivery.sql` is applied. If it reveals an RLS denial, correct policy/schema semantics rather than weakening RLS.

Do not “fix” this by duplicating rows, reading all families, or using a service-role key in the client.

## Build and install commands

The local machine’s known working build command is:

```powershell
$env:JAVA_HOME = 'C:\Users\DELL\tools\jdk-17.0.12+7'
$env:ANDROID_HOME = 'C:\Users\DELL\tools\android'
$env:ANDROID_SDK_ROOT = 'C:\Users\DELL\tools\android'
& 'C:\Users\DELL\.gradle\wrapper\dists\gradle-9.3.1-all\9ot9r568e8zfvvd4mn8rbu1j0\gradle-9.3.1\bin\gradle.bat' ':app:assembleDebug' '--offline' '--no-daemon' '--console=plain'
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

With two USB-connected devices, list device IDs first and explicitly target each one:

```powershell
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" devices
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" -s PARENT_SERIAL install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" -s CHILD_SERIAL install -r app\build\outputs\apk\debug\app-debug.apk
```

Use `adb logcat` separately for each serial while executing P01–P17. Do not include credentials, authorization headers, or raw location history in shared logs.

## Git handoff rules

The worktree includes intended source, documentation, and migration changes plus generated/local folders. Stage only reviewed source and migrations; do not add `output/`, `tmp/`, `local.properties`, `.gradle/`, `.kotlin/`, or unknown local Supabase configuration. Before a handoff commit, run `git diff --check`, build once, and give the commit a factual message such as `docs: add Claude test handoff`.

## Completion definition for the next engineer

The next valid milestone is not “more screens implemented.” It is a written test record showing P01–P17 results, with every failed row linked to a concrete cause and patch. Only then should product expansion resume.
