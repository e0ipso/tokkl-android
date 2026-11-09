---
type: map
title: Entry form and account-time validation
description: >-
  Stopped and manual entry editing preserve untouched seconds and validate
  ranges in account time.
tags:
  - ui
  - timer
  - offline
  - queue
kk_schema_version: 3
kk_id: map-entry-form-and-account-time-validation
kk_derived_from: []
kk_relates_to:
  - map-offline-entry-mutations-and-local-id-mapping
  - practice-match-web-app-behavior-account-timezone
  - map-shared-timer-and-entry-fields
kk_depends_on: []
kk_confidence: high
---
`entry/EntryFormViewModel` and `EntryFormScreen` in `:feature:timer` are the entry form, `Route.Entry`, which opens above the Timer page. A tap on a history entry opens it to edit every field of a stopped entry (H2), and the timer's button opens it to add a past entry (M1), as the web app's `EntryEditor.tsx` and the manual mode of its `TimerBar.tsx` do. `resolveRange` in `:core:entryfields`'s `entryfields/TimeRange.kt` reads the start and end with the parser ports, in the account's timezone. Leaving the start or the end fills in the duration, and leaving the duration fills in the end. On the edit screen a time field left as it opened keeps the entry's own instant, seconds included. Save puts a `TimeMistake` under a field that cannot be read or a span the server would refuse, and queues nothing. Otherwise the change goes to the queue with every field. An entry the cache does not hold, such as one from the history's older days or a calendar week loaded from the server, opens from the screen's copy. Save of an edit goes through the session's entries as `PastEntryEdits.saveEdit` (`SessionEntries`), which updates the cache's row while the cache holds it and otherwise queues the whole entry with `EntryChanges.updateOlder`, a full PUT, for an entry from before the cache's window. A save whose local write the storage refuses (`LocalWrite.Failed`) gives the form back as typed; a delete it refuses says Delete failed. `EntryFormViewModel` keeps the form across configuration changes, and across process death in its `SavedStateHandle` (`entry/SavedEntryForm.kt`): the copy it opened on of an entry the cache lacked, and each field typed that differs from what it opened with. `EntryFormOpenings.take` gives every route its opening, so a route restored in a new process opens the form again. It reads the entry from the cache again, so an entry gone since shows as missing, and puts the typed fields over it.

<!-- kk:related:start -->
# Related

- Related: [map-offline-entry-mutations-and-local-id-mapping](../../data-and-offline/change-queue/map-offline-entry-mutations-and-local-id-mapping.md)
- Related: [practice-match-web-app-behavior-account-timezone](../../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [map-shared-timer-and-entry-fields](map-shared-timer-and-entry-fields.md)
<!-- kk:related:end -->
