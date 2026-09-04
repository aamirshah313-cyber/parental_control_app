# Approval workflow, location, and Guest Mode redesign

Added 2026-09-04. Preserves all previously working pairing, authentication, parent/child
communication, RLS, and backend functionality — no existing table, policy, or RLS-relevant
column was removed or narrowed; only new tables/columns and new client screens were added. See
`docs/TEST_RECORD_2026-08-31.md` for the still-pending two-device confirmation of the earlier
chat/notification fixes, which this work does not depend on or change.

## 1. Approval workflow for child-side app actions

### Android privilege limitations (read this first)

Guardian Link ships as a **standard Android app**. It is not, and this change does not make it,
a **Device Owner** or **Device Admin**. Concretely:

- **Install**: Android alone decides whether an app installs; no app without Device Owner /
  Managed Google Play EMM enrollment can silently allow, block, or trigger an install. Guardian
  Link cannot and does not claim to.
- **Unblock / enable**: what this app *can* do, and all it claims to do, is lift its own
  app-level soft block — the same mechanism `PolicyEngine`/`ProtectionService` already used
  before this change (foreground-app monitoring redirects a blocked package to
  `BlockingActivity`). No Android API is called to disable, hide, or suspend a package.
- `enforcement/DeviceCapabilities.kt` detects `DevicePolicyManager.isDeviceOwnerApp(...)` at
  runtime and is surfaced in the parent Approval Requests screen (`capabilityNote`) so the UI
  never implies a guarantee the OS won't actually honor. If this app is later enrolled as a
  Device Owner, `enforcementDescription()` is the one place to extend with real
  `setPackagesSuspended`/`setApplicationHidden` calls — nothing in this change assumes that will
  happen.

### Workflow, as implemented

