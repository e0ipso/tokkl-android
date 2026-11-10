---
type: map
title: Report ranges and cached filters
description: >-
  Shared report ranges follow account time, and cached project, client and tag
  filters apply together with capability-gated negation.
tags:
  - reports
  - ui
  - toggl
kk_schema_version: 3
kk_id: map-report-ranges-and-cached-filters
kk_derived_from: []
kk_relates_to:
  - practice-match-web-app-behavior-account-timezone
  - practice-honor-account-clock-formatting-in-time-ports
  - practice-preserve-tokkl-timezone-conversion-and-database-limits
  - map-report-apis-rounding-and-pagination
  - map-report-state-lifetimes-and-widget-navigation
kk_depends_on: []
kk_confidence: high
---
`:feature:reports` holds the Reports screen (`ReportsRoute`; `ReportsPage` takes the view model and `ReportsScreen` the state): a tab per report, Summary, Detailed and Weekly, under one range they share. `ReportRange` offers the web app's presets in its order except All time, which needs Tokkl's own `GET /api/app/bounds` and can span ten years. Previous and next move the range, and a custom range covers up to `MAX_RANGE_DAYS`, 366, the most the Reports API v3 accepts. Days and weeks follow the account's timezone and week start.

`ReportFilters` holds the web app's filters (R4): projects, clients and tags, each a list of ids where null stands for none, and billable as all, billable only or non-billable only. Every tab's request carries them as `apiFilter()`, a core:api `ReportFilter` that `ReportsApi` writes as the web app's `filterBody` fields. The Filters button under the range names each active filter in words, such as "Project: Website, Internal" or "Billable only", so the filters show as text and not only as a changed control. An id the cache no longer has reads as the web app's "2 selected". It opens a full-screen sheet whose options come from the phone's cache, archived projects and clients included, so opening it sends nothing. Each list starts with its "without" choice, and nothing applies until Apply, so the report is asked for once. A search field narrows each list by name, ignoring case, but keeps the ticked rows and the "without" row (`matching` in `ReportFilters.kt`), so a search never hides what is picked. On a server with `Capabilities.negatedFilters` each of the three lists offers "Is" and "Is not": `projectsNot`, `clientsNot` and `tagsNot` send the list as `exclude_project_ids`, `exclude_client_ids` or `exclude_tag_ids` instead, as the web app does. Toggl Track's Reports API v3 has no negation, so there the choice is hidden. The filters stay when the screen closes and opens again, as the tab does, in `ReportChoices`. The page also keeps both in its `SavedStateHandle`, so a new process that restores the page gives them back to `ReportChoices.restore`, unless a widget's day tap waits or something was chosen since.

<!-- kk:related:start -->
# Related

- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [practice-honor-account-clock-formatting-in-time-ports](../../data-and-offline/typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md)
- Related: [practice-preserve-tokkl-timezone-conversion-and-database-limits](../../data-and-offline/typescript-time-ports/practice-preserve-tokkl-timezone-conversion-and-database-limits.md)
- Related: [map-report-apis-rounding-and-pagination](map-report-apis-rounding-and-pagination.md)
- Related: [map-report-state-lifetimes-and-widget-navigation](map-report-state-lifetimes-and-widget-navigation.md)
<!-- kk:related:end -->
