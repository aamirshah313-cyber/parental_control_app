# Guardian Link — Claude working guide

This is a Kotlin, Android-first parental-control application. It uses Android platform APIs and Supabase REST/Edge Functions directly; it does not depend on a Flutter or React Native runtime.

## Start here

1. Read [`docs/CLAUDE_HANDOFF.md`](docs/CLAUDE_HANDOFF.md) completely before changing code.
2. Preserve uncommitted work. Do not run `git reset`, `git clean`, or broadly stage generated folders.
3. Before feature work, run the two-device acceptance matrix in the handoff. The reported cross-device chat/inbox issue is **not verified fixed**.

## Local configuration and secrets

- Copy `local.properties.example` to `local.properties` and insert the team's **own** Supabase URL and publishable/anon key.
- `local.properties` is ignored by Git. Never commit it, account credentials, tokens, family IDs, data exports, or a Supabase service-role key.
- A publishable/anon key is appropriate in the Android client only when the database RLS policies are correct. Privileged operations belong in Edge Functions.

## Build the current app

The known working local setup is JDK 17 plus Android SDK API 34. In PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Users\DELL\tools\jdk-17.0.12+7'
$env:ANDROID_HOME = 'C:\Users\DELL\tools\android'
$env:ANDROID_SDK_ROOT = 'C:\Users\DELL\tools\android'
& 'C:\Users\DELL\.gradle\wrapper\dists\gradle-9.3.1-all\9ot9r568e8zfvvd4mn8rbu1j0\gradle-9.3.1\bin\gradle.bat' ':app:assembleDebug' '--offline' '--no-daemon' '--console=plain'
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Current app version: `0.6.4` (version code `22`).

## Important Supabase rule

Run the SQL migrations in the Supabase SQL Editor in the documented order. Do **not** run `supabase db push --linked --include-all` against the current project: its remote migration history was manually altered and includes conflicting date-version entries. Repair and baseline that history first, in a separately reviewed maintenance task.

The final migration currently requiring confirmation in the target project is `supabase/migrations/20260830_harden_family_delivery.sql`. It constrains chat and notification writes to the referenced child's family.

## Code map

- App entry/auth/guest routing: `app/src/main/java/com/guardianlink/MainActivity.kt`
- Parent hub and remote controls: `ui/ParentModeActivity.kt`, `sync/ParentApi.kt`
- Child hub/setup/enforcement: `ui/ChildModeActivity.kt`, `enforcement/`, `sync/PolicySynchronizer.kt`
- Communication and notifications: `ui/FamilyChatActivity.kt`, `ui/QuickMessagesActivity.kt`, `ui/NotificationsActivity.kt`, `sync/SupabaseApi.kt`
- Policies/category controls: `policy/`, `ui/SafeBrowserActivity.kt`, `ui/ManageAppsActivity.kt`
- Location/SOS: `ui/LiveLocationActivity.kt`, `enforcement/LocationService.kt`, `enforcement/SosAlertService.kt`

## Verification discipline

Do not describe a function as working merely because it builds or a local screen renders. For parent/child flows, prove the sender's row, receiver's authenticated query, receiver UI, and notification behavior on two separately authenticated devices. Record the result against the handoff matrix.