1. **Install**: `PackageChangedReceiver` already detected a new install and (when
   `policy.requireAppApproval` and the package isn't pre-approved) soft-blocked it locally. It
   now *also* calls `SupabaseApi.requestAppAction(appName, packageName, "install")`, filing a
   trackable, parent-notified request for the same attempt — the child does not have to do
   anything extra for the common case.
2. **Unblock / enable**: the child opens the new "Ask to install, unblock, or enable an app"
   screen (`ChildAppRequestsActivity`, linked from the Child hub's Connect section), sees their
   reported apps with a computed state (Blocked / Awaiting approval / Allowed), and taps
   `Request unblock` or `Request enable` on a restricted one. A free-text form on the same screen
   lets them request an app that isn't installed yet (name + package name), which is the same
   `install` action.
3. Every request lands in `public.app_action_requests` (family_id, device_id, app_name,
   package_name, action, status, requested_at, expires_at, decided_at, decided_by) and triggers
   one `family_notifications` row (`event_type = 'app_request'`, `target_role = 'parent'`).
4. The parent opens **Approval Requests** (`ApprovalRequestsActivity`, linked from the Parent
   dashboard's Family communication section): pending requests first, each card showing child
   name, a generated avatar in place of a real app icon (Android app icons for a package the
   parent device doesn't have installed aren't retrievable — this is stated in the UI, not
   silently faked), app name, package name, action, request time, and Approve/Deny. Decided
   requests remain visible below as history.
5. On decide, `ParentApi.decideAppActionRequest` updates status/decided_at/decided_by (rejected
   by a database trigger if the request was already decided — see RLS below), and on approval,
   `applyApprovedAction` publishes an updated `ChildPolicy` (removes the package from
   `blockedPackages`, adds it to `approvedPackages`) through the existing `publishPolicy`/
   `PolicySynchronizer` pipeline — the same delivery path every other policy change already uses.
   `ParentApi.sendAppActionDecision` posts exactly one child-facing notification
   (`event_type = 'app_request'`, `target_role = 'child'`).
6. The child sees their request move from **"Waiting for parent approval"** to **"Approved"** or
   **"Request declined by parent."** on `ChildAppRequestsActivity`, and gets the decision
   notification in the existing Notifications inbox.

A request that passes `expires_at` (24h after creation) without a decision is treated as expired
by both UIs (computed client-side from the timestamp, not a background job) — the row's `status`
stays `pending` in the database but both screens display it as expired and stop offering
Approve/Deny.

## 2. Location after pairing

Most of this already existed (`device_locations` table + RLS, `LocationService`,
`LiveLocationActivity`, `LocationLogActivity`) and was preserved as-is. What was added:

- `device_health.location_status` (new column: `available | waiting | permission_denied |
  services_disabled | offline | unavailable`, enum-checked). `LocationService` now reports
  `permission_denied` (no fine/coarse location permission), `services_disabled` (no enabled
  location provider), `waiting` (permission and a provider are fine, but no fix has arrived yet),
  and `available` (an upload just succeeded) at the actual point each condition is detected.
- **"Device offline" is inferred parent-side, not child-reported** — a device with no network
  cannot successfully tell the server it's offline. `LiveLocationActivity` treats a
  `device_health.reported_at` older than 30 minutes as offline.
- `LiveLocationActivity` now renders all six states from the spec (`Location available`,
  `Waiting for location`, `Permission denied`, `Location services disabled`, `Device offline`,
  `Location unavailable`) instead of one generic "no location yet" fallback, plus the existing
  "Refresh now" action and Location Log link.
- Family scoping: `device_locations`/`device_health` RLS (unchanged, pre-existing, re-verified
  this pass) ties every row to its device's family via `devices.family_id`, so one family's
  locations are never visible to another family's parent. No direct `family_id` column was added
  to `device_locations` — it's already derivable through `device_id → devices.family_id`, and
  duplicating it as a redundant column was avoided per this change's own "reuse existing
  relationships" requirement.

## 3. Guest Mode redesign

`GuestPreviewActivity` was restructured (not just recolored) to read as product exploration
rather than a cloned dashboard:

- A persistent **"GUEST MODE — LOCAL PREVIEW, NOT SIGNED IN"** badge renders on every guest
  screen.
- A distinct **teal accent** (`GUEST_ACCENT`, still light/dark adaptive — it is derived the same
  way NoirUi's own tokens are, just a different hue) replaces NoirUi's gold everywhere in Guest
  Mode, so it is visually distinguishable from Parent/Child mode at a glance while staying
  legible in both themes (this also fixes the underlying bug from the previous pass, where guest
  colors had drifted to literally equal NoirUi's tokens 1:1 and so were no longer distinct at
  all).
- The home screen is flat: badge → headline → **Create account / Sign in** (prominent, above the
  fold) → a vertical stack of feature-preview cards, replacing the old 2×2 dashboard-style nav
  grid and multi-level Overview→Family→Chat drill-down.
- **Locked-state cards**: "Location sharing" and "App approval requests" — the two features that
  only mean anything against a real family — render as an explicit locked card (🔒, muted text,
  "Sign in to use this") instead of a simulated preview, so Guest Mode never fabricates or
  implies access to real family/child/location/approval/chat/device data. The other previews
  (controls, chat, apps, time, safety) remain locally simulated with obviously-demo copy, as
  before — `GuestPreviewActivity` makes no network calls at all.
- Every sub-page's back action returns to the single home screen rather than chaining through
  intermediate dashboard pages.

## 4. Database schema and RLS (new objects only)

`supabase/migrations/20260904_app_action_requests_and_location_status.sql`:

```
app_action_requests
  id uuid pk, family_id uuid fk→families, device_id uuid fk→devices,
  app_name text, package_name text, action text check(install|unblock|enable),
  status text check(pending|approved|denied|expired) default 'pending',
  requested_at timestamptz default now(), expires_at timestamptz default now()+24h,
  decided_at timestamptz, decided_by uuid fk→auth.users
```

RLS (mirrors the existing devices/families ownership pattern used throughout the schema):

- `Child creates own app action requests` (INSERT): device must belong to the authenticated
  child, `status/decided_at/decided_by` must be the pending defaults.
- `Child reads own app action requests` (SELECT): device must belong to the authenticated child.
- `Parents read family app action requests` (SELECT): device's family must be owned by the
  authenticated parent.
- `Parents decide family app action requests` (UPDATE): same family-ownership check.
- A `before update` trigger (`guardian_limit_app_action_request_update`) additionally enforces,
  independent of RLS: a request can only be decided once (`old.status` must be `'pending'`), only
  `status/decided_at/decided_by` may change, and the new status must be `approved` or `denied` —
  a parent (even the rightful one) cannot edit `app_name`/`package_name` after the fact, and
  can't re-decide an already-decided row.

`device_health.location_status` (see section 2) reuses the existing `device_health` RLS
unchanged.

### RLS verification (live, no device required)

Every rule above was proven against the live target project by impersonating real
`auth.uid()`s in SQL (`set local role authenticated; set local request.jwt.claims = ...`) rather
than only reading the policy text:

| Check | Result |
| --- | --- |
| Child inserts a pending request for their own device | Succeeded |
| Unrelated parent (different family) reads it | 0 rows |
| Unrelated parent attempts to decide it | 0 rows affected, no error, RLS-filtered |
| Rightful parent (family owner) approves it | Succeeded, `decided_by` set correctly |
| Child re-reads their own request | Sees `status = approved` |
| Rightful parent attempts to edit the decided row further | Rejected by the trigger (`P0001: This request has already been decided`) |
| Insert a `device_health.location_status` outside the allowed enum | Rejected by the check constraint |

All test rows were deleted after verification; no test data was left in the live database.

## 5. Notifications

No new notification pipeline. Both directions reuse `family_notifications` with the pre-existing
`event_type = 'app_request'` value (already valid before this change): one row on request
creation (`target_role = 'parent'`), one row on decision (`target_role = 'child'`) — never more
than one of each per action, avoiding duplicates.

## 6. Testing status — read before trusting anything above as "done"

**This container has no `adb`, no Android emulator, and no Android SDK** (established earlier in
this project's handoff and unchanged). Nothing in this feature has been installed or exercised
on an actual Android device or emulator. Concretely:

- **Backend/RLS**: verified live against the real Supabase project by the SQL impersonation
  technique in section 4 above. This is real evidence, not a description of intent.
- **Client Kotlin**: written to compile and to match this codebase's existing patterns exactly
  (same async/Thread style, same NoirUi helpers, same REST client shape), and validated via the
  existing GitHub Actions CI build (`.github/workflows/build-debug-apk.yml`) — that proves it
  compiles into an installable APK, nothing more.
- **Not verified, and must not be described as complete, until done on a real device**:
  - Every UI interaction in `ApprovalRequestsActivity` / `ChildAppRequestsActivity` (button
    layout, scrolling, dialog behavior, dark/light rendering).
  - `LocationService`'s permission/GPS-disabled detection against real Android permission
    dialogs and real GPS state.
  - The full pairing → request → notification → decision → policy-sync round trip across two
    physical/emulated devices.
  - Guest Mode's new layout on an actual screen.
- **Unit tests**: `app/src/test/java/com/guardianlink/ApprovalAndLocationLogicTest.kt` (JUnit,
  runs on the JVM, no Android framework or device needed) covers the pure-logic pieces that don't
  require Android: request-expiry computation and the blocked/awaiting-approval/allowed app-state
  classification used by `ChildAppRequestsActivity`. There was no existing test infrastructure in
  this repository before this change (`app/src/test` did not exist) — see `app/build.gradle.kts`
  for the added `testImplementation`. Everything that requires Android (Activities, Services,
  `DevicePolicyManager`) is not unit-testable without an emulator/instrumented test, which this
  container cannot run.

Per this project's own standing rule (`CLAUDE.md`): do not convert any bullet above from
"written" to "working" without recorded two-device evidence.
