---
type: map
title: Report state lifetimes and widget navigation
description: >-
  Route and session stores retain different report choices, page lifecycle
  events refresh data, and widget taps request Today before collection.
tags:
  - reports
  - ui
  - navigation
kk_schema_version: 3
kk_id: map-report-state-lifetimes-and-widget-navigation
kk_derived_from: []
kk_relates_to:
  - practice-refresh-on-events-no-polling
  - map-report-ranges-and-cached-filters
  - map-report-apis-rounding-and-pagination
kk_depends_on: []
kk_confidence: high
---
Report data stays in memory. Offline, or on a 502, 503 or 504 from a proxy that cannot reach Tokkl, the screen says reports need the server and shows no numbers. When the app comes back to the foreground, after a stop that was not a configuration change, the model asks for the range again and shows no numbers until the answer comes. `ReportsViewModel` is the Reports route's own view model: the range, the report, the workspace and the CSV export live as long as the route is on the back stack, and a page opened anew from the bar or the More sheet starts on this week. The route's `SavedStateHandle` keeps the range too (`SavedRange`), so a new process that restores the page asks for the range it showed on its first show, unless a widget's day tap waits. The tab and the filters live in `ReportChoices`, in the signed-in session's store, so the next visit opens on them. They are one `ReportChoice`, which `ReportChoices.chosen` exposes read-only; only its own `selectTab`, `applyFilters`, `openToday` and `restore` change it, each in one step, so no reader sees a tab with the filters of a choice not made yet. Its page state is shared with `stateInPage`, as on the catalog pages: a screen that shows again, after a trip to the background or from under another activity such as the Pomodoro alarm or the tile's dialog, asks again for the range, tab and filters on screen. A widget's day tap goes through `ReportChoices.openToday`, which sets the summary tab and no filters; the page's view model takes it as it is made or while it shows, and asks for today's report before the screen reads it, so that show asks nothing. `ReportsViewModel.page` is the screen's one state: the report, the tab, the account settings and time, the filters and their options, and the export. Its parts, `state` and `chosen`, are internal, for the module's tests. The page reports what happened to the CSV share through `onExportShare(event)`, with a sealed `ExportShareEvent`: `Launch`, `Finished`, `NoTarget` or `Failed`. A configuration change keeps the report and sends nothing. `ReportsViewModelTest` pins that the range, tab and filters survive the screen hidden for longer than five seconds, and that a route that left opens on this week with the tab kept; `ReportTabsViewModelTest` pins the range after process death. Device tests reach the page's view model through `reportsViewModel(activity)`, which reads the route's store.

<!-- kk:related:start -->
# Related

- Related: [practice-refresh-on-events-no-polling](../../api-and-security/practice-refresh-on-events-no-polling.md)
- Related: [map-report-ranges-and-cached-filters](map-report-ranges-and-cached-filters.md)
- Related: [map-report-apis-rounding-and-pagination](map-report-apis-rounding-and-pagination.md)
<!-- kk:related:end -->
