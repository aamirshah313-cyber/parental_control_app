# Guardian Link

Android-first parental-control foundation with local time rules, immediate managed-app pause, basic YouTube Shorts/keyword policy evaluation, and a privacy-preserving Supabase control plane.

## What works locally now

- A bedtime schedule (9 PM–7 AM) for managed apps.
- Pause now, pause 30 minutes, and resume controls.
- YouTube is the default managed app (`com.google.android.youtube`).
- Local keyword matching and a `/shorts/` URL policy engine for a future supervised browser.
- A supervised browser that blocks configured domains, `/shorts/` URLs, and matching page titles locally.
- A foreground protection service that checks the foreground managed app every 3 seconds.

The parent controls currently demonstrate on the same phone. The child sync worker downloads the active Supabase policy and accepts the latest pause/resume command once the device has paired credentials.

## Android Studio setup

1. Open this folder in Android Studio Hedgehog or newer.
2. Let Android Studio generate the Gradle wrapper if prompted.
3. Use JDK 17 and install Android SDK Platform 35.
4. Run on a physical Android 8.0+ device.
5. In **Set up this child device**, grant **Usage Access**, then enable protection.

## Supabase setup

1. Create a Supabase project on the Free plan.
2. Run [`supabase/schema.sql`](supabase/schema.sql) in the SQL Editor.
3. Create a local `local.properties` file containing `SUPABASE_URL=https://YOUR_PROJECT.supabase.co` and `SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY`. Never put the Supabase service-role key in the Android app.
4. Deploy the included pairing functions: `supabase functions deploy create-pairing` and `supabase functions deploy claim-child-device`.
5. After parent sign-in, call `create-pairing` with `family_id` and `child_name`. It returns a single-use code valid for 10 minutes. Paste that code into **Set up this child device** on the child phone. The child receives its own scoped Auth session; it never receives the parent session.
6. The child’s active protection service polls for a policy or pause/resume command every 30 seconds; this is the no-extra-dependency fallback. Add a Supabase Realtime subscription or FCM push path for genuinely immediate remote pause commands.

## Enforcement boundaries

- Schedules and an already-downloaded pause continue offline.
- A remote pause can only be received when the child device reconnects.
- This starter locks managed apps; it does not claim to cut off all device internet traffic.
- Blocking `youtube.com/shorts/` is reliable in a supervised browser. Selective Shorts filtering inside the official YouTube app is not guaranteed.
- Do not collect private messages, keystrokes, camera, microphone, or hidden screenshots.

## Next implementation slice

1. Parent authentication and QR pairing.
2. Sync policy JSON and command acknowledgements through Supabase.
3. Add a supervised browser screen using `PolicyEngine.pageDecision`.
4. Add a transparent VPN/DNS filtering mode only if whole-device web filtering is required.
