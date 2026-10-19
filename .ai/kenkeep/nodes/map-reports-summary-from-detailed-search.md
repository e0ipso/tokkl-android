---
type: map
title: 'Reports: the summary summed on the phone from the detailed search'
description: >-
  reports/ pages the Reports v3 detailed search and sums it on the phone, since
  Tokkl's totals and summary carry no rates or billable split.
tags:
  - reports
  - api
  - toggl
  - ui
kk_schema_version: 3
kk_id: map-reports-summary-from-detailed-search
kk_derived_from: []
kk_relates_to:
  - map-tokkl-toggl-api
  - practice-match-web-app-behavior-account-timezone
  - practice-refresh-on-events-no-polling
  - practice-port-tokkl-typescript-with-javascript-semantics
kk_depends_on: []
kk_confidence: high
---
`reports/` holds the Reports screen (`Destination.REPORTS`) with a Summary tab. `ReportRange` offers the web app's presets in its order except All time, which needs Tokkl's own `/api/app/entries/bounds` and can span ten years. Previous and next move the range, and a custom range covers up to `MAX_RANGE_DAYS`, 366, the most the Reports API v3 accepts. Days and weeks follow the account's timezone and week start.

`ReportsServer` takes the account's default workspace from `GET /api/v9/me` and its rounding from `GET /api/v9/workspaces/{id}`. It pages `POST /reports/api/v3/workspace/{id}/search/time_entries`, following `X-Next-Row-Number`, and the phone sums the entries. The summary endpoints will not do: Tokkl's v3 totals send `rates: []`, and its v3 summary groups carry seconds only, with no billable time or amount. Each search asks for the workspace's `rounding` and `rounding_minutes`, because the Reports API rounds only when asked while the web app always rounds. `ReportsScreenTest` checks the summary against `POST /api/app/report`, the web app's own call.

Report data stays in memory. Offline, or on a 502, 503 or 504 from a proxy that cannot reach Tokkl, the screen says reports need the server and shows no numbers. When the app comes back to the foreground, after an `ON_STOP` that was not a configuration change, the model asks for the range again and shows no numbers until the answer comes. Adding the lifecycle observer replays `ON_START` at once, and the model ignores that one. A configuration change keeps the report and sends nothing.

Every page of the detailed search is one request. Against Toggl Track's hourly quotas that will cost more than one summary call.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](map-tokkl-toggl-api.md)
- Related: [practice-match-web-app-behavior-account-timezone](practice-match-web-app-behavior-account-timezone.md)
- Related: [practice-refresh-on-events-no-polling](practice-refresh-on-events-no-polling.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics](practice-port-tokkl-typescript-with-javascript-semantics.md)
<!-- kk:related:end -->
