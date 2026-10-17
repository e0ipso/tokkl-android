# Tokkl for Android PRD

A native Android client for a self-hosted Tokkl server. It does the daily work of Tokkl's web app and adds what a PWA cannot: the running timer in a notification, a home screen widget, a Quick Settings tile, and starting and stopping timers with no connection to the server. A later version talks to Toggl Track through the same API.

- Owner: Tokkl contributors
- Status: requirements gathered in an interview on 2026-10-17 (appendix). No code yet. Anything marked "proposed" came from the interviewer and has not been reviewed.
- Last updated: 2026-10-17

## 1. Why this exists

Tokkl's web app works on a phone and installs as a PWA. On Android a PWA cannot keep the running timer in a notification, sit on the home screen as a widget, add a Quick Settings tile, or start a timer while the server is out of reach. Tokkl's own PRD lists that last gap as risk 3, and its offline outbox (C4) is still planned. People also prefer native apps.

Tokkl set itself a bar: if starting a timer is slower than in Toggl, the owner drifts back to Toggl. The same bar applies here, with the PWA as the thing to beat. The app has done its job when the owner no longer opens the PWA on the phone.

### Goals

1. Start and stop the timer from the notification, the widget, the Quick Settings tile or the app.
2. Do the daily work of Tokkl's web app on the phone: timer, manual entries, history and edits, the week calendar, projects, reports.
3. Keep working with no route to the server, and send the changes when it comes back.
4. Connect to any Tokkl server, however it is set up.
5. Ship on F-Droid.

### Non-goals

- Toggl Track as a backend in the first version. It comes later through the base URL (section 3).
- A sync engine or a full local copy of the account.
- Reports, or history older than the cache, without a connection.
- Tokkl's admin pages, Toggl CSV import, JSON export and restore, and bulk edit. They stay in the web app.
- A Wear OS app and a separate tablet design.
- Client certificates (mTLS).
- Proprietary libraries, including Google Play Services and Firebase. There is no push through FCM.

## 2. User and context

The first user is the person who runs the Tokkl server. They track client work and personal projects and want every timer start, stop and fix on the phone to happen in this app. After them come F-Droid users who run their own Tokkl server.

Those servers differ. Some sit behind a reverse proxy with a public certificate, some use a tailnet name with a certificate from `tailscale cert`, some use a self-signed certificate, and some run Tokkl's default: plain HTTP on port 8080.

Usage pattern this design optimizes for:

- Starting and stopping timers away from the laptop, often from the notification, widget or tile without opening the app.
- Fixing a forgotten or wrong entry.
- Checking this week's or month's totals.
- Stretches with no route to the server, such as away from home with the VPN down.

## 3. Backends

Known from the tokkl repo (`../tokkl/docs/PRD.md` and source, read 2026-10-17):

- Tokkl implements a subset of the Toggl Track API v9 at `https://<server>/api/v9` and of the Reports API v3 at `https://<server>/reports/api/v3`. A client that accepts a base URL needs no other change to talk to it.
- Authentication on both is HTTP Basic, with `<api_token>:api_token` or `<email>:<password>`. Failed auth returns 403. Tokkl returns 429 with `Retry-After` after too many failed password attempts.
- Tokkl adds two fields to `GET /api/v9/me`: `role` and `time_resolution_minutes`. Stopped entries snap to that resolution on the server.
- Some web app features use Tokkl-only endpoints outside the Toggl API: the trash (`GET/DELETE /api/app/trash`, `DELETE /api/app/trash/{id}`) and undoing a delete (`POST /api/app/time_entries/{id}/restore`). Favorites are a Tokkl key in `/me/preferences`, up to 9 of them.
- Tokkl's desktop companions sign in with the API token by default, or email and password, and keep the secret in the platform keystore.
- The Tokkl server listens on plain HTTP (`app.listen` in `packages/api/src/index.ts`). HTTPS comes from a reverse proxy or `tailscale serve` in front of it.
- Toggl Track's API lives at `api.track.toggl.com`.

Decisions:

