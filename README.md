# Guardian Link

Android-first parental-control app with cloud-backed parent controls, offline child enforcement, supervised browsing, and explicit visible location sharing.

## Included modules

- Parent Supabase sign-in, family loading, paired-device list, and cloud pairing-code generation.
- Single-use pairing-code duration choices: 10 minutes, 30 minutes, 1 hour, or 24 hours.
- Remote pause now, 30-minute pause, and resume commands.
- Cloud policies for daily bedtime, keywords, YouTube Shorts links in the supervised browser, app blocking, new-app approval, and child location sharing.
- Offline child enforcement for downloaded schedules, pauses, app blocks, and browser filtering.
- Standard-mode new-app detection: a newly installed app is locally blocked until a parent publishes an approval.
- Visible, opt-in child location sharing with coordinates, accuracy, and recorded time in the Parent Dashboard.
- Safe places (name, coordinates, radius) evaluated locally on the child phone, with only enter/leave events uploaded.

## Build

1. Open this folder in Android Studio or run `gradlew.bat :app:assembleDebug` with JDK 17 and Android SDK API 34.
2. Create a local `local.properties` containing `SUPABASE_URL=https://YOUR_PROJECT.supabase.co` and `SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY`.
3. Never put a Supabase secret/service-role key in the Android app.

## Supabase deployment

1. Run [`supabase/schema.sql`](supabase/schema.sql) in the Supabase SQL Editor if the base schema is not already installed.
2. Run [`supabase/migrations/20260828_parent_dashboard.sql`](supabase/migrations/20260828_parent_dashboard.sql) after the base schema. It adds location storage, new event types, and the limited child last-seen permission.
3. Deploy the pairing functions, including the new validity chooser:

   ```powershell
   npx supabase@latest functions deploy create-pairing --use-api
   npx supabase@latest functions deploy claim-child-device --no-verify-jwt --use-api
   ```

4. On the parent phone, sign in and generate a pairing code. On the child phone, paste it in **Set up this child device**, then grant Usage Access and enable protection.
5. To share location, the parent enables the policy, then the child phone explicitly grants Android location permission and starts **visible location sharing**.

## Enforcement boundaries

- Schedules and an already-downloaded pause continue offline. The active pause is stored separately so a later policy refresh cannot cancel it.
- A remote command is received on the child phone's next policy sync (currently every 30 seconds).
- Standard mode detects and blocks a new app after installation; true approval *before* installation requires Android Device Owner enrollment on a dedicated child phone.
- The app does not claim to cut all device internet traffic yet. A transparent VPN/DNS module is the next layer for a stronger device-wide internet pause and domain filter.
- Blocking `/shorts/` is reliable in the supervised browser. Selective Shorts filtering inside the official YouTube app is not guaranteed.
- Location is deliberately not hidden: it has a persistent Android foreground-service notification and requires the child phone's permission.
- Do not collect private messages, keystrokes, camera, microphone, or hidden screenshots.

## Remaining advanced modules

1. Supabase Realtime or FCM delivery for faster remote commands and parent notifications.
2. Device-event history, safe-place geofences, location history, and retention/deletion controls.
3. Transparent VPN/DNS filtering.
4. Android Device Owner enrollment for stronger tamper resistance and true pre-install controls.
