---
id: 11
group: "migration-and-acceptance"
dependencies: [10]
status: "completed"
created: 2026-10-28
skills:
  - android-testing
  - api-testing
complexity_score: 6
complexity_notes: "One real-server workflow suite covers both directions of the same revisioned timer contract. The cases and two-second bounds are explicit; Android lifecycle/Doze and the final full-suite gate are separated into task 12."
execution_profile: "complex-architecture"
---
# Prove cross-device timer workflows

## Objective

Verify that the phone and another credential on the real released server share sessions, phases and pause chains without an offline session queue.

## Skills Required

Android instrumented workflow testing and authenticated real-server API testing.

## Acceptance Criteria

- [x] A session started through another credential of the same user reaches the backgrounded phone within 2 seconds. Its phase, countdown and `Pomodoro N of M` match the server, and a phone-started session appears through the API with its form fields and overrides.
- [x] At the server planned end the phone rings. API Next phase and End session each stop ringing and close its alarm within 2 seconds. A stale phone Next receives 409, advances nothing and shows the server state plus `Another device changed the timer first.`
- [x] Pause/Resume work in both directions and Discard clears a paused chain. Compare `pause.seconds` and the clone contribution using a controlled sampling instant/tolerance, and verify Stop of a running clone stays an ordinary v9 stop.
- [x] All six session/pause actions fail offline with the exact offline message and unchanged held state/no queued session work. Exercise the app, alarm/session notification, running notification, widget, tile and reminder where each action is exposed. Ordinary queued Start/Stop still change the phone immediately.
- [x] On an actually unsupported older Tokkl server, Pomodoro and every Pause/Resume control are hidden while Start/Stop still work. Include a non-Tokkl capability decision through existing capability coverage, without requiring a personal Toggl account or adding a fake server.
- [x] Cover remote phase replacement, repeated discarded-entry notices and a stale native revision through focused workflow cases or the meaningful retained component checks. Do not recreate tests of server phase rules in Android.
- [x] Add `TimerCrossDeviceServerTest` and `TimerActionsOfflineServerTest`, and update affected existing server workflow tests. For each of tokkl-api36 and tokkl-api31 run `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timerstate.TimerCrossDeviceServerTest,com.github.e0ipso.tokkl.timerstate.TimerActionsOfflineServerTest --no-daemon` with `TOKKL_TEST_AVD` set. Expect the stated cases executed, zero failures and successful runner receipts. Retain case counts, timing and image digest in evidence.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Use the existing `TokklTestServer`/credential-file support and the image pinned by task 1. Another credential must address the same user, such as password auth alongside that user's API token. A second user would not exercise shared timer state. Unsupported-server checks may start the old digest-pinned 1.8.1 image using the existing runner setup pattern, without changing the primary pin or introducing a replacement harness.

## Input Dependencies

Hard dependency on task 10's complete server-backed app and surviving queue/migration paths. No soft dependencies.

## Output Artifacts

Focused real-server integration tests, an explicit scenario-to-test/surface matrix and retained results on both supported emulator API levels.

## Implementation Notes

Write a few tests, mostly integration. Write tests for custom business logic and algorithms; critical user workflows and data transformations; edge cases and error conditions in core functionality; integration points between components; and complex validation or calculation logic. Do not write tests for third-party library/framework functionality, simple CRUD without custom logic, trivial getters/setters/static configuration, or obvious functionality that would break immediately if incorrect. Combine related scenarios into one task and favor integration/critical-path coverage over per-method tests. Never create one test task per CRUD operation.

<details>
<summary>Cross-device and offline workflow coverage</summary>

1. Inventory retained tests before adding cases. Reuse existing server helpers, native action helpers and component tests; record the matrix showing which test proves each required action/surface. Do not multiply identical transport tests across every button.
2. Make remote actions with a distinct credential for the signed-in user and the real API. Keep tokens/passwords in the runner's private credential files, never process arguments or retained logs. Use API responses as the authoritative phase/revision/total assertions.
3. Background the activity while SSE remains active, start a real short session and measure observable arrival against the action completion with a monotonic deadline. Verify actual ringing/full-screen lifecycle at its planned end. Use bounded condition waits rather than arbitrary sleeps.
4. Capture a revision, change the phase remotely, then invoke the phone action with the captured revision. Assert 409, no extra server transition, holder adoption, message and remote silencing. An action that silently upgrades to a fresh revision must fail the test.
5. Exercise offline actions through actual consumer paths. Check the held state and queue before/after; separately assert immediate queued Start/Stop. Do not fake timer API responses or infer a pass only from a snackbar.
6. Use an older real pinned server for the absent-capability scenario and inspect bar/rail plus native controls. Preserve the main new-server pin and the runner's setup, isolation and cleanup rules; reuse existing test setup mechanisms rather than adding a new harness.
7. Run focused tests on each AVD through the maintained script in OOM-safe scopes. Confirm nonzero per-class JUnit cases and device/orchestrator receipts. Fix all known focused failures before task 12's full-suite validation.

