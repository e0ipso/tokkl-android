---
type: map
title: Report APIs rounding and pagination
description: >-
  Summary sums detailed search, Detailed follows server pagination, and Weekly
  reads rounded v3 rows.
tags:
  - reports
  - api
  - toggl
kk_schema_version: 3
kk_id: map-report-apis-rounding-and-pagination
kk_derived_from: []
kk_relates_to:
  - map-tokkl-toggl-api
  - practice-preserve-javascript-parsing-and-numeric-boundaries
  - practice-keep-upstream-golden-cases-with-source-pinned-ports
  - practice-match-javascript-decimal-and-percentage-formatting
  - map-report-ranges-and-cached-filters
kk_depends_on: []
kk_confidence: high
---
`ServerReportsRepository`, the Reports page's repository on the signed-in account's server, takes the account's default workspace from `GET /api/v9/me`, which `account.defaultWorkspace()` reads as `MeWorkspace`, and its rounding from `GET /api/v9/workspaces/{id}`. It maps core:api's `ReportSearchRow`, `ReportSearchEntry` and `WeeklyReportRow` to the page's types. It pages `POST /reports/api/v3/workspace/{id}/search/time_entries`, following `X-Next-Row-Number`, and the phone sums the entries. The summary endpoints will not do: Tokkl's v3 totals send `rates: []`, and its v3 summary groups carry seconds only, with no billable time or amount. Each search asks for the workspace's `rounding` and `rounding_minutes`, because the Reports API rounds only when asked while the web app always rounds. `ReportsScreenTest` checks the summary against `POST /api/app/report`, the web app's own call.

The Detailed tab lists entries newest first, 50 a page, as the web app's detailed report does. `ServerReportsRepository.detailedPage` asks the same search in `DESC` order and starts each next page at the row the server named in `X-Next-Row-Number`. The phone never works out a page itself. A long list composes lazily, so only what shows is laid out.

The Weekly tab's range is always a week, so it offers only the week presets. `ServerReportsRepository.weekly` sends `POST /reports/api/v3/workspace/{id}/weekly/time_entries`, which answers a row per project with its seconds on each of the seven days, and asks for the workspace's rounding as the search does. On a screen too narrow for the grid, the days scroll sideways under a project column that stays put. The Summary chart draws its bar track in `chartBand`, Tokkl's `--chart-band`.

Every page of the detailed search is one request. Against Toggl Track's hourly quotas that will cost more than one summary call.

<!-- kk:related:start -->
# Related

- Related: [map-tokkl-toggl-api](../../api-and-security/map-tokkl-toggl-api.md)
- Related: [practice-preserve-javascript-parsing-and-numeric-boundaries](../../data-and-offline/typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md)
- Related: [practice-keep-upstream-golden-cases-with-source-pinned-ports](../../data-and-offline/typescript-time-ports/practice-keep-upstream-golden-cases-with-source-pinned-ports.md)
- Related: [practice-match-javascript-decimal-and-percentage-formatting](../../data-and-offline/typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md)
- Related: [map-report-ranges-and-cached-filters](map-report-ranges-and-cached-filters.md)
<!-- kk:related:end -->