- The first version targets Tokkl only. Toggl Track comes later by pointing the app at Toggl's base URL, because both speak the same API. The Toggl version hides the Tokkl-only features above.
- The app has no sync engine. The server is the source of truth. The phone keeps a cache of what it last loaded and a queue of changes it has not sent yet (section 5.9).
- The app makes few requests (principle 3), so that the base URL change works later within Toggl's quotas (risk 2).

## 4. Principles

1. **Data stays between the phone and the server.** No telemetry, analytics, crash reporting or ads, and no request to any host other than the configured server. This carries Tokkl's first principle over and matches F-Droid's rules.
2. **Offline first.** Tapping Start or Stop never fails because the server is out of reach. The change is kept on the phone and sent later.
3. **Few requests.** The app refreshes on events (app opened, timer changed, network back), not on a short polling timer. The running clock ticks on the phone from the start time.
4. **Tokkl behavior.** Same time parsing, favorites, grouping and undo as the web app, in the account's timezone, so moving between the laptop and the phone needs no relearning.
5. **Loud look, plain function.** Tokkl's sixth principle applies as written: any style that costs a readable number, a reachable control or a passing test gets dropped.

### Visual direction

- **Components.** Material 3 through Compose Material 3, the official Material toolkit for Jetpack Compose.
- **Colour.** Always Tokkl's palette, never the wallpaper. Light theme: sand background, ink text, sunset orange primary, aqua secondary, hot pink and lime for states. Dark theme: navy background with the neon counterparts. The source of truth is the colour tokens in `../tokkl/packages/web/src/styles.css`. Dynamic color is off, at most an opt-in setting.
- **Type.** Anybody for the running clock, totals and headings, bundled with the app. Tokkl already ships it under the SIL Open Font License 1.1 (`../tokkl/packages/web/public/fonts/OFL.txt`). Everything else uses the system font.
- **Contrast and signals.** WCAG AA, as in Tokkl: 4.5:1 for text, 3:1 for non-text elements. Colour is never the only signal for a state.
- **Devices.** Phones first. Layouts adapt to tablets and foldables, with no separate design.

## 5. Functional requirements

The first release is feature complete: it ships every requirement below, so they carry no priority. Every row is Planned.

### 5.1 Server and sign-in

| ID | Requirement |
|---|---|
| S1 | Sign in with a server URL and the API token (default), or email and password. The URL may carry a path, such as `https://example.com/tokkl`. |
| S2 | Accept HTTPS with a publicly trusted certificate or with a CA the user installed in Android. |
| S3 | Accept HTTPS with a self-signed certificate after the user reads a disclaimer and trusts it. The app shows the certificate's SHA-256 fingerprint, trusts that certificate only, and asks again when it changes. It never accepts any certificate without a check. |
| S4 | Accept plain `http://` after a warning, stronger than the one in S3, that the token travels unencrypted unless the network encrypts it (Tailscale does). |
| S5 | Keep the token or password encrypted with a key in the Android Keystore. |
| S6 | One server and one account at a time. Signing out warns when unsent changes would be lost, then clears the cache and the queue. |
| S7 | Read the timezone, first day of the week and `time_resolution_minutes` from `GET /api/v9/me`, and use the account timezone everywhere. |

### 5.2 Timer

| ID | Requirement |
|---|---|
| T1 | Start and stop the timer, and edit the running entry: description, project, task, tags, billable. |
| T2 | Continue a past entry. |
| T3 | Show stopped entries as the server returns them, after it snaps them to `time_resolution_minutes`. |
| T4 | Description autocomplete from recent entries. |
| T5 | Favorites: start any of up to 9 pinned entries in one tap. They are the same favorites the web app shows, read from and written to `/me/preferences`. |

### 5.3 Manual entry

| ID | Requirement |
|---|---|
| M1 | Add a past entry by start and end or by duration, with Tokkl's parsing: `9`, `930`, `9:30`, `4pm`, entries across midnight, and durations like `1h 30m`, `90`, `1,5h`. |

### 5.4 History and editing

| ID | Requirement |
|---|---|
| H1 | Day-grouped history that loads older days from the server as you scroll. |
| H2 | Edit every field of an entry. |
| H3 | Delete with undo, using Tokkl's restore endpoint. |
| H4 | Group similar entries within a day, as the web app does. |
| H5 | Duplicate an entry. |
| H6 | Split an entry. |
| H7 | Trash: list deleted entries, restore them, purge one or all. |

