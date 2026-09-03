# Guardian Link — P01–P17 test record, 2026-08-31

## Environment constraint (read first)

This pass ran in a remote container with **no `adb`, no Android emulator/system image, and no
`local.properties`**. Per `docs/CLAUDE_HANDOFF.md`, a device-level pass/fail requires two
separately authenticated Android devices; that evidence cannot be produced here. Every P0x row
below is **BLOCKED (no device)** for the device/UI/logcat columns unless stated otherwise.

What *was* available: direct, read-only-then-corrective access to the live Supabase project
`parental_control_app` (`sbotscvpncsdctixyknu`) via the Supabase MCP tools, and the full source
tree. That let the reported chat/notification defect be root-caused from real backend state and
code, not simulated. An Android SDK build was also attempted and is blocked in this container
(`dl.google.com` is rejected by the outbound proxy policy, and no cached SDK exists) — the two
code fixes below are unbuilt. **Both fixes need `:app:assembleDebug` on the documented Windows
setup, then the two-device P08/P09/P10/P12 rows, before either can be called "fixed."**

## Matrix status

| ID | Flow | Status |
| --- | --- | --- |
| P01 | Pairing code generation | BLOCKED (no device) |
| P02 | Re-pair before expiry | BLOCKED (no device) — see root-cause note below; `create-pairing` intentionally never reuses an already-paired row |
| P03 | App inventory refresh | BLOCKED (no device) |
| P04 | Pause/resume all apps | BLOCKED (no device) |
| P05 | Selected-app block/unblock | BLOCKED (no device) |
| P06 | New-app approval | BLOCKED (no device) |
| P07 | Screen-time/schedule | BLOCKED (no device) |
| P08 | Parent → child chat | **Root cause identified and backend-verified; fix applied. Two-device confirmation still required.** |
| P09 | Child → parent chat | Same root cause as P08 (symmetric code path); same status |
| P10 | Fixed quick messages | **Separate real RLS defect found and fixed live. Two-device confirmation still required.** |
| P11 | Voice message | BLOCKED (no device) |
| P12 | Notification center | Same RLS defect as P10 affected notification reads; fixed live. Two-device confirmation still required |
| P13 | SOS | BLOCKED (no device) |
| P14 | Location/safe place | BLOCKED (no device) |
| P15 | Safety categories/keywords | BLOCKED (no device) — client-local logic, not reviewed this pass |
| P16 | UI flow/back/theme | BLOCKED (no device) |
| P17 | Guest flow | BLOCKED (no device) |

## P08/P09 — chat: root cause, evidence, fix

**Sender action (reconstructed from real data, not fabricated):** a parent sent chat messages
to a paired child ("SM-A325F") via `ParentApi.sendChatMessage`, which posts to
`family_chat_messages` keyed by the child's `device_id`.

**Supabase row evidence (read via `execute_sql` against `sbotscvpncsdctixyknu`):**

- `public.devices` holds **four** separate rows with `display_name = 'SM-A325F'`, all
  `retired_at IS NULL` (still counted as active by `ParentApi.devices()`):
  `49557931…` (never seen), `8d8897ff…` (never seen), `294e7224…` (seen once, 2026-08-28
  15:47), `ccb220ee…` (seen most recently, 2026-08-28 16:13).
- `family_chat_messages` has 7 parent-authored rows against `device_id = 8d8897ff…` — the row
  whose `last_seen_at` is **`null`**, i.e. no child session ever authenticated as that device.
  The device the child app was actually live on (`ccb220ee…`, the one with real check-ins) has
  **zero** chat rows.
- `family_notifications` for that same window shows the matching 7 `target_role='child'` rows
  under `device_id = 8d8897ff…` — confirms the notification path mirrors the chat bug, not an
  independent one.

**Receiving device / UI / logcat:** BLOCKED (no device) — but the row evidence alone already
demonstrates the defect: messages were durably written where no child session could ever read
them, independent of any client polling or rendering behavior.

