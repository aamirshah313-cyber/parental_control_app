# Guardian Link coherence and security audit

Date: 2026-08-29
Scope: the Android parent and child clients, their Supabase REST flows, policies, commands, alerts, app inventory, locations, messages, and time requests.

## Fixed in this release

| Flow | Finding | Correction |
| --- | --- | --- |
| Parent actions -> child | A failed REST `POST` could be presented as success because an empty local JSON response was manufactured after a failed request. | Parent writes now return failure on RLS, authentication, or network rejection. The UI tells the parent to retry instead of saying a command/rule was sent. |
| Child sync -> parent | A child could show “Rules synced” even if its paired device session could not be read or its `last_seen_at` update failed. | Sync now verifies the device JWT/RLS session and updates last-seen before reporting success. |
| Child inventory -> parent | Android package visibility may hide launchable apps on modern Android, and the parent refresh control was removed when the list rendered. | Added a narrow launcher `<queries>` declaration and kept the refresh action permanently visible. |
| Installed/uninstalled apps | Full inventory uploads only inserted/updated rows, leaving uninstalled apps on the parent dashboard. | A manual child refresh is now authoritative and removes obsolete rows after its secure upload. It needs the included migration. |
| Location -> parent | A location event and safe-place change could be written after the actual location insert failed. | Location events/transitions now occur only after the location row succeeds. |
| UI clarity | The opening/guest screens retained white cards after switching the app to graphite and gold, hiding light text. | Opening and guest surfaces use the shared graphite palette, gold primary controls, and state-aware feedback. |

## Verified module data paths

| Module | Child to parent | Parent to child | Verification point |
| --- | --- | --- | --- |
| Pairing | Claim edge function issues device-scoped session | Parent creates time-limited code | Pairing is one-time; raw code is not stored in the database. |
| Rules, schedules, keywords, YouTube Shorts | Child downloads only latest active policy | Parent creates monotonically versioned policy | Child sync verifies its session first; parent write outcome is truthful. |
| Pause, resume, bonus time | Child posts acknowledgement | Parent queues command | Parent can use **Check delivery** to see last-seen and command acknowledgement. |
| Installed apps and approval | Child reports launcher apps/installs | Parent publishes allow/block/pause/limit policy | Child **Refresh installed apps** then parent **Refresh reported apps**. A full report clears stale rows. |
| Location and safe places | Foreground child location service writes opt-in positions/events | Parent publishes location/safe-place settings | Parent uses latest location or Location Log; failed locations no longer produce false update events. |
| SOS | Child inserts SOS event | Parent dashboard polls and activates visible alarm | Requires SOS migration and network on both devices. |
| Messages | Each side writes device-scoped fixed template message | Both sides read the same device-scoped thread | RLS restricts the message to the paired device and family owner. |
| More-time requests | Child creates pending request | Parent resolves it and queues `grant_time` | Request remains pending if the command cannot be queued. |
| Device health | Child writes battery, permissions, protection and screen-time summary | Parent reads summary | RLS restricts the row to paired child/family owner. |

## Required deployment state

Run the existing migrations in order and then run `20260829_sync_integrity.sql` and `20260829_command_delivery_queue.sql` in Supabase SQL Editor. In particular, missing `20260828_reported_apps.sql` is the usual reason the parent receives no installed-app rows. The parent/child APK must be updated on both phones; the child needs to tap **Refresh installed apps for parent** once after updating. The command-delivery migration changes command handling from “latest only” to an ordered, visible queue.

For typed Family Chat and private voice notes, additionally run `20260829_family_chat_voice.sql`.

Current builds also require `20260829_unified_family_communication.sql`. Quick Updates are now labelled preset messages in the same protected conversation stream as Family Chat, so a preset sent from either phone is visible in Family Chat on both phones.

`20260830_family_notifications.sql` adds a recipient-specific in-app inbox. A chat or preset sent by the parent is recorded for the child, and vice versa. The inbox remains available even when Android push notifications are disabled; both phones can use its manual Refresh control to confirm a just-sent update.

The parent inbox intentionally reads every child stream in the family, not merely the currently selected dashboard profile. A child update therefore remains visible to the one parent account and opens the exact child thread when tapped.

## Security controls confirmed

- The mobile client uses Supabase’s publishable key, never the service-role key.
- Parent reads/writes are scoped by family ownership; child reads/writes are scoped by its `child_auth_user_id`.
- Location uses an explicit foreground notification and Android location permission; it is not a hidden tracker.
- The app inventory uses launcher visibility rather than the privacy-invasive `QUERY_ALL_PACKAGES` permission.
- Pairing codes are generated/claimed through edge functions and are short-lived/one-time.
- Browser filtering applies only to Guardian Link Family Browser; normal Android mode cannot inspect encrypted traffic from Chrome or the official YouTube app.
- Standard Android mode can report an install and block first use, but cannot reliably approve an app before Play Store installation without a Device Owner deployment.

## Remaining operational limits and next hardening work

1. Commands are polled, so “instant” means at the next child sync (normally within the running protection service interval), not a guaranteed push delivery. Firebase Cloud Messaging or Supabase Realtime would improve latency but requires an additional delivery integration.
2. The command stream currently applies the newest outstanding command. Queue processing with idempotency keys is the next recommended reliability upgrade for several rapid actions while a child phone is offline.
3. Add automated Android/device tests for the matrix above and a Supabase staging project before a production release.
4. Store only the minimum event/location history and add a parent-facing retention control before broad distribution.