### 5.5 Calendar

| ID | Requirement |
|---|---|
| K1 | Week view in the account timezone. |
| K2 | Create, move and resize entries by touch: long-press then drag. |

### 5.6 Projects, clients, tags and tasks

| ID | Requirement |
|---|---|
| P1 | Pick a project, task and tags wherever an entry is edited, including archived items when an old entry uses them. |
| P2 | Create, edit and archive projects: colour, client, rate, estimate, billable default. |
| P3 | Create, edit, archive and restore clients. |
| P4 | Create, rename and delete tags. |
| P5 | Create, edit and delete tasks within a project. |

### 5.7 Reports

| ID | Requirement |
|---|---|
| R1 | Summary report: totals, billable share, amounts, a daily chart and a project breakdown, for any date range. |
| R2 | Detailed report: every matching entry, paged by the server. |
| R3 | Weekly report: the project-by-week grid. |
| R4 | Filters by project, client, tag and billable, as in the web app. |
| R5 | Reports need the server. With no connection the screen says so instead of showing stale numbers. |

### 5.8 Android integration

| ID | Requirement |
|---|---|
| N1 | An ongoing notification while a timer runs, with the description, project, a ticking elapsed time and a Stop action. |
| N2 | On Android 16 and later, promote that notification to a Live Update, which shows as a chip in the status bar, at the top of the notification drawer and on the lock screen. Elsewhere it stays a regular ongoing notification. |
| N3 | A home screen widget with the running timer, Start and Stop, and the favorites. |
| N4 | A Quick Settings tile that shows whether a timer runs. Tapping it stops the running timer, or continues the last entry when none runs. |
| N5 | Ask for the notification permission on Android 13 and later when the first timer starts, and say what is lost without it. |
| N6 | Launcher shortcuts (long-press the app icon) for the favorites. One tap starts that timer. |

### 5.9 Offline

| ID | Requirement |
|---|---|
| O1 | Start, stop, create and edit entries with no route to the server. Each change waits in a queue on the phone, marked as not sent, and goes out in order when the server is reachable. A timer started offline keeps its real start time. |
| O2 | Cache what the app last loaded: projects, clients, tags, tasks, favorites and about two weeks of history. All of it can be read offline, and entries can be edited. |
| O3 | When the phone and another client both changed an entry, the change that reaches the server last wins. |
| O4 | Send the queue in the background when the network comes back, with the app closed. |
| O5 | Show how many changes are not sent and when the server last answered. |

### 5.10 Settings

| ID | Requirement |
|---|---|
| G1 | Theme: light, dark, or follow the system. |
| G2 | Opt-in dynamic color. |

## 6. Architecture

Proposed. Nothing here is built.

### 6.1 Stack