**Cause (code review, `app/src/main/java/com/guardianlink/ui/ParentModeActivity.kt`):**
`create-pairing` (`supabase/functions/create-pairing/index.ts`) deliberately never reuses an
already-paired device row when a parent (re)generates a code for a child name that's already
paired — the comment in that function explains this is intentional, since two distinct children
can share a display name. Every such re-pair therefore leaves the old, now-dead device row
active and un-retired. Before this fix, `ParentModeActivity.refreshFamily()` reselected the
in-memory `selectedDevice` only by exact `id` match against the freshly de-duplicated device
list; once a same-named re-pair caused the old id to drop out of that list (the live/newer row
now wins the de-dup), the code fell straight through to `devices.firstOrNull()` — the single
top-of-list device for the *whole family*, not necessarily the previously-selected child. Chat,
Quick updates and Notifications are all launched from that one `selectedDevice`
(`ParentModeActivity.kt`, `buildFamilyCenter`), so a stale selection routes every message on all
three screens to a device id the child's app never queries.

**Fix applied (not yet built/tested):** `ParentModeActivity.kt`, `refreshFamily()` — added a
same-display-name fallback between the exact-id match and the family-wide fallback, so a
superseded selection stays pinned to the same child instead of silently jumping to an unrelated
one:

```kotlin
val deviceToShow = devices.firstOrNull { it.id == previouslySelected?.id }
    ?: devices.firstOrNull { it.displayName == previouslySelected?.displayName }
    ?: devices.firstOrNull()
```

**What this fix does not claim to fix:** it does not retroactively repair a chat/quick-messages/
notifications screen that was already open at the moment of a re-pair (its `device_id` Intent
extra is fixed at launch time), and it does not stop `create-pairing` from creating an orphaned
device row in the first place — that behavior is explicitly intentional in the source comment
(distinct same-named children must not collide) and changing it is a design decision outside
this pass's "fix only proven causes" scope. Flagging for a maintainer decision, not fixing
silently: **automatic retirement of the old row would need a way to tell "this is the same
child re-pairing" from "this is a second, different child with the same name," which the current
data model cannot distinguish.**

## P10/P12 — quick messages and notifications: separate real defect, fixed live

**Supabase row evidence:** `pg_policies` on the live project showed four policies whose family
check was `d.family_id = d.family_id` — always true:

- `family_messages`: `"Parents send family quick messages"`, `"Child sends own quick messages"`
  (both INSERT `with_check`)
- `family_notifications`: `"Parents read own family notifications"`,
  `"Children read own family notifications"` (both SELECT `qual`)

**Cause:** not DB drift — the bug is in the migration source itself
(`20260829_family_quick_messages.sql`, `20260830_family_notifications.sql`). Inside
`exists (select 1 from public.devices d where d.id = device_id and d.family_id = family_id ...)`,
Postgres resolves the bare `family_id` on the right of the second comparison to the subquery's
own scope (`devices.family_id`) rather than the outer `family_messages`/`family_notifications`
row, because `devices` also has a `family_id` column. The intended family-ownership check
silently became a no-op. `20260830_harden_family_delivery.sql` avoided this by explicitly
qualifying (`family_chat_messages.family_id`), which is why `family_chat_messages` and the
`family_notifications` INSERT/UPDATE policies (also written with explicit qualification) were
already correct.

This does not by itself explain "message invisible to the other role" (an overly-permissive
check doesn't block legitimate reads — `family_messages` SELECT policies never checked
`family_id` at all, only `device_id`), but it is a real, confirmed, currently-live family-
isolation gap: a row's stated `family_id` was never actually checked against its device's real
family on write (`family_messages`) or read (`family_notifications`).

**Fix applied and verified live:** `supabase/migrations/20260831_fix_family_delivery_scope_drift.sql`,
applied via `apply_migration` against project `sbotscvpncsdctixyknu`. Re-queried `pg_policies`
afterward — all four policies now read `d.family_id = family_messages.family_id` /
`d.family_id = family_notifications.family_id` (outer table explicitly qualified). Also ran a
sanity check joining every existing `family_messages`/`family_notifications` row to its device:
0 rows have a `family_id` that disagrees with their device's real family, so the corrected,
now-enforcing policy does not newly hide or reject any existing data.

## Supabase security-advisor findings

`get_advisors(type: security)` was run before and after the two fixes above. It reported four
findings, none of which are the chat/notification root cause — recorded here because they were
found in the same project during this pass and two were safe, in-scope fixes.

