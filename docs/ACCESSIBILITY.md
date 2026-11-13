# Accessibility verification

This matrix records source coverage and the runtime evidence still needed. Source coverage,
compilation and older test receipts do not certify accessibility. Acceptance remains pending
until automated checks, rendered contrast, TalkBack traversal and settings restoration are
verified against the final source revision. Missing evidence alone does not establish a defect.

## Maintained checks

`AccessibilityTestRunner` in `:core:testing` enables Espresso's accessibility checks from the root View. The app's `TokklTestRunner` extends it, and every feature module's instrumented tests run it.
The audit tests import factories from `accessibility/AccessibilityComposeRules.kt`. They
retain the v2 Compose rules and enable the maintained Compose ATF integration. Actions run
checks before changing the UI. Explicit `checkAccessibility()` calls also cover static and
final states. There are no finding suppressions or reduced severity settings.

Compose ATF requires API 34 or later. API 36 is the automated ATF target. API 31 retains
Espresso checks and layout, label and action assertions; its result must not be
called a Compose ATF pass. Timing tests keep their original factories so ATF does not enter
their measured actions. Screenshot references and tolerances stay unchanged.

Selected largest-font tests request `SystemFontScale(2f, largestDisplaySize = true)`.
`SystemDisplaySize` uses the default display's effective pixel size, physical density and
AOSP Settings' `display_density_max_scale` / `display_density_min_scale_interval` fractions.
It follows SettingsLib's 320dp minimum dimension and even-dpi rounding, rather than assuming
that 150% is offered on every device. Unsupported Settings resources fail setup visibly.
Configuration callbacks and readbacks check application of both settings. Cleanup restores
the exact font setting and density override, including their original absence, on setup
failure and test failure. A cleanup failure fails verification and is attached to the primary
failure when there is one.

