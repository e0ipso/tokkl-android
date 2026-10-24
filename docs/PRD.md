# Tokkl for Android PRD

A native Android client for a self-hosted Tokkl server. It does the daily work of Tokkl's web app and adds what a PWA cannot: the running timer in a notification, a home screen widget, a Quick Settings tile, and starting and stopping timers with no connection to the server. A later version talks to Toggl Track through the same API.

- Owner: Tokkl contributors
- Status: requirements gathered in interviews on 2026-10-17. No code yet. Anything marked "proposed" came from the interviewer and has not been reviewed.
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
- Idle detection and "forgot to start" reminders, which would need wakeups while no timer runs. The long-running timer reminder in N7, requested by the owner on 2026-10-20, and the Pomodoro session in section 5.12 are in scope.
- Push from the server in the first version. The phone learns about changes made elsewhere on the events in section 6.3. UnifiedPush is a later option (risk 10).
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
- Some web app features use Tokkl-only endpoints outside the Toggl API: the trash (`GET/DELETE /api/app/trash`, `DELETE /api/app/trash/{id}`) and undoing a delete (`POST /api/app/time_entries/{id}/restore`). Timer templates are the web app's favorites, a Tokkl key named `favorites` in `/me/preferences`, up to 9 of them. Report requests also take Tokkl's `exclude_project_ids`, `exclude_client_ids` and `exclude_tag_ids`, the web app's "Is not" filters, which Toggl's Reports API v3 lacks.
- Tokkl's desktop companions sign in with the API token by default, or email and password, and keep the secret in the platform keystore.
- The Tokkl server listens on plain HTTP (`app.listen` in `packages/api/src/index.ts`). HTTPS comes from a reverse proxy or `tailscale serve` in front of it.
- Toggl Track's API lives at `api.track.toggl.com`.

Decisions:

- The first version targets Tokkl only. Toggl Track comes later by pointing the app at Toggl's base URL, because both speak the same API. The Toggl version hides the Tokkl-only features above.
- The app has no sync engine. The server is the source of truth. The phone keeps a cache of what it last loaded and a queue of changes it has not sent yet (section 5.9).
- The app makes few requests (principle 3), so that the base URL change works later within Toggl's quotas (risk 2).

## 4. Principles

