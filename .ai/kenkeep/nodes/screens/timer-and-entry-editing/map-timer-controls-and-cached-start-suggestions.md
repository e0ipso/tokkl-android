---
type: map
title: Timer controls and cached start suggestions
description: >-
  Optimistic timer actions settle input and use cached history and favorites to
  start entries.
tags:
  - ui
  - timer
  - offline
  - queue
kk_schema_version: 3
kk_id: map-timer-controls-and-cached-start-suggestions
kk_derived_from: []
kk_relates_to:
  - map-timer-page-layout-and-scroll-away-header
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-never-wait-for-server-on-start-stop
kk_depends_on: []
kk_confidence: high
---
`timer/TimerModel` runs the timer on the Timer page (`docs/PRD.md` T1), and `TimerBar` draws it as the web app's `TimerBar.tsx` does. Every tap goes to the change queue through the `EntryChanges` interface, so nothing waits for the network, and a JVM test can stand in for the database. The timer shows the cache's running entry, and from a tap until the cache shows it, what the tap did. Start and Stop taps in between are ignored, so a double tap starts one entry. A cache that never shows the tap is given up on after `SETTLE_MS`, 5 s. Start launches undispatched, so the queue reads the tap time inside the tap's handler. Until the first account settings bring the default workspace, the fields and Start are disabled.

Picks of project, task, tags and billable go to the queue at once. A description goes when typing ends, on `descriptionDone` or Stop, so the queue does not get a change for every letter. Until the cache has the edit, the timer shows it over the cache's entry, so a refresh cannot take letters back. After Start or Stop comes the button that adds a past entry by hand (M1), whether an entry runs or not.

Continue on a history entry (T2) goes through `TimerModel.continueEntry`, and a favorite through `startFrom`; both share the private `startWith` with Start, so a running entry stops at the new start and a double tap starts one entry. While the description has focus, `entry/DescriptionSuggestions.kt` lists cached entries as the web app's `suggestionsFrom` in `TimerBar.tsx` ranks them (at most 8), and a pick fills every field through `TimerModel.fill`, as `pickSuggestion` does. Two focus gotchas hold here. Clearing a text field's focus right after setting its text makes it hand back the text it held before, so a suggestion pick keeps focus. A tap on a favorite or Continue leaves focus in the description field, which would later hand the old text to the new entry, so the caller clears focus after the model call with `settleDescription` in `timer/TimerBar.kt`, never before.

<!-- kk:related:start -->
# Related

- Related: [map-timer-page-layout-and-scroll-away-header](../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md)
- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-never-wait-for-server-on-start-stop](../../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
<!-- kk:related:end -->