The source formula follows [AOSP DisplayDensityUtils](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java).
The integration follows the [Compose accessibility testing guide](https://developer.android.com/develop/ui/compose/accessibility/testing),
the [Compose rule API and API 34 requirement](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/junit4/ComposeTestRule),
and [Espresso accessibility checking](https://developer.android.com/training/testing/espresso/accessibility-checking).
Espresso 3.7.0 is the [stable release matching the existing test runner generation](https://developer.android.com/jetpack/androidx/releases/test).
The existing Compose BOM selects `ui-test-junit4-accessibility` 1.12.1.

## Screen and dialog matrix

All rows need runtime evidence. "Prepared" identifies source coverage. At every rendered
state, check label, role, enabled/selected state, reading order, reachable actions, text clipping, touch bounds and rendered contrast. Scroll every
list/sheet/form to its last control; an initial viewport cannot certify the rest of a page.
Check light and dark themes. User project colours and wallpaper opt-in colours need separate
rendered contrast observations; palette unit assertions alone do not certify them.

| Area and bounded states | Prepared automated coverage | Remaining criterion evidence |
| --- | --- | --- |
| Sign-in: API token/password, field/server errors, keyboard and rate-limit notice | `SignInScreenTest`, `LargestFontScaleTest`; ATF, combined sizes, original error/keyboard assertions | Spoken hints/errors and focus after error; rate-limit notice at combined sizes |
| Trust: HTTP confirmation, new/changed certificate, refusal over sign-in and Timer, hostname mismatch | `LargestFontScaleTest`, `CertificatePromptTest`, `CertificateHostMismatchTest`; ATF and existing assertions | All prompt choices, long host/fingerprint and focus return with TalkBack |
| Timer: idle/running, description/suggestions, project/task/tags, billable, start/stop, favourites | `LargestFontScaleTest`, picker audit; ATF and combined sizes | Running/favourite variants and suggestion popup at combined sizes; spoken state transitions |
| Entry: new/past/running, date/start/end/duration, validation, save/cancel | `LargestFontScaleTest`, picker audit | Date/time dialogs and every validation branch at combined sizes; preserve `EntryFormServerTest` deadlines/cleanup |
| History: individual/group, collapsed/expanded, duplicate/split/pin/delete, confirmation and Undo | `HistoryFitTest`, `HistoryGroupUiTest`, `EntryActionsUiTest`; ATF, combined sizes where the existing font rule applies | Real TalkBack order, Undo announcement and focus restoration; no-restore/offline/error paths |
| Calendar: week/day, upright/landscape, running/unsent, narrow and overlapping blocks | `WeekStripFitTest`, `LandscapeCalendarTest`, `DayCalendarTest`; ATF and combined sizes where the system font rule applies | Drag/edit alternatives, narrow block spoken content and focus; verify real window layout |
| Navigation: bar/rail, Timer running badge, selected pages, expanded entry pane | `AdaptiveLayoutTest`; ATF with original window assertions | Window overrides do not certify system display size; actual compact/rail/expanded windows at combined sizes remain pending |
| More: Clients/Tags/Trash, close/back and selection | `LightAdditionalAccessibilityTest`, `DarkAdditionalAccessibilityTest`; actual sheet, ATF, combined sizes, touch actions | TalkBack traversal/close/focus return; real navigation remains covered by existing `BottomBarTest` |
| Reports Summary: totals, billable/non-billable chart, groups, empty/loading/offline/error | `ReportsScreenTest`; ATF; marked largest-font test uses combined sizes | All data/error variants and rendered chart contrast; spoken daily values |
| Reports Detailed/Weekly: rows, sideways grid, range presets/custom dates | `DetailedReportServerTest`, `WeeklyReportServerTest`; ATF, combined marked tests and original scroll assertions | Real traversal of rows/totals/date dialog controls, empty/loading/error states |
| Report filters: projects/clients/tags, negation, billable/all/non-billable, apply/clear/cancel | `ReportFiltersFitTest`, report screen audit; ATF and combined sizes | Every filter selection and disabled/unavailable variant with TalkBack |
| Projects/tasks: active/archived/offline, new/edit, errors, colour/client/billable/rate/estimate, task add/edit/delete and archive/restore questions | `CatalogFitTest` usual/largest subclasses; ATF; largest subclass uses combined sizes | Keyboard, picker and all destructive-question focus transitions; user-colour contrast |
| Clients: active/archived/offline, new/edit/errors, archive/restore | `CatalogFitTest`; ATF and combined largest sizes | TalkBack labels/order/focus on all variants |
| Tags: list/offline, add/rename/delete/errors/questions | `CatalogFitTest`, `LazyCatalogAccessibilityTest`; ATF and combined largest sizes | Spoken rename/delete results and lazy-list focus retention |
| Trash: empty/list/offline, restore/delete-one/empty-all questions/errors | `TrashFitTest`, `LazyCatalogAccessibilityTest`; ATF and combined sizes | TalkBack actions, dialog focus and list retention; no destructive live-data checks |
| Sync: unsent count, last answer, refused/sign-in-needed/discard notices | `SyncStatusFitTest`; ATF and combined sizes; original queue/server tests retained | All notice variants and announcements, link focus |
| Needs attention: empty, CREATE/UPDATE/STOP/RESTORE/DELETE, reason, edit/resend/discard | `LightAdditionalAccessibilityTest`, `DarkAdditionalAccessibilityTest`; actual screen, ATF, combined sizes, touch actions | Real queue/server transitions, absent-reason and signed-out cases, TalkBack focus/announcements |
| Settings: theme/system/dynamic, reminders, account, sign-out, unsent warning/failure | `LargestFontScaleTest`, `SignOutFitTest`; ATF and combined largest sizes | Theme transitions, all reminder validation and sign-out spoken focus paths |
| Licences: Anybody/OFL, Tokkl sounds/MIT, pinned bar/back and full-text scroll | `LicencesScreenTest`, extended `LargestFontScaleTest`; ATF and combined largest sizes | Full long-text traversal and spoken headings; preserve full notice comparisons |
| Hosted native widget: running/idle/favourite, normal/minimum size | `WidgetLargeTextTest`: font-only and combined font/display cases using `SystemDisplaySize`, existing fit/ATF and clock-tick helpers | Both cases are unrun; the actual launcher/SystemUI widget and spoken traversal remain separate native criteria |
| Permission explanation/refusal/settings action and native permission dialog | `NotificationPermissionPromptTest`; ATF and combined maximum font/display on app-owned UI; explicit checks only in the three cases that set Compose content | Registry stand-in is not the native dialog; native prompt and rendered maximum-size checks remain pending |

## Non-colour occurrence matrix

These are inspected source cues. Each row still needs rendered/spoken verification at the
final commit. Absence of a billable/unsent presentation on a native surface is not evidence
that it displays that state. Verify only states actually presented there.

| State and every identified presentation | Source cue and audit location |
| --- | --- |
| Running: Timer bar | `timer/TimerBarParts.kt` `RunningLabel`: running text, Stop action and elapsed clock |
| Running: bar and rail | `navhost/AppNavigation.kt`: badge shape and Timer `stateDescription` |
| Running: calendar block/day | `calendar/CalendarBlock.kt`: dashed outline and `spokenBlock` running words; check narrow blocks too |
| Running: notification/reminder | `notification/RunningNotifier.kt`: stopwatch notification, elapsed clock, Stop; `reminder/ReminderNotifier.kt`: running reminder text and Stop/Keep running actions |
| Running: widget | `widget/TokklWidget.kt`: idle/running words and play/stop controls; verify signed-out/no-favourite states too |
| Running: tile | `tile/RunningTileLook.kt`: state-specific label/subtitle/icon/content description for running, continue and unavailable |
| Billable: timer and entry field editors | `entryfields/EntryFieldButtons.kt` `BillableToggle`: check/dollar icon, Billable label, checkbox role and checked state |
| Billable: history individual/group/member | `history/HistoryEntryRow.kt`: Billable words on the presented entry; inspect expanded and collapsed groups |
| Billable: project list and form | `projects/ProjectsScreen.kt`: Billable text; `projects/ProjectForm.kt`: labelled checked option row |
| Billable: report totals/chart/filter | `reports/SummaryContent.kt`: Billable time text and striped non-billable chart portion; `reports/FilterControl.kt`: named radio choices and textual active-filter summary |
| Unsent: timer bar | `timer/TimerBarParts.kt`: icon plus NOT SENT words |
| Unsent: history individual/group/member | `history/HistoryEntryRow.kt`: unsent icon and words, group aggregates pending members |
| Unsent: calendar normal/narrow blocks | `calendar/CalendarBlock.kt`: unsent icon, words where space permits, and Not sent in `spokenBlock` |
| Unsent: sync/Needs attention and sign-out | `sync/SyncStatusBand.kt`: icon/count/words; `sync/NeedsAttentionScreen.kt`: reason and action text; `settings/SettingsScreen.kt`: explicit unsent warning/count |

## Runtime verification

Follow the [build and device runtime guidance](../.ai/kenkeep/ENTRY.md) when scheduling
checks. Record the source commit, resolved dependencies, API/image/profile, font and density
settings, and test selection. Retain logs, JUnit results, ATF findings and cleanup readbacks.
Do not combine overlapping test counts from different runs.

1. Compile and run host checks before device tests. Fix source/API errors first. Use focused
   runs for failures, then the surrounding tests and broader acceptance checks. The matrix
   includes inherited tests, Compose and Espresso View ATF canaries, restoration tests and
   screenshot comparisons; a small selection of classes cannot certify all of it.
2. Verify the display helper's computed maximum against Settings on each image. Observe the
   original settings, combined maximum and restored settings, including after a deliberately
   failed body on a disposable device. Window overrides and widget dp resizing cannot replace
   this check. Keep normal screenshot settings, references and tolerances unchanged.
3. The hosted widget tests cover 360dp normal and 240dp/152dp minimum host sizes at font scale
   2.0. The combined-size case changes system density before launching the host, caps normal
   width to the display, checks settled updates and clock ticks, then restores density before
   font scale. This RemoteViews activity host does not certify launcher grid sizing, SystemUI
   focus or spoken TalkBack behavior.
4. Traverse every screen/dialog row in both directions with TalkBack, in both themes at
   combined maximum sizes. Activate each control on disposable data. Record spoken labels,
   roles, states, focus order/return and action outcomes. Stop at failures and retain a
   reproduction. A semantics tree, notification extras or UiAutomation inspection alone does
   not prove spoken behavior.
5. Complete the native cases below. Establish the TalkBack package/version, audible speech
   observation and gesture control before claiming a traversal pass. Record speech and focus
   with timestamped actions; attach audio/video when supported.
6. Remove test entries, accounts/favourites, widget hosts and pinned shortcuts/tile additions.
   Restore font/density, accessibility services, permission/channel settings and notification
   state, with readbacks. Permission revocation can kill instrumentation, so test native
   permission transitions in a separate disposable-device session.

| Native cases at combined maximum sizes | Required observation/action |
| --- | --- |
| Running notification, collapsed and expanded, empty and long description/project/task | Focus title/context/clock/Stop; observe elapsed announcement without continuous focus theft; open app; Stop offline once, confirm queued state |
| Reminder notification, Stop and Keep running | Traverse title/body/both actions, activate each on a separate disposable timer and observe dismissal/state |
| Launcher widget, idle/running/favourites, signed out/no favourite, normal/minimum host size | Traverse every native control and clock; Start/Stop/favourite offline; check actual launcher focus and clipping. Hosted ATF/`performClick` alone is insufficient |
| Quick Settings tile, running/continue/unavailable | Traverse actual SystemUI tile, hear state/context, activate running/continue offline, observe refresh after shade reopen; unavailable must not start an entry |
| Launcher favourites shortcut, long name/project and removed favourite | Traverse actual launcher menu, hear target/action, activate one disposable favourite, verify stale target handling and cleanup |
| Native notification permission, first ask/allow/refuse/settings | Traverse actual system dialog and app explanation, check focus return. API 31 has no Android 13 permission dialog |
