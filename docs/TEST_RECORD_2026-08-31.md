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
| `auth_leaked_password_protection` | **Not fixed — no tool access.** | This is an Auth-service setting (HaveIBeenPwned check on sign-up/password-change), not a SQL object; none of the available Supabase MCP tools expose writing Auth config, and doing it via a raw Management API call would need a token this session doesn't have. Enable it manually: Supabase Dashboard → Authentication → Sign In / Providers (or Policies, depending on dashboard version) → Password → "Leaked password protection". |

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