</details>

## Execution record

Run on 2026-10-28 on branch `feature/2--server-owned-pomodoro-and-pause-on-android`, against `ghcr.io/e0ipso/tokkl:1.10.0@sha256:207db4f2a6547fea35ad898570fef892f55ef3ab26b6f142bfc3e3e0e680ad2f`. The older server is `ghcr.io/e0ipso/tokkl:1.8.1@sha256:70106ba2a6bc5b23c11f443f33bf2c7fb76d3af75b0335461047c4d27bd52e9b`, which the runner now starts beside it. Both device runs used the same sources.

The other device signs in with the throwaway account's email and password (`TestAccounts.Account.otherDevice`). The phone signs in with the account's API token. Both are the same user. The other device's API answers are what the assertions compare with.

### Results

| AVD | Command | Exit | JUnit cases | Failures |
| --- | --- | --- | --- | --- |
| tokkl-api36 | `env TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.timerstate.TimerCrossDeviceServerTest,com.github.e0ipso.tokkl.timerstate.TimerActionsOfflineServerTest --no-daemon` | 0 | 4 + 3 | 0 |
| tokkl-api31 | the same with `TOKKL_TEST_AVD=tokkl-api31` | 0 | 4 + 3 | 0 |

Each run printed a `TOKKL_DEVICE_BUILD` receipt for the emulator it booted (SDK 36 and SDK 31), had no `AndroidTestRunner failed` line, and wrote one non-empty JUnit file. Both ran inside `systemd-run --user --scope -p OOMPolicy=continue -- choom -n 1000`.

Times the tests logged under the `TimerCrossDevice` tag, from the moment the other device's request returned:

| Event | API 36 | API 31 |
| --- | --- | --- |
| Remote session start held by the backgrounded phone / session notification posted | 12 ms / 12 ms | 12 ms / 23 ms |
| Work phase rang after its planned end | 86 ms | 98 ms |
| Remote Next phase: state held / alarm closed / ring stopped | 22 / 22 / 96 ms | 10 / 10 / 76 ms |
| Short rest rang after its planned end | 48 ms | 57 ms |
| Remote End session: state held / alarm closed / ring stopped | 11 / 11 / 59 ms | 0 / 0 / 143 ms |
| Phone-started session's work phase rang after its planned end | 70 ms | 85 ms |
| Remote Resume held by the phone | 10 ms | 10 ms |
| Chain total, server against phone, at one sampled instant | 600 s / 601 s, then 601 s / 601 s | 600 s / 600 s, then 601 s / 601 s |

The limit is 2000 ms for arrival and silencing, 10 s for a ring after the planned end (never before it, with 1 s of clock tolerance) and 2 s for chain totals.

Also run: `CapabilitiesTest.favoritesAreFalseWhenMeLacksRole` (JVM, 1 case, 0 failures), and on tokkl-api36 the existing callers of `TimerActionToasts.await`, whose wait changed. `RunningNotificationServerTest#anOfflinePauseFromTheNotificationSaysWhyAndChangesNothing`, `ReminderServerTest#reminderPauseGoesToTheServerAndOfflineKeepsTheReminder`, `WidgetServerTest#anOfflinePauseSaysWhyAndKeepsTheTimerWhileStopStillQueues` and `RunningTileServerTest#aTapOnThePausedTileResumesOnTheServerAndOfflineSaysWhy` passed. `RunningTileServerTest#anOfflineLongPressPauseSaysWhyAndStopStillQueues` failed once because SystemUI never opened the compact controls after the long-click, before the toast helper ran, and passed alone on the rerun. These were not rerun on tokkl-api31.

### Scenario to test and surface

