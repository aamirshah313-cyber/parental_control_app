# Production delivery and managed-device rollout

Guardian Link works today with a visible child foreground service and a 30-second cloud sync. This keeps the starter deployment free of paid APIs, but Android can delay background networking. Use the two upgrades below only after the normal parent/child flow is stable.

## 1. Reliable alert delivery with Firebase Cloud Messaging

FCM itself has no charge. It needs a Firebase project, an Android app entry matching the final `APPLICATION_ID`, and a `google-services.json` file generated for that exact package name.

1. Create or choose a Firebase project owned by the app publisher.
2. Add the final Android application ID and download `google-services.json`.
3. Add Firebase Messaging to the Android build and register each parent and child device token only after the user accepts notifications.
4. Store the token server-side with the device role and rotate/delete it on sign-out, family removal, or notification permission removal.
5. Have a trusted Edge Function send data-only notifications for `pause`, `grant_time`, SOS, app approvals, and safe-place events. The app must still fetch the scoped record from Supabase before acting; notification data is never trusted as policy.
6. Retain polling as the offline fallback and record command acknowledgements. Do not put location, message body, pairing codes, or credentials inside an FCM payload.

This repository intentionally does not include a Firebase project file or server credential. Adding one without ownership and notification consent would make an insecure release.

## 2. True pre-install approval with Android Device Owner

Standard Android apps can observe an install and block it on first launch. Blocking before installation needs a Device Owner policy controller on a dedicated or factory-reset child device. It cannot be retrofitted silently onto an ordinary personal phone.

1. Publish a signed release APK/AAB using the final application ID and an owned signing key.
2. Implement and test a `DeviceAdminReceiver` and Device Policy Controller only on test hardware.
3. During the Android setup wizard, provision the phone using the approved QR/zero-touch/EMM enrollment method for your organization.
4. Present a child/guardian disclosure describing what is managed, how to leave the program, and emergency access.
5. Apply package-install policy only after the parent policy is downloaded and verified. Keep a local emergency/essential-app allowlist.
6. Test unenrollment, factory reset, lost-device handling, and all supported Android versions before offering it to families.

Do not advertise standard mode as pre-install blocking. The app UI should continue to state that new apps are detected and prevented from opening until a parent approves them.

## Release checks

- Use a privacy policy and a family/device retirement or deletion workflow.
- Set data-retention periods for locations, health reports, requests, and event logs.
- Verify each RLS policy with a parent account and a child account from different families.
- Test no-network behavior: the last downloaded policy must remain enforced, and no remote action may be claimed as immediate until acknowledged.
- Follow Google Play Families, background location, device-management, and notification policies before distribution.
