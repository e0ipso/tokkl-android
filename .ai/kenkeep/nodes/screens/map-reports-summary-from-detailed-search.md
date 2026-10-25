---
type: map
title: 'Reports: Summary summed on the phone, Detailed paged, Weekly from v3'
description: >-
  reports/ sums the v3 detailed search on the phone for Summary, pages it for
  Detailed, reads v3 weekly for Weekly, and filters all three.
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
`reports/` holds the Reports screen (`Destination.REPORTS`): a tab per report, Summary, Detailed and Weekly, under one range they share. `ReportRange` offers the web app's presets in its order except All time, which needs Tokkl's own `GET /api/app/bounds` and can span ten years. Previous and next move the range, and a custom range covers up to `MAX_RANGE_DAYS`, 366, the most the Reports API v3 accepts. Days and weeks follow the account's timezone and week start.

`ReportsServer` takes the account's default workspace from `GET /api/v9/me` and its rounding from `GET /api/v9/workspaces/{id}`. It pages `POST /reports/api/v3/workspace/{id}/search/time_entries`, following `X-Next-Row-Number`, and the phone sums the entries. The summary endpoints will not do: Tokkl's v3 totals send `rates: []`, and its v3 summary groups carry seconds only, with no billable time or amount. Each search asks for the workspace's `rounding` and `rounding_minutes`, because the Reports API rounds only when asked while the web app always rounds. `ReportsScreenTest` checks the summary against `POST /api/app/report`, the web app's own call.

The Detailed tab lists entries newest first, 50 a page, as the web app's detailed report does. `ReportsServer.detailedPage` asks the same search in `DESC` order and starts each next page at the row the server named in `X-Next-Row-Number`. The phone never works out a page itself. A long list composes lazily, so only what shows is laid out.

The Weekly tab's range is always a week, so it offers only the week presets. `ReportsServer.weekly` sends `POST /reports/api/v3/workspace/{id}/weekly/time_entries`, which answers a row per project with its seconds on each of the seven days, and asks for the workspace's rounding as the search does. On a screen too narrow for the grid, the days scroll sideways under a project column that stays put. The Summary chart draws its bar track in `chartBand`, Tokkl's `--chart-band`.

`ReportFilters` holds the web app's filters (R4): projects, clients and tags, each a list of ids where null stands for none, and billable as all, billable only or non-billable only. Every tab's request carries them through `writeTo`, which adds the web app's `filterBody` fields. The Filters button under the range names each active filter in words, such as "Project: Website, Internal" or "Billable only", so the filters show as text and not only as a changed control. An id the cache no longer has reads as the web app's "2 selected". It opens a full-screen sheet whose options come from the phone's cache, archived projects and clients included, so opening it sends nothing. Each list starts with its "without" choice, and nothing applies until Apply, so the report is asked for once. A search field narrows each list by name, ignoring case, but keeps the ticked rows and the "without" row (`matching` in `ReportFilters.kt`), so a search never hides what is picked. On a server with `Capabilities.negatedFilters` each of the three lists offers "Is" and "Is not": `projectsNot`, `clientsNot` and `tagsNot` send the list as `exclude_project_ids`, `exclude_client_ids` or `exclude_tag_ids` instead, as the web app does. Toggl Track's Reports API v3 has no negation, so there the choice is hidden. The filters stay when the screen closes and opens again, as the tab does.

Report data stays in memory. Offline, or on a 502, 503 or 504 from a proxy that cannot reach Tokkl, the screen says reports need the server and shows no numbers. When the app comes back to the foreground, after an `ON_STOP` that was not a configuration change, the model asks for the range again and shows no numbers until the answer comes. Adding the lifecycle observer replays `ON_START` at once, and the model ignores that one. A configuration change keeps the report and sends nothing.

Every page of the detailed search is one request. Against Toggl Track's hourly quotas that will cost more than one summary call.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](../api-and-security/map-tokkl-toggl-api.md)
- Related: [practice-match-web-app-behavior-account-timezone](../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [practice-refresh-on-events-no-polling](../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics → practice-honor-account-clock-formatting-in-time-ports](../data-and-offline/typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics → practice-keep-upstream-golden-cases-with-source-pinned-ports](../data-and-offline/typescript-time-ports/practice-keep-upstream-golden-cases-with-source-pinned-ports.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics → practice-match-javascript-decimal-and-percentage-formatting](../data-and-offline/typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics → practice-preserve-javascript-parsing-and-numeric-boundaries](../data-and-offline/typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md)
- Related: [practice-port-tokkl-typescript-with-javascript-semantics → practice-preserve-tokkl-timezone-conversion-and-database-limits](../data-and-offline/typescript-time-ports/practice-preserve-tokkl-timezone-conversion-and-database-limits.md)
<!-- kk:related:end -->