1. **Data stays between the phone and the server.** No telemetry, analytics, crash reporting or ads, and no request to any host other than the configured server. An explicit Share CSV action may grant the Android app the user chooses temporary read access to that report file; the receiving app controls any onward sharing. This carries Tokkl's first principle over and matches F-Droid's rules.
2. **Offline first.** Tapping Start or Stop never fails because the server is out of reach. The change is kept on the phone and sent later.
3. **Few requests.** The app refreshes on events (app opened, timer changed, network back), not on a short polling timer. The running clock ticks on the phone from the start time. A Pomodoro session (section 5.12) adds no polling. It runs on the phone, its timing comes from alarms, and its only requests are the queued changes for the entries it saves or stops. The four defaults arrive with the preferences the app already reads, and changing one is a single write.
4. **Tokkl behavior.** Same time parsing, timer templates (the web app's favorites), grouping and undo as the web app, in the account's timezone, so moving between the laptop and the phone needs no relearning.
5. **Loud look, plain function.** Tokkl's sixth principle applies as written: any style that costs a readable number, a reachable control or a passing test gets dropped.

### Visual direction

- **Components.** Material 3 through Compose Material 3, the official Material toolkit for Jetpack Compose.
- **Colour.** Always Tokkl's palette, never the wallpaper. Tokkl's Spectrum palette, from the hues of the wordmark and the logo. Light theme: a warm white background, white surfaces, ink text and borders, a hot orange primary fill with a darker orange for primary text, a yellow fill for the selected segment, cyan secondary, hot pink for the running state and sky for billable. Dark theme: the wordmark's ink as background, deep indigo surfaces, white text and the neon counterparts. The source of truth is the colour tokens in `../tokkl/packages/web/src/styles.css`. Dynamic color is off, at most an opt-in setting.
- **Type.** Anybody for the running clock, totals and headings, bundled with the app. Tokkl already ships it under the SIL Open Font License 1.1 (`../tokkl/packages/web/public/fonts/OFL.txt`). Everything else uses the system font.
- **Spacing and type scale.** Spacing is 4, 8, 12, 16, 20, 24, 32 and 48dp, Tokkl's `--space-1` to `--space-8`. Screens keep a 16dp margin on compact widths and 24dp on medium and wider, and content is capped at 600dp. Anybody, always at width 85, sets the page title (28sp, weight 900, uppercase), clock (36sp, 800), totals (28sp, 800), day total (22sp, 800), day header (17sp, 900, uppercase), card title (18sp, 800) and section heading (16sp, 800). The system font sets body text at 16, 14 and 12sp, the small-caps label at 12sp weight 700 and the button label at 14sp weight 700. A monospace role sets fingerprints and codes.
- **Wordmark.** The owner's graffiti artwork, shipped as an image with the content description "Tokkl" for TalkBack, so it uses no font. It appears on the sign-in screen and the launch splash only, never in a page header or a top bar. In dark mode a 1dp halo in `--wordmark-halo` (#B9B5D6) traces its edge, because the artwork's navy outline matches the background there. Light mode draws no halo. See `../tokkl/docs/PRD.md`, Visual direction, Typeface.
- **Logo.** The logo appears only as the launcher icon, never beside the wordmark. The wordmark's "o" is a clock, but that clock is a letter of the wordmark, not the logo.
- **Texture.** Gradients or vectors only, never raster images, and never behind inputs or data. See `../tokkl/docs/PRD.md`, Visual direction, Texture.
- **Type effects.** Rotated or skewed type is decoration only. It never appears on controls, numbers or labels. See `../tokkl/docs/PRD.md`, Visual direction, Type effects.
- **Motion.** All motion respects the system's remove-animations setting. With it on, nothing animates. See `../tokkl/docs/PRD.md`, Visual direction, Motion.
- **Bundled assets.** Fonts and images ship with the app. The app loads none from outside, which follows principle 1. See `../tokkl/docs/PRD.md`, Visual direction, No outbound assets.
- **Contrast and signals.** WCAG AA, as in Tokkl: 4.5:1 for text, 3:1 for non-text elements. Colour is never the only signal for a state.
- **Devices.** Phones first. Layouts adapt to tablets and foldables, with no separate design.

## 5. Functional requirements

The first release is feature complete: it ships every requirement below, so they carry no priority. Every row is Planned.

### 5.1 Server and sign-in

| ID | Requirement |
|---|---|
| S1 | Sign in with a server URL and the API token (default), or email and password. The URL may carry a path, such as `https://example.com/tokkl`. Email and password sign in once: the app reads `api_token` from `GET /api/v9/me`, keeps the token and never stores the password. |
| S2 | Accept HTTPS with a publicly trusted certificate or with a CA the user installed in Android. |
| S3 | Accept HTTPS with a self-signed certificate after the user reads a disclaimer and trusts it. The app shows the certificate's SHA-256 fingerprint, trusts that certificate only, and asks again when it changes. It never accepts any certificate without a check. |
| S4 | Accept plain `http://` after a warning, stronger than the one in S3, that the token travels unencrypted unless the network encrypts it (Tailscale does). |
| S5 | Keep the token encrypted with a key in the Android Keystore. |
| S6 | One server and one account at a time. Signing out warns when unsent changes would be lost, then clears the cache and the queue. |
| S7 | Read the timezone, first day of the week and `time_resolution_minutes` from `GET /api/v9/me`, and use the account timezone everywhere. |
| S8 | No Android backup. The manifest sets `android:allowBackup="false"`, and `dataExtractionRules` exclude everything from `<cloud-backup>` and `<device-transfer>`, because on Android 12 and later `allowBackup="false"` alone does not stop device-to-device transfer on some phones ([Auto Backup](https://developer.android.com/identity/data/autobackup)). Keystore keys do not move to a new phone, so a restored token could not be decrypted, and a restored queue could replay old changes. On a new phone the user signs in again. |

### 5.2 Timer

| ID | Requirement |
|---|---|
| T1 | A labelled Fullscreen timer action opens a focused clock for the running or paused timer, with enlarged elapsed time, entry context, shared Pause/Resume/Stop, Edit and Exit. App navigation and history are hidden there and in its running editor. Back and Exit restore the selected Timer view and scroll position; rotation preserves the focused route. Running Edit uses the shared editor below; paused Edit changes the fields saved for Resume. Local ticking includes cumulative stopped-segment time, while paused time stays frozen. Final Stop or authoritative external idle exits; a remote replacement updates the focused context and fences callbacks for the old durable target. Start, pause, resume and stop the timer, and edit the running entry: description, project, task, tags, billable. Tapping the labelled running clock in List or Calendar opens the shared editor for its date and start time, with End and Duration hidden. Saving keeps the same running segment and workspace, queues its unsnapped start offline and rejects an entry that stopped, paused or was replaced while editing. A corrected start changes only the active segment's elapsed contribution to a pause chain. Pause and Resume follow the web app's pause chain (`../tokkl/packages/web/src/components/pauseChain.ts`), which only the phone keeps. Pause stops the running entry with T3's captured Stop and keeps its fields; a segment too short to keep adds nothing, and the timer still pauses. Resume starts a new entry with the kept fields and never reopens a stopped one. The clock shows the stored time of the chain's stopped segments plus the running one, without the gaps. While paused, the timer says Paused, shows that total standing still, and offers Resume and Stop; edits change what Resume starts, and Stop ends the chain without a request. Any other start, a Stop, and a delete or stop of the resumed entry also end it. The chain survives process death, ends when a refresh shows that another client started or stopped a timer, and is cleared at sign-out. The notification, widget, tile and long-timer reminder act on the same chain (N1, N3, N4, N7). |
| T2 | Continue a past entry. |
| T3 | Show stopped entries as the server returns them. For typed creates and time edits, predict Tokkl 1.6.1+ snapping in the account timezone: truncate both endpoints to whole seconds, then snap each independently to the nearest wall-clock grid point, taking the later point on a tie. Refuse a span shorter than half a step or with coincident snapped endpoints before queueing. Non-time edits of stored legacy zero-length entries remain possible. The running clock is not snapped. A Stop freezes its tap instant before database or network work and sends it in the queued PATCH body. A span too short to keep leaves history at once, goes to Trash on the server, and offers Undo; Undo restores a stopped zero-length entry, including when queued offline. |
| T4 | Description autocomplete from recent entries. |
| T5 | Timer templates: the web app's favorites, stored under the `favorites` key in `/me/preferences`, up to 9. More > Timer templates lists them in saved order and edits each one's description, project, task, tags and billable, with Save, Cancel, Delete and Move up and down. Cancel leaves the saved list unchanged. Changes need the server and stay serialized; a failed save keeps the previous list and says why. The timer panel's icon opens a popup of the cached templates in that order. Choosing one dismisses the popup and starts it at once through the ordinary start, including offline, which ends a pause chain. The popup also offers Save as template for the timer's current fields and Manage templates, and an empty state. A history entry's menu still saves that entry as a template. Without the capability the More destination, the panel icon and the save actions are hidden; an empty list on a server that has the capability stays manageable. Cached templates still start offline. The widget and the launcher shortcuts start them the same way, with this wording. |

### 5.3 Manual entry

| ID | Requirement |
|---|---|
| M1 | Add a past entry by start and end or by duration, with Tokkl's parsing: `9`, `930`, `9:30`, `4pm`, entries across midnight, and durations like `1h 30m`, `90`, `1,5h`. |

### 5.4 History and editing

| ID | Requirement |
|---|---|
| H1 | Day-grouped history that loads older days from the server as you scroll. |
| H2 | Edit every field of an entry. |
| H3 | Delete with undo, using Tokkl's restore endpoint. A delete made offline waits in the queue; undoing it before it is sent removes it from the queue. |
| H4 | Group similar entries within a day, as the web app does. |
| H5 | Duplicate an entry. |
| H6 | Split an entry. |
| H7 | Trash: list deleted entries, restore them, purge one or all. Needs the server. |

### 5.5 Calendar

| ID | Requirement |
|---|---|
| K1 | Calendar in the account timezone. Phones in portrait show one day at a time, swipe to the next day, and show the week with its daily totals in a strip on top. Landscape, tablets and unfolded foldables show the week. This differs from the web app, which squeezes seven columns of about 45 px into a phone (`../tokkl/packages/web/src/styles.css`, the 760 px media query). |
| K2 | Create, move and resize entries by touch: long-press then drag, in both the day and the week view. |

### 5.6 Projects, clients, tags and tasks

| ID | Requirement |
|---|---|
| P1 | Pick a project, task and tags wherever an entry is edited, including archived items when an old entry uses them. A new tag can be typed offline: the queue sends tags by name, and Tokkl creates the missing ones (`resolveTagNames` in `../tokkl/packages/api/src/models/tags.ts`). Creating a project, client or task needs the server; offline, the picker offers what is cached and says so. |
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
| R6 | Share CSV on supported Summary and Detailed tabs sends authenticated JSON POST to the configured server's `/api/app/report/summary.csv` or `/api/app/report/detailed.csv`, preserving URL prefixes, shared trust and failure handling. Weekly has no Share action. Tokkl's `role` in `/me` offers both kinds. A 404 or 410 from one POST removes only that kind; transient failures never remove support. |
| R7 | CSV exports snapshot inclusive account dates, every report filter, workspace rounding and rounding minutes, and account duration format. Summary groups and subgroups by projects; Detailed sorts by date descending and includes all matching rows independently of loaded pages. Server CSV bytes are preserved, including Unicode, quoting and empty results. |
| R8 | Export progress prevents duplicate requests. Only a successful, complete, validated CSV may reach the Android chooser as `tokkl-{kind}-{from}-to-{to}.csv`, using a `content://` URI, `text/csv` and temporary read permission. Missing receivers and preparation failures show feedback; cancelling the chooser is harmless. Account replacement and sign-out cancel or invalidate old exports before a chooser can launch. |
| R9 | Export files stay in a private area, outside backups. Each file is capped at 16 MiB, with at most four completed and four staging files, pruned on export. Completed files expire after 24 hours and the provider refuses expired reads. Sign-out clears the area. The provider exposes only completed exports, never partial downloads, credentials, cache databases or other private files. |

### 5.8 Android integration

| ID | Requirement |
|---|---|
| N1 | An ongoing notification while a timer runs, with the description, project, a ticking elapsed time that includes the pause chain's stored time (T1), and Pause and Stop actions. The system ticks the clock (`setUsesChronometer`), so no foreground service runs. The one exception is the service that rings at a Pomodoro phase end, and it runs only while the alarm rings (W7). Since Android 14 the user can swipe an ongoing notification away ([behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all)). A dismissed notification comes back on the next start, pause, resume, stop or edit of the running entry from any surface, not when the app opens. While the timer is paused, a quiet notification that is not ongoing and never a Live Update shows the paused total with Resume and Stop; once swiped away, it stays away until the paused timer changes, also after a restart. |
| N2 | On Android 16 and later, promote that notification to a Live Update, which shows as a chip in the status bar, at the top of the notification drawer and on the lock screen. Elsewhere it stays a regular ongoing notification. A Live Update cannot be colorized ([Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)), so Tokkl's palette reaches only its icon and accent colour. |
| N3 | A home screen widget with the running timer, Start and Stop, and the timer templates. It updates on the same events as the notification and the tile (section 6.3). |
| N4 | A Quick Settings tile that shows whether a timer runs. Tapping it stops the running timer, resumes a paused one (T1), or continues the last entry when neither shows. The tap acts on what the tile shows and never waits for the network. While the timer runs or is paused, the subtitle keeps Running or Paused and says "Hold for controls", and a long press opens compact controls for the timer the tile showed: Pause and Stop while it runs, Resume and Stop while it is paused. Each control acts on that shown target and works offline. TalkBack reads that state and the tap, and says "Hold for controls". When the notification shade opens (`onStartListening`), the tile asks the server for the running entry, at most once a minute. |
| N5 | Ask for the notification permission on Android 13 and later when the first timer starts, and say what is lost without it. |
| N6 | Launcher shortcuts (long-press the app icon) for the timer templates. One tap starts that timer, through the same start that ends a pause chain. |

| N7 | Remind once per running entry id and start after a configurable 1–168 hours, enabled by default at 8 hours. Settings shares `long_timer_reminder_enabled` and `long_timer_reminder_hours` through `/me/preferences` when both keys exist; older Tokkl and Toggl keep them on the phone. One delayed, inexact WorkManager request needs no exact-alarm permission and can arrive a few minutes late. Stop, Pause or a different timer cancels it, and the entry Resume starts gets its own. The separate reminder channel asks "Is your timer still running on purpose?", with Keep running, and offline Pause and Stop through the existing queue. Tapping opens Timer. Confirmation survives process death; correcting the start resets it. Sign-out clears its settings and confirmation. |

### 5.9 Offline

| ID | Requirement |
|---|---|
| O1 | Start, stop, create, edit and delete entries with no route to the server. Each change waits in a queue on the phone, marked as not sent, and goes out in order when the server is reachable. A timer started offline keeps its real start time. |
| O2 | Cache what the app last loaded: projects, clients, tags, tasks, timer templates and about two weeks of history. All of it can be read offline, and entries can be edited. |
| O3 | When the phone and another client both changed an entry, the change that reaches the server last wins. A queued start is the exception, because Tokkl stops the running entry when a new one starts (Tokkl T2). Before sending a queued start, the app fetches the running entry. If that entry started after the queued start, the app sends the queued entry as stopped at that entry's start, on the grid point where that start snaps (T3), and the later timer keeps running. The timer started last is the one the user meant. |
| O4 | Send the queue in the background when the network comes back, with the app closed. |
| O5 | Show how many changes are not sent and when the server last answered. |
| O6 | When the server rejects a queued change: a 409 on a stop is dropped without a message, because the entry is stopped either way. A 404 is dropped, and the app says so ("1 change discarded: the entry was deleted elsewhere"). A 400 moves to a "needs attention" list with the server's message, where the user fixes and resends it or discards it; later changes to the same entry wait behind it, and the rest of the queue goes on. A 403 pauses the whole queue and asks the user to sign in again, discarding nothing. A 5xx or no network retries with backoff. |

### 5.10 Settings

| ID | Requirement |
|---|---|
| G1 | Theme: light, dark, or follow the system. |
| G2 | Opt-in dynamic color. |
| G3 | The four Pomodoro defaults that the session's start form begins from (W3): work minutes (`pomodoro_focus_interval_in_minutes`, default 25, 1 to 180), short rest minutes (`pomodoro_break_interval_in_minutes`, 5, 1 to 60), long rest minutes (`pomodoro_long_break_interval_in_minutes`, 15, 1 to 60) and long rest every N work phases (`pomodoro_long_break_every`, 4, 1 to 12). On Tokkl they live in `/me/preferences` and the web app shares them. Toggl Track has no such keys, so there they are kept on the phone alone, with the same defaults and ranges. |

| G4 | Long-running timer reminders: an on/off switch and hours from 1 to 168, enabled at 8 hours by default (N7). These account settings work offline and clear on sign-out. |

### 5.11 Navigation

The app moves between pages as Tokkl's web app does on a phone (`NAV` and the More sheet in `../tokkl/packages/web/src/App.tsx`), so the same habits work on both.

| ID | Requirement |
|---|---|
| B1 | Every signed-in screen has a bottom navigation bar with Timer, Pomodoro, Projects, Settings and More, in that order. On medium and expanded window widths a navigation rail at the side holds the same items. More opens a sheet with Reports, Timer templates, Clients, Tags and Trash, with capability-dependent pages hidden when unsupported. The Pomodoro item carries a running mark while a session runs (W11). Admin stays out, since the app has no admin features. Sign-in has no bar. The web app's phone bar changes the same way (TT-0324 in `../tokkl`). |
| B2 | Timer is the first page after sign-in. Today and This week totals sit below the timer and above the List and Calendar switch in both views, and scroll away with the timer. They sum effective cached entries, including queued changes and each actual pause segment once, in the account timezone, week start and duration format. Each entry contributes its nonnegative whole elapsed seconds to its start day, including overnight entries. This week runs from the account week start through today, regardless of the week being browsed. The running contribution ticks locally, and account midnight rolls both periods without Reports requests or historical paging. An unresolved account timezone is named without a device-zone fallback. History (H1) and the calendar (K1) fill the space below the switch, as on the web app's Timer page. |
| B3 | Back from any page in the bar or the sheet, Pomodoro and Reports included, goes to Timer, and Back from Timer leaves the app. A screen opened from a page, such as Licences from Settings, goes back to that page. |

### 5.12 Pomodoro

The owner asked for a Pomodoro session on 2026-10-20 and made the decisions below. W1 to W11 state the owner's decisions S1 to S11 of that request, and W12 states S13. Tokkl's web app runs the same session (Tokkl T13), so the rules match on both clients.

| ID | Requirement |
|---|---|
| W1 | Pomodoro is a mode the user picks on its own page, apart from the regular timer. There is no global on/off preference. |
| W2 | Phases run work, then a short rest, or a long rest after every Nth work phase, then work again. A phase changes only when the user confirms it with Next phase. Next phase before the planned end skips the rest of the phase. After the planned length the phase counts overtime until the user confirms. Every ended work phase counts toward N, skipped ones too, and the count restarts after a long rest. There are four transitions: work to short rest, short rest to work, work to long rest, and long rest to work. End session works at any time. |
| W3 | The start form sets this session's work, short rest and long rest minutes and the long rest every N. It begins from the four defaults in G3 and uses their ranges. |
| W4 | The entry fields are description, project, task, tags and billable. The user sets them before the start and can edit them during the session. An edit applies to the pomodoro in progress and to later ones. There are no start or stop fields. |
| W5 | Each pomodoro is one stopped entry. It covers a work phase and the rest after it, from the work start until the user confirms the next work phase or ends the session, and the phone saves it when that pomodoro ends. The phone snaps both ends independently to the nearest grid point of `time_resolution_minutes` in the account timezone, and a tie goes to the later point, following the latest supported [Tokkl 1.8.1 contract](https://github.com/e0ipso/tokkl/blob/bed61cb1e9e3179efdc0e29b82bc8ab9e663b71e/packages/common/snap.ts#L34-L43). The next pomodoro starts at the previous one's exact unsnapped end. A pomodoro whose ends snap to the same point is not saved, and the app tells the user. |
| W6 | At each phase's planned end the transition's sound loops until the user confirms the next phase or ends the session. No control only silences it, and it has no time cap. Tokkl generates the four sounds, one per transition, with FFmpeg in `../tokkl`, under MIT and bitexact, and records them in its `docs/sounds/README.md`. Tokkl for Android copies the files. |
| W7 | The sound plays on the alarm stream (`USAGE_ALARM`), so it rings in silent mode, and in Do Not Disturb when alarms are allowed. The phone vibrates in a repeating pattern with alarm attributes, shows a full-screen alarm over the lock screen and posts on a high-importance alarm channel. One `AlarmManager.setAlarmClock` exact alarm fires at each planned phase end, using `SCHEDULE_EXACT_ALARM` on API 31 and 32 and `USE_EXACT_ALARM` from API 33, as described in the [Android alarm guide](https://developer.android.com/develop/background-work/services/alarms). On API 31 and 32 the app checks `canScheduleExactAlarms`; if access is denied, the Pomodoro page says so. The app sets the stored phase's alarm again after a reboot, an app update, a clock or timezone change, and an exact-alarm permission grant. A missed end rings immediately and leaves the phase in overtime until the user confirms. Next phase and End session cancel the previous alarm, and no alarm is scheduled without a session. A foreground service runs only while the alarm rings (N1). |
| W8 | For reference, the web app plays looped audio that the Start click unlocks, warns when the browser blocks playback, shows a notification that stays until the user acts and alerts again at each transition, and puts the phase in the tab title. |
| W9 | One thing runs at a time. Starting a session stops the running timer at the session's start, with the rule a new timer uses to stop the running one (T3). Starting a regular timer from any surface during a session ends the session at that start and saves the partial pomodoro. A timer started on another client ends the session at that entry's start once the phone sees it (section 6.3). |
| W10 | The session lives on the device only, with no server state or API. The phone keeps it in Room's `account_cache.db` and writes it in the same transaction as the queued entry. Signing out ends the session and cancels its alarm. |
| W11 | The bar and the rail hold Timer, Pomodoro, Projects, Settings and More, and the More sheet holds Reports, Clients, Tags and Trash (B1). The web app's phone bar changes the same way (TT-0324 in `../tokkl`). The Pomodoro item carries a running mark during a session. Pomodoro gets its own hue, and Reports keeps its hue in More. |
| W12 | Out of scope: syncing a session across devices, and a Live Update for the session notification. |

## 6. Architecture

Built: Kotlin with Jetpack Compose and Compose Material 3, Room for the cache and the queue, WorkManager for the background send, OkHttp and kotlinx.serialization with the pinning trust manager, the network security config in 6.2, the Kotlin ports in 6.1, the running timer's notification (N1) without its Live Update, the long-running timer reminder and its Settings controls (N7, G4), the Glance home screen widget with its running clock and offline Start, Stop and timer templates (N3), the timer templates' dynamic launcher shortcuts with offline starts and a short confirmation (N6), and the Quick Settings tile's cached state and offline Stop and Continue actions (N4). Not built: the tile's server refresh when the shade opens (N4), the running entry's check every 15 minutes in 6.3, and the Pomodoro session (section 5.12) with its settings (G3) and the new bar and More sheet (B1).

### 6.1 Stack

- Kotlin and Jetpack Compose with Compose Material 3. Android has been Kotlin-first since Google I/O 2019, and Compose is not available from Java ([Android's Kotlin-first approach](https://developer.android.com/kotlin/first)).
- Jetpack Glance for the widget. It builds home screen widgets from composables on the Compose runtime ([Glance](https://developer.android.com/develop/ui/compose/glance)).
- A `TileService` for the Quick Settings tile.
- Room (SQLite) for the cache and the queue of unsent changes.
- WorkManager to send the queue when the network comes back.
- OkHttp and kotlinx.serialization for the API, with a trust manager that adds the pinned self-signed certificate (S3).
- A Kotlin port of three TypeScript functions, each checked against the cases in its test file: `parseDuration` in `../tokkl/packages/common/duration.ts` (cases in `../tokkl/packages/common/duration.test.ts`), `parseTimeOfDay` in `../tokkl/packages/web/src/lib/timeOfDay.ts` (cases in `../tokkl/packages/web/src/lib/timeOfDay.test.ts`) and `snapSpan` (T3) in `../tokkl/packages/common/snap.ts` (cases in `../tokkl/packages/common/snap.test.ts`). The time-of-day parser lives in the web package, not in `packages/common`.
- A Kotlin port of `formatDuration` in `../tokkl/packages/common/duration.ts`, checked against the format cases in `../tokkl/packages/common/duration.test.ts`. Screens write every duration in the account's `duration_format`, which the app reads from `GET /api/v9/me/preferences`. When the preferences leave it out, they use the improved format, as the web app does.
- A Kotlin port of `formatTime` in `../tokkl/packages/web/src/lib/settings.ts`. Screens write every clock time on the 12-hour clock, as `2:00 PM`, when the preferences' `timeofday_format` holds an `a`, as `h:mm A` does, and on the 24-hour clock, as `14:00`, otherwise. When the preferences leave it out, they use the 24-hour clock, the web app's default `H:mm`.

Every dependency must be free software (section 8.1).

### 6.2 Network security

Since Android 9 an app cannot use cleartext HTTP unless it opts in, and since Android 7 it trusts only system CAs by default ([network security configuration](https://developer.android.com/privacy-and-security/security-config)). The app's network security config therefore permits cleartext (S4) and trusts user-added CAs (S2). The self-signed certificate in S3 is pinned in the app, separate from Android's trust store.

### 6.3 Data flow

- The server is the source of truth. The phone holds the cache and the queue.
- The app refreshes the running entry and the visible data when it opens, after a change, and when the network comes back. It does not poll on a short timer.
- The notification, widget and tile read the running entry from the phone, so they react at once, and the queue sends the change behind them.
- No push reaches the phone, so it can miss a timer started or stopped on another client. Two more events cover that. Opening the notification shade makes the tile fetch `GET /me/time_entries/current`, at most once a minute (N4); the cap keeps Toggl's 30 requests an hour for `/me` endpoints in reach (risk 2). While a timer runs, a WorkManager job checks the running entry every 15 minutes, the shortest periodic interval WorkManager allows, and clears the notification and widget if the timer stopped elsewhere. Otherwise the phone learns of a timer started elsewhere only on one of these events. When it learns of one during a Pomodoro session, the session ends at that entry's start (W9).

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| Speed | A tap on Start or Stop in the app, notification, widget or tile changes the state on the phone without waiting for the server. Tests prove it with a network that never answers. Jetpack Macrobenchmark runs cold start (`StartupTimingMetric`) and tap-to-state (`TraceSectionMetric`) on the CI emulator and fails a build that is slower than the previous one. The goals are 200 ms from tap to new state and 1.5 s cold start on a mid-range phone; no real phone checks them, so they guide design but do not gate releases. |
| Compatibility | Android 12 (API 31) and later. Phones first; tablets and foldables get adaptive layouts. |
| Privacy | No app network traffic to any host but the configured server. Explicit Share CSV grants the chosen receiving app temporary access to that report only, as in R6 to R9. |
| Battery | No wakeups while no timer runs and the queue is empty. No short-interval polling. While a timer runs, one check every 15 minutes (section 6.3), plus at most one delayed inexact wakeup for its long-running reminder (N7), cancelled when it stops or changes. The reminder adds no periodic job or idle wakeup. While a Pomodoro session runs, one exact wakeup per planned phase end (W7). |
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

### 8.2 GitHub and CI

The repo is public at `github.com/e0ipso/tokkl-android` and copies Tokkl's setup:

- One `ci.yml` workflow on pull requests and pushes to `main`, with every action pinned to a commit SHA and its version in a comment. Dependabot updates the actions, the Gradle dependencies and the npm tooling weekly.
- commitlint with Tokkl's rules: Conventional Commits, no AI or co-author trailers, no ticket IDs in the subject. A husky hook runs it locally, and a CI job lints the commits of each pull request and push.
- CI jobs for the Gradle build with lint and the unit tests (`assembleDebug check`), for a release build made twice from clean and compared byte for byte (`scripts/reproducible-release.sh`), for the instrumented tests on emulators at API 36 and 31, and for the macrobenchmarks on an API 36 emulator, which fails a median more than 10% slower than the last green run on `main`. GitHub's Ubuntu runners need a udev rule before the emulator can use KVM ([android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner)).

Releases run in the same workflow after every CI check passes:

- semantic-release on pushes to `main` with Tokkl's release rules and `v${version}` tags. It writes `CHANGELOG.md` and the version into the Gradle build, derives `versionCode` from the version, and attaches the signed release APK to the GitHub release, where F-Droid's `Binaries` can find it.
- The signing key lives in GitHub Actions secrets, never in the repo. After the release commit and tag exist, `android-release` builds and verifies the signed APK from a clean checkout of that tag, and `android-publish` attaches it to the release. README "Automatic releases" describes the one-time starting tag and signing secrets.

### 8.3 Versions and stores

- F-Droid only. No Play Store listing is planned. The signed APK on each GitHub release (section 8.2) serves anyone who sideloads.
- The app has its own version, independent of Tokkl's, with its own `v*` tags and changelog, starting at 0.1.0. semantic-release does not support a first release below 1.0.0 ([FAQ](https://semantic-release.gitbook.io/semantic-release/support/faq)), so the first release is a hand-made `v0.1.0` tag and semantic-release counts on from it. A commit marked as a breaking change then moves the app to 1.0.0.
- Tokkl snapping follows the latest supported contract, the 1.6.1+ independent-endpoint rules, with Tokkl 1.8.1 pinned by digest in device tests. Older snapping contracts are unsupported; there is no server-version selector or compatibility probe. Missing `time_resolution_minutes`, as on Toggl Track, keeps the no-resolution behavior. Other capability checks still hide Tokkl-only features when their endpoints return 404. Tokkl 1.8.1 accepts a captured timestamp in PATCH Stop and moves a short Stop to Trash for Undo.

## 9. Risks and open questions

1. Tokkl's PRD lists native mobile apps as a non-goal, with exceptions only for the three desktop companions. That non-goal and its section 5.8 get a new row when this app's first release ships, not before. On 2026-10-17 tokkl's `main` checkout had uncommitted edits to its PRD, so it was left alone.
2. Toggl Track's API quotas. Since 2025-09-05, workspace and organization requests are capped per user per organization at 30 an hour on Free, 240 on Starter and 600 on Premium, with custom limits on Enterprise. User endpoints such as `/me` are capped at 30 an hour on every plan below Enterprise. The window slides over 60 minutes, and a request over the limit gets HTTP 402 ([Toggl support](https://support.toggl.com/api-webhook-limits), read 2026-10-17). Tokkl's web app polls the running timer every 30 seconds, which alone is 120 requests an hour. If this app polled like that, pointing it at Toggl would hit the Free quota within 15 minutes.
3. F-Droid's NonFreeNet anti-feature covers apps that "promote or depend entirely on a proprietary network service" ([Anti-Features](https://f-droid.org/docs/Anti-Features/), read 2026-10-17). It does not apply while the app talks to Tokkl only. Once Toggl support lands, F-Droid's reviewers decide whether supporting Toggl counts as promoting it.
4. Tokkl-only features (trash, undo of a delete, timer templates, time resolution, "Is not" report filters) will not work against Toggl. The capability checks in section 8.3 hide them there, as they do on older Tokkl servers.
5. The Kotlin port of Tokkl's time parsing and snapping can drift from the TypeScript original. Sharing the test cases between the two repos would catch that. The cases are in `../tokkl/packages/common/duration.test.ts`, `../tokkl/packages/web/src/lib/timeOfDay.test.ts` and `../tokkl/packages/common/snap.test.ts`.
6. Live Updates depend on the device and on the Android 16 release it runs. The notification has to work without them.
7. Google's developer verification. From 2026-09-30 certified Android 7+ phones in Brazil, Indonesia, Singapore and Thailand block apps from unregistered developers when installed from participating stores, and Google plans to extend it to all apps worldwide in 2027. A full registration needs a government ID checked by Google and a $25 fee; the free limited account covers only 20 devices ([developer verification](https://developer.android.com/developer-verification), [FAQ](https://developer.android.com/developer-verification/guides/faq), read 2026-10-17). Whether F-Droid is a participating store was not confirmed. The owner will not register for now and will decide in 2027. Shipping F-Droid builds signed with the project's own key (section 8.1) keeps registration possible later.
8. Some phone makers restrict background work, which can delay sending the queue. To verify on real devices.
9. Build environment. This machine had no JDK or Android SDK on 2026-10-17. `/dev/kvm` exists and the user is in the `kvm` group, so a headless emulator is possible.
10. Changes made on another client reach the phone late (section 6.3). UnifiedPush would let the Tokkl server tell the phone at once without Google, but it needs new server work, a distributor app such as ntfy on every phone, and an ntfy server, which is a host other than the configured one (principle 1). Toggl cannot send it, so the event-based checks stay either way.
11. A password change in Tokkl leaves the API token valid today (Tokkl A13), so a phone that signed in with a password keeps working after the password is reset. A Tokkl change to rotate the token on every password change was handed over on 2026-10-17 and is not made yet.
