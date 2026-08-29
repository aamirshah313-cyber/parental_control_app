# Guardian Link app-flow architecture

## Entry choices

```
Welcome
├── Parent / guardian → sign in or create account → family dashboard
├── Child device → pairing and visible Android setup → child hub
└── Guest mode → local demo dashboard (no sign-in, no remote data)
```

Guest mode is intentionally a complete interactive demonstration of the product structure: overview, controls, app controls, time requests, safety, family chat preview, alerts, and Guardian Guide. It does not call Supabase or expose a real family, child, location, messages, or controls. A parent signs in only when they want to use real protected family data.

## Parent navigation

```
Parent dashboard
├── Overview: selected child, screen-time dial, current location preview
├── Controls: pause, schedules, app approval, app list, time requests
├── Safety: browser rules, location, safe places, activity
└── Family: pairing, alerts, family chat, help, sign out
```

Detail pages are children of the parent dashboard: Manage Apps, Time Requests, Live Location, Location Log, Safety Activity, Quick Messages, Family Chat, and Guardian Guide. Android Back returns to the still-open parent dashboard, keeping its selected child and section.

## Child navigation

```
Child hub
├── Pairing / setup / sync
├── Protection, apps, time request, SOS
├── Location and supervised browser
└── Communication: quick messages, family chat, Guardian Guide
```

The child hub stays in the task while a detailed screen opens. Back returns to the hub. The supervised browser’s back affordance first walks browser history; at the initial page it closes the browser and returns to the child hub.

## Back-stack rules

1. A nested page uses Android Back to return to its immediate hub, never opens `MainActivity` directly.
2. Guest subpages return to Guest Overview; Back from Guest Overview exits Guest mode to Welcome.
3. A notification that opens a message now creates `Parent hub → message` or `Child hub → message`; Back returns to the relevant hub.
4. External maps are deliberately another app. Back there returns to the Guardian Link detail page that opened it.
5. A protection-block screen is the exception: “Go to home screen” exits the task deliberately to avoid bypassing a block.

## Data boundary per flow

| Flow | Needs sign-in/pairing | Guest equivalent |
| --- | --- | --- |
| Parent dashboard, controls, location, apps | Parent session + family ownership | Local demo state |
| Child rules, status, SOS, inventory | Paired child session | Explanatory/demo state only |
| Messages and voice notes | Parent owner or paired child JWT | Local preview; no message is sent |
| Guardian Guide | No | Fully available, on-device |

This boundary keeps guest exploration useful without turning it into an unauthenticated route to a real child’s data or controls.