| Scenario | Proved by | Surface |
| --- | --- | --- |
| Remote session reaches the backgrounded phone in 2 s with the server's phase, countdown, number and upcoming phase | `TimerCrossDeviceServerTest#aSessionFromAnotherDeviceReachesTheBackgroundedPhoneAndItsNextAndEndSilenceTheRing` | held state, session notification |
| Ring at the server's planned end | the same, and `#aSessionFromThePhoneShowsThroughTheApiAndAStaleNextFromTheRingIsRefused` | ringing service |
| Remote Next phase and End session stop the ring and close the alarm in 2 s, and replace the phase | the first case | ringing service, full-screen alarm |
| Phone-started session shows through the API with description, project, tag, billable and the four lengths | `#aSessionFromThePhoneShowsThroughTheApiAndAStaleNextFromTheRingIsRefused`; retained `PomodoroPageServerTest#theFormSendsItsLengthsAndFieldsAndNextPhaseAndEndFollowTheServer` | Pomodoro page |
| Stale phone Next: 409, no extra transition, adoption, message, ring stops | `#aSessionFromThePhone...` with the stream held back; retained `PomodoroPageServerTest#aTapDrawnBeforeAnotherDeviceMovedTheTimerOnIsRefusedAndTheServersStateShows` | ringing notification (stale native revision), Pomodoro page |
| Pause and Resume both ways, one total, Discard elsewhere clears the chain | `#pauseResumeAndDiscardFromEitherDeviceShareOneChainAndOneTotal` | running notification, held state |
| A running clone's Stop is a queued v9 stop that ends the chain on the server | `TimerActionsOfflineServerTest#aClonesStopAndPlainStartAndStopChangeThePhoneAtOnceAndGoThroughTheQueue`; JVM `TimerSurfaceActionsTest#stopOfARunningEntryIsQueuedAlsoForAResumedClone` | Timer page |
| Pause offline | `TimerActionsOfflineServerTest#pauseResumeAndDiscardFailOfflineOnThePageTheNotificationAndTheWidget`; retained `WidgetServerTest#anOfflinePauseSays...`, `RunningTileServerTest#anOfflineLongPressPauseSays...`, `ReminderServerTest#reminderPauseGoesToTheServerAndOfflineKeepsTheReminder`, `FullscreenTimerServerTest#offlineActionsAndSharedEditorsKeepCumulativeTimeAndFinalStopExits` | Timer page, running notification, widget, tile, reminder, full-screen timer |
| Resume and Discard offline | `#pauseResumeAndDiscardFailOffline...`; retained `RunningTileServerTest#aTapOnThePausedTileResumesOnTheServerAndOfflineSaysWhy`, `FullscreenTimerServerTest#offlineActions...` | Timer page, running notification, widget, tile tap, full-screen timer |
| Start session, Next phase and End session offline | `#sessionStartNextAndEndFailOfflineOnThePageTheNotificationsAndTheAlarm` | Pomodoro page, session notification, ringing notification, full-screen alarm |
| Ordinary Start and Stop offline change the phone at once | `#aClonesStopAndPlainStartAndStop...`; retained `TimerServerTest#startEditsAndStopWorkInAirplaneModeAndReachTheServerAfterwards` | Timer page |
| Older Tokkl hides Pomodoro and Pause, Start and Stop reach it, no stream | `TimerCrossDeviceServerTest#anOlderTokklWithoutTheTimerApiHidesPomodoroAndPauseWhileStartAndStopWork`; JVM `TileControlsTest#pauseIsLeftOutWithoutTheTimerApiAndDuringAPomodoroSession`; retained `ReminderServerTest#withoutTheTimerApiTheReminderOffersNoPause` | bar, rail, Timer page, full-screen timer, running notification, widget, tile, reminder |
| Toggl Track decides no timer API | JVM `CapabilitiesTest#favoritesAreFalseWhenMeLacksRole` (now asserts it) and `#aMissingTimerRouteMeansAbsentAndTurnsAPresentFlagOff` | capability |
| Repeated discarded-pomodoro notice shows once | retained `TimerNoticeServerTest#aTooShortPomodoroIsSaidOnceAndNotAgainWhenItsStateArrivesAgain`; JVM `TimerStateSourceTest#aDiscardedPomodoroGivesOneNoticeAcrossReplaysAndRestarts` | Timer page |
| Old native buttons after a phase change send nothing | retained `PomodoroSessionNotificationServerTest#nextAndEndGoToTheServerWithTheShownRevisionAndOfflineChangesNothing`, `PomodoroRingingServiceTest#staleButtonsCannotAdvanceEndOrRepostThePhaseThatReplacedThem`; JVM `TimerSurfaceActionsTest#resumeSendsTheRevisionTheControlWasDrawnAt` | session and ringing notifications, widget, tile |

Every offline case checks the exact message, the held state with its server clock set aside, the queue count, and afterwards the server's revision through the other device. Resume and Stop while paused offline on the tile's long press have no device test of their own. They go through the same `TimerSurfaceActions` and `showTimerActionFailure` as the widget and the notification, which are tested offline.

### Notes

- The stale Next case stops the phone's stream with `TokklApplication.timerSync.stop()` before the other device's Next phase, as a slow network would delay it. With the stream up, the phone adopts the new phase first, and a native button of a phase it no longer holds sends nothing.
- In the first API 36 run, the offline Pause case found a notification with the running timer's id and no actions. `RunningNotifier` always adds Stop, so it was not the app's running notification. Which notification it was is unverified. `TimerSurfaces.runningNotification` now takes only the one on the running timer's channel that is no group summary, and names every notification with that id when it times out. It passed in the three later runs that used it.
- No app bug was found.
