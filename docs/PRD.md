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

Open: which backend comes first, whether one install talks to both at once, and how Toggl's API limits affect sync.

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

Open.

## 9. Risks and open questions

1. Tokkl's PRD lists native mobile apps as a non-goal, with exceptions only for the three desktop companions. If this app ships, that non-goal and its section 5.8 need a new row.
2. Toggl Track's API rate limits for third-party clients. Unverified; to research before the sync design.
3. Build environment. This machine had no JDK or Android SDK on 2026-10-17. `/dev/kvm` exists and the user is in the `kvm` group, so a headless emulator is possible.

## Interview log

Answers recorded as the interview goes, before they move into the sections above.