- Kotlin and Jetpack Compose with Compose Material 3. Android has been Kotlin-first since Google I/O 2019, and Compose is not available from Java ([Android's Kotlin-first approach](https://developer.android.com/kotlin/first)).
- Jetpack Glance for the widget. It builds home screen widgets from composables on the Compose runtime ([Glance](https://developer.android.com/develop/ui/compose/glance)).
- A `TileService` for the Quick Settings tile.
- Room (SQLite) for the cache and the queue of unsent changes.
- WorkManager to send the queue when the network comes back.
- OkHttp and kotlinx.serialization for the API, with a trust manager that adds the pinned self-signed certificate (S3).
- A Kotlin port of the duration and time-of-day parsing in `../tokkl/packages/common`, checked against the same cases as the TypeScript tests.

Every dependency must be free software (section 8.1).

### 6.2 Network security

Since Android 9 an app cannot use cleartext HTTP unless it opts in, and since Android 7 it trusts only system CAs by default ([network security configuration](https://developer.android.com/privacy-and-security/security-config)). The app's network security config therefore permits cleartext (S4) and trusts user-added CAs (S2). The self-signed certificate in S3 is pinned in the app, separate from Android's trust store.

### 6.3 Data flow

- The server is the source of truth. The phone holds the cache and the queue.
- The app refreshes the running entry and the visible data when it opens, after a change, and when the network comes back. It does not poll on a short timer.
- The notification, widget and tile read the running entry from the phone, so they react at once, and the queue sends the change behind them.

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| Speed | A tap on Start or Stop in the app, notification, widget or tile shows the new state within 200 ms, online or offline, before the server answers. A cold start shows the timer screen from the cache within 1.5 s on a mid-range phone, measured with Jetpack Macrobenchmark on a real device. |
| Compatibility | Android 8.0 (API 26) and later. Phones first; tablets and foldables get adaptive layouts. |
| Privacy | No network traffic to any host but the configured server. |
| Battery | No wakeups while no timer runs and the queue is empty. No short-interval polling. |
| Accessibility | WCAG AA contrast, TalkBack labels on every control, colour never the only signal, layouts that survive the largest font scale. |
| Language | English. All text lives in string resources. No other language is planned; contributors can add translations. |
| Tests | JVM unit tests, Compose UI tests, and instrumented tests on an emulator against a real Tokkl server from the `ghcr.io/e0ipso/tokkl` image, never a fake one. |

## 8. Distribution and release

### 8.1 F-Droid

From F-Droid's docs, read 2026-10-17:

- The app must be free software under a free license, built by F-Droid from source with a free toolchain, with no proprietary libraries such as Google Play Services or Firebase ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)).
- Store text and images live in the repo under `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt` (4000 at most), `changelogs/<versionCode>.txt`, `images/icon.png` and `images/phoneScreenshots/` ([descriptions and graphics](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)).
- With `UpdateCheckMode: Tags`, F-Droid checks every git tag for the highest version code, reading it from the Gradle build by default ([build metadata](https://f-droid.org/docs/Build_Metadata_Reference/)).
- If the build is reproducible, F-Droid can publish the APK signed with the project's own key. `Binaries` points at a versioned APK URL, such as a GitHub release asset, and `AllowedAPKSigningKeys` holds the SHA-256 fingerprint of the signing certificate. F-Droid publishes only when its build matches ([reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)). The docs ask for pinned build-tools and no embedded timestamps.

Decisions: the license is MIT, like Tokkl. The application ID is `com.github.e0ipso.tokkl`, the same ID as the macOS and KDE companions.

### 8.3 Versions and stores

Decided by the interviewer on 2026-10-17, when the owner left these to them:

- F-Droid only. No Play Store listing is planned. The signed APK on each GitHub release (section 8.2) serves anyone who sideloads.
- The app has its own version, independent of Tokkl's, with its own `v*` tags and changelog. Tying the two would need releases across two repos, and F-Droid reads the version code from this repo's tags.
- The oldest supported server is Tokkl 1.2.0, the first release whose `GET /api/v9/me` returns `time_resolution_minutes`. The trash, restore and favorites endpoints were already in it. Tokkl does not report its version over the API, so the app checks for that field: when `GET /api/v9/me` lacks it, sign-in fails with a message that the server needs Tokkl 1.2.0 or later.

### 8.2 GitHub and CI

The repo is local only for now. When it moves to `github.com/e0ipso/tokkl-android`, it copies Tokkl's setup:

- One `ci.yml` workflow on pull requests and pushes to `main`, with every action pinned to a commit SHA and its version in a comment, and Dependabot for updates.
- commitlint with Tokkl's rules: Conventional Commits, no AI or co-author trailers, no ticket IDs in the subject. A husky hook runs it locally.
- Jobs for the Gradle build, lint and unit tests, and for instrumented tests on an emulator. GitHub's Ubuntu runners need a udev rule before the emulator can use KVM ([android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner)).
- semantic-release on pushes to `main` with Tokkl's release rules and `v${version}` tags. It writes `CHANGELOG.md` and the version into the Gradle build, derives `versionCode` from the version, and attaches the signed release APK to the GitHub release, where F-Droid's `Binaries` can find it.
- The signing key lives in GitHub Actions secrets, never in the repo.

## 9. Risks and open questions

1. Tokkl's PRD lists native mobile apps as a non-goal, with exceptions only for the three desktop companions. That non-goal and its section 5.8 get a new row when this app's first release ships, not before. On 2026-10-17 tokkl's `main` checkout had uncommitted edits to its PRD, so it was left alone.
2. Toggl Track's API quotas. Since 2025-09-05, workspace and organization requests are capped per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium, with custom limits on Enterprise. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402 ([Toggl support](https://support.toggl.com/api-webhook-limits), read 2026-10-17). Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. If this app polled like that, pointing it at Toggl would hit the Free quota within 15 minutes.
3. F-Droid's NonFreeNet anti-feature covers apps that "promote or depend entirely on a proprietary network service" ([Anti-Features](https://f-droid.org/docs/Anti-Features/), read 2026-10-17). It does not apply while the app talks to Tokkl only. Once Toggl support lands, F-Droid's reviewers decide whether supporting Toggl counts as promoting it.
4. Tokkl-only features (trash, undo of a delete, favorites, time resolution) will not work against Toggl. The Toggl version must detect the backend and hide them.
5. The Kotlin port of Tokkl's time parsing can drift from the TypeScript original. Sharing the test cases between the two repos would catch that.
6. Live Updates depend on the device and on the Android 16 release it runs. The notification has to work without them.
7. Some phone makers restrict background work, which can delay sending the queue. To verify on real devices.
8. Build environment. This machine had no JDK or Android SDK on 2026-10-17. `/dev/kvm` exists and the user is in the `kvm` group, so a headless emulator is possible.

## Appendix: interview record

Answers as given on 2026-10-17. Later answers override earlier ones.

1. Who is it for? Public users, distributed through F-Droid.
2. Why native? People prefer native apps. Scope is a fully featured client, plus deep Android integration: an ongoing notification with the running timer and a Stop button, a home screen widget, a Quick Settings tile, and offline start and stop that syncs later.
3. Toggl at full parity? No sync engine. Tokkl and Toggl share the API, so a base URL setting is enough for Toggl. The first version targets Tokkl only.
4. Server unreachable when you tap Start? Offline first. The app keeps the change on the phone, marks it as not sent, and sends it when the server is back.
5. What works offline? What the app last loaded: projects, clients and tags for picking, and recent history (about two weeks), which can be edited. Reports and older history need the server. When the phone and the web both changed an entry, the change that reaches the server last wins.
6. What does fully featured cover? The daily-use features: timer with favorites and continue, manual entry with Tokkl's time parsing, day-grouped history with edit, duplicate, split, undo and trash, the week calendar, projects, clients, tags and tasks, and the summary, detailed and weekly reports. Left to the web app: admin pages, Toggl CSV import, JSON export and restore, bulk edit.
7. Success after six months? The owner no longer opens the PWA on the phone. F-Droid users are a bonus, but the app must handle servers set up differently from the owner's.
8. Which server setups? Any HTTPS server. A self-signed certificate works after the user accepts a "trust this certificate" disclaimer.
9. Plain HTTP? Yes, behind a stronger warning than the self-signed one.
10. Theme? Always Tokkl's palette, light and dark, on Material 3 components, with Anybody for the running clock and totals. Dynamic color off, or at most an opt-in setting.
11. Devices and versions? Phones first, with layouts that adapt to tablets and foldables but no separate design. No Wear OS app in the first version. Android 8.0 (API 26) or newer. On Android 16 the running timer is a Live Update; older versions get a regular ongoing notification.
12. Summary and defaults? Confirmed: API token or email and password, one account at a time, English only, Kotlin and Jetpack Compose, MIT, `com.github.e0ipso.tokkl`, F-Droid only for now. No GitHub repo yet, but plan for one with GitHub Actions like Tokkl's.
13. What goes in the first release? Everything in section 5. The first release is feature complete.
14. What do priorities mean? Nothing. Everything has to be done, so section 5 has no priority column.
15. Quick Settings tile (N4)? Keep it as written: a tap stops the running timer, or continues the last entry when none runs.
16. Launcher shortcuts (N6)? Yes: long-pressing the app icon lists the favorites, and a tap starts that timer.
17. Tests against a real Tokkl server? Yes, the `ghcr.io/e0ipso/tokkl` image, not a fake.
18. Play Store, versioning, speed targets, languages, tokkl's PRD? Left to the interviewer ("just do it"). Decisions in sections 7, 8.3 and risk 1.
