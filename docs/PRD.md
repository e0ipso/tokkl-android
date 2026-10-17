# Tokkl for Android PRD

A native Android client for a self-hosted Tokkl server and for Toggl Track.

- Status: collecting input. Sections marked "Open" wait on the interview.
- Last updated: 2026-10-17

## 1. Why this exists

Open.

## 2. User and context

Open.

## 3. Backends

Known from the tokkl repo (`../tokkl/docs/PRD.md`, read 2026-10-17):

- Tokkl implements a subset of the Toggl Track API v9 at `https://<server>/api/v9` and of the Reports API v3 at `https://<server>/reports/api/v3`. A client that accepts a base URL needs no other change to talk to it.
- Authentication on both is HTTP Basic, with `<api_token>:api_token` or `<email>:<password>`. Failed auth returns 403. Tokkl returns 429 with `Retry-After` after too many failed password attempts.
- Tokkl adds two fields to `GET /api/v9/me`: `role` and `time_resolution_minutes`. Stopped entries snap to that resolution on the server.
- Tokkl's own clients (the KDE, macOS and GNOME companions) sign in with the API token by default, or email and password, and keep the secret in the platform keystore.
- Toggl Track's API lives at `api.track.toggl.com`.

Decided (interview, 2026-10-17): the first version targets Tokkl only. Toggl Track comes later by pointing the app at Toggl's base URL, because both speak the same API. The app has no sync engine and no local mirror of the account; it reads from and writes to the server.

Open: how many requests the app makes. Toggl's quotas (risk 2) do not apply to Tokkl, but they decide whether "change the base URL" works later.

## 4. Principles

Open.

### Visual direction

Open. Starting point: Material 3 as the design system, with colour, type and tone taken from Tokkl's visual direction (`../tokkl/docs/PRD.md` section 4).

## 5. Functional requirements

Open.

## 6. Architecture

Open.

## 7. Non-functional requirements

Open.

## 8. Distribution and release

- The app ships on F-Droid (interview, 2026-10-17). That means FLOSS code under a FLOSS license, a build F-Droid can run from source with a FLOSS toolchain, and no proprietary libraries such as Google Play Services or Firebase ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), read 2026-10-17).
- Open: Play Store as well, or F-Droid only.

## 9. Risks and open questions

1. Tokkl's PRD lists native mobile apps as a non-goal, with exceptions only for the three desktop companions. If this app ships, that non-goal and its section 5.8 need a new row.
2. Toggl Track's API quotas. Since 2025-09-05, workspace and organization requests are capped per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium, with custom limits on Enterprise. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402 ([Toggl support](https://support.toggl.com/api-webhook-limits), read 2026-10-17). Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. If this app polls like that, pointing it at Toggl later will hit the Free quota within 15 minutes.
3. F-Droid's NonFreeNet anti-feature covers apps that "promote or depend entirely on a proprietary network service" ([Anti-Features](https://f-droid.org/docs/Anti-Features/), read 2026-10-17). Toggl Track is proprietary. Tokkl is a free alternative, so the app does not depend entirely on Toggl, but the page does not say whether supporting Toggl counts as promoting it. Expect F-Droid reviewers to decide whether to add the label.
4. Build environment. This machine had no JDK or Android SDK on 2026-10-17. `/dev/kvm` exists and the user is in the `kvm` group, so a headless emulator is possible.

## Interview log

Answers recorded as the interview goes, before they move into the sections above.

1. Who is it for? Public users, distributed through F-Droid. (2026-10-17)
2. Why native? People prefer native apps. Scope is a fully featured client, plus deep Android integration: an ongoing notification with the running timer and a Stop button, a home screen widget, a Quick Settings tile, and offline start and stop that syncs later. (2026-10-17)
3. Toggl at full parity? No sync engine. Tokkl and Toggl share the API, so a base URL setting is enough for Toggl. The first version targets Tokkl only. (2026-10-17)
4. Server unreachable when you tap Start? Offline first. The app keeps the change on the phone, marks it as not sent, and sends it when the server is back. Open: how much the phone keeps for reading, given answer 3 rules out a sync engine. (2026-10-17)
5. What works offline? What the app last loaded: projects, clients and tags for picking, and recent history (about two weeks), which can be edited. Reports and older history need the server. When the phone and the web both changed an entry, the change that reaches the server last wins. (2026-10-17)
6. What does fully featured cover? The daily-use features: timer with favorites and continue, manual entry with Tokkl's time parsing, day-grouped history with edit, duplicate, split, undo and trash, the week calendar, projects, clients, tags and tasks, and the summary, detailed and weekly reports. Out of scope, left to the web app: admin pages, Toggl CSV import, JSON export and restore, bulk edit. (2026-10-17)
7. Success after six months? The owner no longer opens the PWA on the phone; every timer start, stop and fix happens in the app. F-Droid users are a bonus, but the app must handle servers set up differently from the owner's. Open: translations. (2026-10-17)
8. Which server setups? Any HTTPS server. A self-signed certificate works after the user accepts a "trust this certificate" disclaimer. Proposed: the app trusts that one certificate by its SHA-256 fingerprint and asks again when it changes. Open: plain HTTP, which is what Tokkl serves without a reverse proxy (`app.listen` in `packages/api/src/index.ts`). (2026-10-17)
9. Plain HTTP? Yes, behind a stronger warning than the self-signed one. Tokkl's default install serves plain HTTP, and a Tailscale address is already encrypted by WireGuard. (2026-10-17)
10. Theme? Always Tokkl's palette, light and dark, on Material 3 components, with Anybody for the running clock and totals. Dynamic color off, or at most an opt-in setting. (2026-10-17)
11. Devices and versions? Phones first, with layouts that adapt to tablets and foldables but no separate design. No Wear OS app in the first version. Android 8.0 (API 26) or newer. On Android 16 the running timer is a Live Update with a status bar chip; older versions get a regular ongoing notification. (2026-10-17)
