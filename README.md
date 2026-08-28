# Guardian Link — Generic Parental Control Template

An Android-first, self-hosted parental-control starter app. Any parent can create an account, create a family, pair child phones, and manage only that family’s data through Supabase Row Level Security.

This repository contains no personal email, password, family ID, Supabase project URL, or API key.

## What it includes

- Parent account sign-in and self-service account creation.
- One-time parent/child pairing codes and multi-child families.
- Parent controls for pause, schedules, selected-app blocking, new-app approval, and supervised browser rules.
- Explicit, visible child location sharing, safe places, activity history, and SOS alerts.
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

Deploy the Edge Functions:

```powershell
npx supabase@latest functions deploy create-pairing --use-api
npx supabase@latest functions deploy claim-child-device --no-verify-jwt --use-api
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

## Product boundaries

- Remote commands are polled by the child about every 30 seconds. Add FCM for lower-latency delivery at scale.
- Standard Android mode detects a new app immediately after installation and blocks use until approved. True pre-install approval requires Device Owner enrollment on a dedicated/reset child device.
- YouTube Shorts/keyword filtering is reliable in the supervised browser; it cannot selectively inspect the official YouTube app.
- Location is opt-in and visibly indicated by an Android foreground-service notification.
- Do not use the app for hidden monitoring, private-message capture, keystroke logging, camera/microphone collection, or screenshots.

## Before public distribution

- Create a privacy policy, consent flow, data-retention period, and account/family deletion controls.
- Replace the debug signing key with your own release signing key.
- Review Google Play’s Families, background-location, accessibility, and device-management requirements before publishing.
