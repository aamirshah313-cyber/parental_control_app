# Guardian Link — Generic Parental Control Template

An Android-first, self-hosted parental-control starter app. Any parent can create an account, create a family, pair child phones, and manage only that family’s data through Supabase Row Level Security.

This repository contains no personal email, password, family ID, Supabase project URL, or API key.

## Engineering handoff and verification

For a reproducible Claude Code handoff, start with [`CLAUDE.md`](CLAUDE.md) and the detailed [`docs/CLAUDE_HANDOFF.md`](docs/CLAUDE_HANDOFF.md). They document the safe local configuration boundary, current build baseline, Supabase migration caveat, known unverified synchronization issue, and the required two-device acceptance matrix.

## What it includes

- Parent account sign-in and self-service account creation.
- One-time parent/child pairing codes and multi-child families.
- Parent controls for pause, schedules, selected-app blocking, new-app approval, and supervised browser rules including adult, graphic-violence, gambling, and social-media website categories.
- Daily screen-time allowance, selected-app daily limits, and parent-approved one-day bonus time.
- Explicit, visible child location sharing, safe places, activity history, and SOS alerts.
- Child-device health status (battery, Usage Access, protection setup, and today's screen time) plus soft device retirement.
- Offline enforcement of previously downloaded rules.
- Android Keystore encryption for local parent and child session tokens.

## Configure your own branded app

1. Create your own Supabase project.
2. Copy [`local.properties.example`](local.properties.example) to `local.properties`.
3. Enter only your project’s publishable/anon key. Never use a service-role key in Android.
4. Change the optional branding values:

```properties
PRODUCT_NAME=My Family Safety
BRAND_PRIMARY_COLOR=#1366D6
APPLICATION_ID=com.yourcompany.familysafety
```

`PRODUCT_NAME` changes the launcher label and visible service messaging. The color config sets the Android accent color. `APPLICATION_ID` should be a unique reverse-domain ID before release; it is also respected by the global-pause safety exception. Replace the launcher icon resources before publishing a white-label build.

## Set up Supabase

Run these SQL files in the Supabase SQL Editor, in order:

1. [`supabase/schema.sql`](supabase/schema.sql)
2. [`supabase/migrations/20260828_parent_dashboard.sql`](supabase/migrations/20260828_parent_dashboard.sql)
3. [`supabase/migrations/20260828_sos_alert.sql`](supabase/migrations/20260828_sos_alert.sql)
4. [`supabase/migrations/20260828_reported_apps.sql`](supabase/migrations/20260828_reported_apps.sql)
5. [`supabase/migrations/20260829_family_quick_messages.sql`](supabase/migrations/20260829_family_quick_messages.sql)
6. [`supabase/migrations/20260829_professional_controls.sql`](supabase/migrations/20260829_professional_controls.sql)
7. [`supabase/migrations/20260829_sync_integrity.sql`](supabase/migrations/20260829_sync_integrity.sql)
8. [`supabase/migrations/20260829_family_chat_voice.sql`](supabase/migrations/20260829_family_chat_voice.sql)
9. [`supabase/migrations/20260829_category_safety_events.sql`](supabase/migrations/20260829_category_safety_events.sql)
10. [`supabase/migrations/20260829_command_delivery_queue.sql`](supabase/migrations/20260829_command_delivery_queue.sql)
11. [`supabase/migrations/20260829_unified_family_communication.sql`](supabase/migrations/20260829_unified_family_communication.sql)
12. [`supabase/migrations/20260829_harden_command_receipts.sql`](supabase/migrations/20260829_harden_command_receipts.sql)
13. [`supabase/migrations/20260830_family_notifications.sql`](supabase/migrations/20260830_family_notifications.sql)
14. [`supabase/migrations/20260830_harden_family_delivery.sql`](supabase/migrations/20260830_harden_family_delivery.sql)

Deploy the Edge Functions:

```powershell
npx supabase@latest functions deploy create-pairing --use-api
npx supabase@latest functions deploy claim-child-device --no-verify-jwt --use-api
npx supabase@latest functions deploy retire-device --use-api
```

In Supabase Auth, enable email/password sign-up. If email confirmation is enabled, new parents confirm their email before their first sign-in.

## Build

Use JDK 17 and Android SDK API 34:

```powershell
gradlew.bat :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## First-use flow

1. Install the APK on the parent phone and choose **I am the parent**.
2. Create an account or sign in, then create a family.
3. Generate a one-time pairing code.
4. Install the same APK on the child phone, choose child setup, and paste the code.
5. Complete Step 5 on the child phone to grant the Android permissions required for protection and optional location.

## Navigation and Guest mode

- **Guest mode** opens directly into a full local demo dashboard. It does not require a login to explore controls, safety, app management, time requests, family communication, or Guardian Guide; it cannot access or change a real family’s data.
- Detail pages return to their immediate Parent or Child hub with Android Back. Notification message shortcuts also create that hub in the Back stack.
- See [`docs/APP_FLOW_ARCHITECTURE.md`](docs/APP_FLOW_ARCHITECTURE.md) for the complete route map and data-access boundaries.

## Product boundaries

- Remote commands are polled by the child about every 30 seconds. Add FCM for lower-latency delivery at scale.
- Standard Android mode detects a new app immediately after installation and blocks use until approved. True pre-install approval requires Device Owner enrollment on a dedicated/reset child device.
- Category, YouTube Shorts, and keyword filtering is reliable in the supervised browser. Category checks use transparent on-device domain and page/search-term rules, need no paid API, and are not a complete content-classification service.
- Category blocks appear in Safety activity with only the category and time; URLs, page text, and search terms are not recorded.
- The app cannot selectively inspect the official YouTube app, Chrome, or other encrypted third-party apps. Block those apps from **Manage child apps** when required.
- Location is opt-in and visibly indicated by an Android foreground-service notification.
- Do not use the app for hidden monitoring, private-message capture, keystroke logging, camera/microphone collection, or screenshots.

## Production upgrades

Before enabling low-latency notifications or Device Owner mode, follow [`docs/PRODUCTION_DELIVERY_AND_DEVICE_OWNER.md`](docs/PRODUCTION_DELIVERY_AND_DEVICE_OWNER.md). These capabilities require project-specific Firebase credentials and/or a reset dedicated child device; they cannot be safely enabled from a normal installed APK.

## Before public distribution

- Create a privacy policy, consent flow, data-retention period, and account/family deletion controls.
- Complete and publish the editable [privacy-policy template](docs/PRIVACY_POLICY_TEMPLATE.md), including a real support contact and deletion process.
- Replace the debug signing key with your own release signing key.
- Review Google Play’s Families, background-location, accessibility, and device-management requirements before publishing.
