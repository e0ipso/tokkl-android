---
type: map
title: 'Timer, entry form and entry menu: TimerModel, EntryFormModel, EntryActions'
description: >-
  The timer starts, stops and edits through the queue, the ENTRY form edits or
  adds a past entry, and the menu duplicates or deletes.
tags:
  - ui
  - timer
  - offline
  - queue
  - accessibility
kk_schema_version: 3
kk_id: map-timer-entry-form-and-entry-menu
kk_derived_from: []
kk_relates_to:
  - map-timer-page-history-calendar-and-timer-view
  - map-change-queue-sends-entry-changes-in-order
  - practice-never-wait-for-server-on-start-stop
  - practice-match-web-app-behavior-account-timezone
  - practice-check-capabilities-not-server-version
kk_depends_on: []
kk_confidence: high
---
`timer/TimerModel` runs the timer on the Timer page (`docs/PRD.md` T1), and `TimerBar` draws it as the web app's `TimerBar.tsx` does. Every tap goes to the change queue through the `EntryChanges` interface, so nothing waits for the network, and a JVM test can stand in for the database. The timer shows the cache's running entry, and from a tap until the cache shows it, what the tap did. Start and Stop taps in between are ignored, so a double tap starts one entry. A cache that never shows the tap is given up on after `SETTLE_MS`, 5 s. Start launches undispatched, so the queue reads the tap time inside the tap's handler. Until the first account settings bring the default workspace, the fields and Start are disabled.

Picks of project, task, tags and billable go to the queue at once. A description goes when typing ends, on `descriptionDone` or Stop, so the queue does not get a change for every letter. Until the cache has the edit, the timer shows it over the cache's entry, so a refresh cannot take letters back. After Start or Stop comes the button that adds a past entry by hand (M1), whether an entry runs or not.

Continue on a history entry (T2) goes through `TimerModel.continueEntry`, and a favorite through `startFrom`; both share the private `startWith` with Start, so a running entry stops at the new start and a double tap starts one entry. While the description has focus, `entry/DescriptionSuggestions.kt` lists cached entries as the web app's `suggestionsFrom` in `TimerBar.tsx` ranks them (at most 8), and a pick fills every field through `TimerModel.fill`, as `pickSuggestion` does. Two focus gotchas hold here. Clearing a text field's focus right after setting its text makes it hand back the text it held before, so a suggestion pick keeps focus. A tap on a favorite or Continue leaves focus in the description field, which would later hand the old text to the new entry, so the caller clears focus after the model call with `settleDescription` in `timer/TimerBar.kt`, never before.

`favorites/` holds Tokkl's favorites (T5), up to Tokkl's `MAX_FAVORITES`, the same list the web app shows. They live in the `favorites` key of `/me/preferences` (the `Favorite` type in `../tokkl/packages/types/index.ts`) and are cached in Room, so they show and start offline. Adding, removing and moving one needs the server: `FavoritesServer.save` posts the `favorites` key alone to `POST /api/v9/me/preferences` and then replaces the cache, and no other change starts until it answers. `FavoritesModel.add` takes `onDone`, which gets null once the server has stored the list or the reason nothing was pinned. Without `Capabilities.favorites`, as on Toggl Track, the page shows no favorites control.

`entry/EntryFieldsEditor` is the field row the timer and the entry form share: the description, a project and task button and a tags button that both open `picker/EntityPicker` in a full-screen dialog, and billable. It wraps as the web app's bar does, with the description at least `DescriptionMinWidth`, 280dp, wide. While the description has focus it keeps what is typed whatever its input says, and it hands the text on when focus leaves or the keyboard's Done is pressed. A picked field is drawn in primary and an empty one in the outline colour, and TalkBack reads an empty one as "Project: none" or "Tags: none".

`entry/EntryFormModel` and `EntryFormScreen` are the entry form, `Destination.ENTRY`, which opens above the Timer page. A tap on a history entry opens it to edit every field of a stopped entry (H2), and the timer's button opens it to add a past entry (M1), as the web app's `EntryEditor.tsx` and the manual mode of its `TimerBar.tsx` do. `resolveRange` in `EntryTimes.kt` reads the start and end with the parser ports, in the account's timezone. Leaving the start or the end fills in the duration, and leaving the duration fills in the end. On the edit screen a time field left as it opened keeps the entry's own instant, seconds included. Save puts a `TimeMistake` under a field that cannot be read or a span the server would refuse, and queues nothing. Otherwise the change goes to the queue with every field. An entry the cache does not hold, such as one from the history's older days or a calendar week loaded from the server, opens from the screen's copy and saves through `EntryChanges.updateOlder`, a full PUT. `EntryFormViewModel` keeps the form across configuration changes but not across process death, so after a restart the ENTRY screen closes itself at once.

`history/EntryActions` is the history's entry menu (H3, H5). Delete goes through the queue, so the entry leaves the list at once, also offline, and a snackbar offers Undo, which calls `ChangeSender.undoDelete`. The snackbar with Undo stays for Material's long duration, which grows with Android's accessibility timeout. When `Capabilities.restore` is false, as on Toggl Track, Delete asks first and the message offers no Undo. Duplicate queues a create with the source's fields. Split (H6) cuts a stopped entry at a typed clock time, ported in `entry/SplitEntry.kt` from the web app's `splitHalves` and `snappedSplit`. A time that is not after the start falls on the next calendar day at the same clock time, for an overnight entry, and the cut moves to the grid of the time resolution. It queues two changes, as the web app posts them: a create of the new second half first, then the original ending at the cut, so a split works offline. The create records the server's entries near its start in `PendingChange.existingIds`, so the lookup for a create whose answer was lost never takes the source for the copy.

<!-- kk:related:start -->
# Related

- Related: [map-timer-page-history-calendar-and-timer-view](map-timer-page-history-calendar-and-timer-view.md)
- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
- Related: [practice-never-wait-for-server-on-start-stop](../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
- Related: [practice-match-web-app-behavior-account-timezone](../data-and-offline/practice-match-web-app-behavior-account-timezone.md)
- Related: [practice-check-capabilities-not-server-version](../api-and-security/practice-check-capabilities-not-server-version.md)
<!-- kk:related:end -->