| Finding | Action | Why |
| --- | --- | --- |
| `function_search_path_mutable` on `guardian_limit_notification_update` | **Fixed live.** `alter function ... set search_path = ''` | The trigger function makes no schema-qualified reference (only `to_jsonb`/`NEW`/`OLD`, resolved via `pg_catalog`), so pinning an empty search_path is safe and fully closes the finding. |
| `anon_security_definer_function_executable` on `complete_child_command` | **Fixed live.** `revoke execute ... from anon` | The function was reachable by unauthenticated callers via `/rest/v1/rpc/complete_child_command`. It was not actually exploitable — the function checks `d.child_auth_user_id = auth.uid()` internally, and `auth.uid()` is `null` for `anon`, so no row ever matched — but anon never has a legitimate reason to call it, so the grant was needless attack surface. |
| `authenticated_security_definer_function_executable` on `complete_child_command` | **Not changed — reviewed and intentional.** | This is the function's real, load-bearing use: a paired child's authenticated JWT calls it to mark its own command as processed (`SupabaseApi.markCommandProcessed`, used by P04/P07's delivery-receipt flow). Revoking `authenticated` execute would break that flow. `SECURITY DEFINER` is required here so the child's limited-grant role can update `parent_commands`; the function already scopes the update to the caller's own device internally, so this is the correct shape, not a bug. |
| `auth_leaked_password_protection` | **Ignored — not available on this project's plan.** Confirmed by the project owner: the HaveIBeenPwned password check is a paid-tier Auth feature and isn't offered on the free plan this project runs on. No action possible or needed until/unless the project is upgraded. |

Fixes applied in `supabase/migrations/20260831_close_security_advisor_findings.sql`, applied
live via `apply_migration` and confirmed with a follow-up `get_advisors` call (both target
findings gone from the report; only the two "not changed" rows above remain).

## Build status

`:app:assembleDebug` could not be run in this container: `ANDROID_HOME`/`local.properties` are
unset (expected — secrets are correctly git-ignored) and the Android SDK cannot be fetched here
(`dl.google.com` is rejected by the outbound proxy: `CONNECT tunnel failed, response 403`), with
no cached SDK found on disk. Gradle 9.3.1 itself resolves fine through the proxy. **Both code
changes need a build on the documented Windows/JDK 17/API 34 setup before install.**

## Required next step

Build `app-debug.apk` from this commit on the documented setup, install on two devices with
distinct authenticated identities (fresh parent + fresh child pairing), and run P08/P09/P10/P12
end-to-end per the acceptance-matrix evidence requirements (sender action, Supabase row, receiver
query, receiver UI, logcat). Only record them "PASS" here once that evidence exists.

## Follow-up pass, 2026-09-01: design coherence and a voice-message RLS bug

The user reported design mismatches between pages, text made unreadable by font-color choices,
and asked to confirm audio messages actually work. Code review (still no device access in this
container) found these root causes:

**AlertDialogs never followed the app's dark/light state.** `AndroidManifest.xml` fixes the app
theme to `Theme.Material.Light.NoActionBar` — that's the base Activity theme, not NoirUi's
adaptive dark/light state, and a plain `android.app.AlertDialog.Builder(this)` inherits it
regardless of what NoirUi is currently rendering. Every popup (pairing dialog, parent-alerts
dialog, privacy notice, retire-device confirm, child re-pair confirm, child extra-time request,
guest-mode message boxes — 7 call sites total) was therefore always a plain light system dialog,
mismatched against a dark NoirUi screen behind it. Worse, `ParentModeActivity.showAlertControls()`
put a `Switch` colored with `NoirUi.TEXT` (light cream in dark mode) directly on that dialog with
no background box of its own — genuinely unreadable, near-invisible text, in dark mode. Fixed
with `NoirUi.dialogBuilder(context)` (`NoirUi.kt`), which builds an `AlertDialog.Builder` themed
`android.R.style.Theme_Material_Dialog` or `Theme_Material_Light_Dialog` to match
`NoirUi.isDark()`, and swapped in at all 7 call sites (`ParentModeActivity.kt`,
`ChildModeActivity.kt`, `GuestPreviewActivity.kt`).

**Two screens had colors hardcoded to only their dark-mode values.** `GuestPreviewActivity.kt`
(`note()`, `primary()`/`secondary()` pressed/hover states) and `LocationLogActivity.kt`
(`locationCard()`, `messageCard()`) painted card backgrounds and button interactive states with
literal hex (`0xFF23242C`, `0xFF7D6A3B`, `0xFF2B2D36` — NoirUi's dark-mode SURFACE/GOLD_DIM/
SURFACE_RAISED values, copy-pasted instead of referencing the adaptive getters). In light mode
these rendered as dark boxes sitting in an otherwise light screen — the literal "mismatch"
reported — and in `GuestPreviewActivity.note()` specifically, `MUTED` text (mid-gray, meant for a
light surface) landed on a near-black box, close to unreadable. Fixed by replacing every literal
with the matching adaptive `NoirUi.*` getter (added `SURFACE`/`SURFACE_RAISED`/`GOLD_DIM` aliases
alongside each file's existing `BACKGROUND`/`NAVY`/`MUTED`/`BLUE`/`BORDER` ones).

`ParentModeActivity.showPairingDialog()`'s pairing-code output box also used an unrelated
hardcoded light-blue palette (`Color.rgb(17, 80, 130)` text on `Color.rgb(232, 242, 255)`) that
never adapted and didn't match the app's graphite/gold language either. Switched to
`NoirUi.TEXT` on `rounded(NoirUi.SURFACE_RAISED, NoirUi.GOLD_DIM)`.

`SosAlertActivity` (the full-screen SOS alarm) was deliberately left untouched: it uses its own
hardcoded high-contrast red/white palette instead of NoirUi, which is intentional — an emergency
alarm screen should look alarming and distinct, not blend into the graphite/gold everyday theme.
Its own contrast (white/light-pink text on red) is fine on inspection.

**Chat message sequencing:** re-checked `SupabaseApi.chatMessages()` / `ParentApi.chatMessages()`
and `FamilyChatActivity.render()`. Both sides already query the same `family_chat_messages` rows
by `device_id` only (not filtered by `sender_role`), order ascending by `created_at`, and fully
replace the rendered list on every load — so parent and child messages already interleave in one
chronological thread on both screens by design. No code defect found here; if this still doesn't
hold once tested on two devices, it's most likely a symptom of the P08/P09 device-selection bug
already fixed (a stale `device_id` splits the conversation across two device rows, which would
look like "not in sequence" — actually two different threads).

**Voice messages: a real, confirmed RLS bug, not a missing library.** The `guardian-voice`
storage bucket, its RLS policies, `RECORD_AUDIO` in the manifest, and the record/upload/play code
in `SupabaseApi`/`ParentApi`/`FamilyChatActivity` were all already present and correct-looking —
but live inspection of `storage.objects` policies on the target Supabase project showed the two
**parent-side** policies (`Parents read guardian voice`, `Parents upload guardian voice`) had
silently resolved `(storage.foldername(name))[1]` to `families.name` (the family's display
name) instead of `storage.objects.name` (the uploaded file's path) — the identical class of
Postgres subquery-scoping bug already fixed for `family_messages`/`family_notifications` in
`20260831_fix_family_delivery_scope_drift.sql`, this time baked into
`20260829_family_chat_voice.sql` since it was first written. A family's display name never
matches a device UUID, so these two policies always evaluated false: **a parent could never
read or upload a voice note**, while the child-side policies (whose subquery only touches
`devices`, which has no `name` column to shadow the reference) worked correctly the whole time.
This is very likely what read as "the audio button isn't there" — a parent's recording would
fail to upload, so no chat row (and therefore no ▶ Play voice note button) was ever created.

Fixed and verified live: `supabase/migrations/20260901_fix_guardian_voice_parent_policies.sql`,
applied to `sbotscvpncsdctixyknu` via `apply_migration`. Re-queried `pg_policies` afterward — both
policies now read `(storage.foldername(objects.name))[1]`, correctly qualified.

None of this needed a new library or SDK module — `MediaRecorder`/`MediaPlayer`/`RECORD_AUDIO`
were already correctly wired; the failure was purely the backend policy. As with the other RLS
fixes in this file, this is backend-verified, not two-device-confirmed — record a real P11 pass
once you can install and test on two devices.
