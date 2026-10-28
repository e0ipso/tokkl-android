---
id: 9
group: "timer-surfaces"
dependencies: [5, 6]
status: "completed"
created: 2026-10-28
skills:
  - android-components
  - kotlin
complexity_score: 6
complexity_notes: "The existing native surfaces share one TimerControls target contract. Migrating that contract once across its consumers avoids independent pause policies; Compose timer controls and session/alarm notifications are separate tasks."
execution_profile: "complex-architecture"
---
# Move native timer actions to server pause state

## Objective

Make the running notification, widget, Quick Settings tile and long-running reminder reflect and control the server pause chain.

## Skills Required

Android native component actions and Kotlin state/intent integration.

## Acceptance Criteria

- [x] Running/paused notification, widget and tile read task 6's shared effective timer model, including server carried seconds and frozen paused totals. Their native ticking clocks keep working without a periodic redraw loop.
- [x] Pause, Resume and Stop-while-paused use the direct action client. Every revisioned action carries the server revision it displayed, including after process restart, instead of local chain/session/epoch fields.
- [x] Without the capability, Pause/Resume are hidden on every native surface and reminder. Pause is hidden during a Pomodoro session. Ordinary Start, Continue, favorite Start and Stop of a running entry remain queued offline actions.
- [x] Offline/refused actions preserve state and report the standard message through each surface's existing failure path. Dismissal/restoration behavior for notifications and stale-action protection remain consistent with their displayed state.
- [x] The long-running reminder retains its existing WorkManager timing, cancellation and Keep running behavior. Only its Pause routing/gating changes; no new periodic work is added.
- [x] Rewrite affected native model checks and run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.widget.WidgetStateTest' --tests 'com.github.e0ipso.tokkl.tile.TileControlsTest' --tests 'com.github.e0ipso.tokkl.tile.RunningTileContentTest' --tests 'com.github.e0ipso.tokkl.reminder.ReminderSyncTest'`; expect nonzero tests and zero failures.
- [x] Run `TOKKL_TEST_AVD=tokkl-api36 scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.widget.WidgetServerTest,com.github.e0ipso.tokkl.tile.RunningTileServerTest,com.github.e0ipso.tokkl.reminder.ReminderServerTest --no-daemon` after adapting moved-behavior cases. Expect nonzero tests, zero failures and successful runner receipts.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Own `notification/` running-timer state/actions, `widget/`, `tile/` and reminder Pause wiring, plus their composition-root connections. Task 8 owns Pomodoro session/ringing notifications. Keep cached ordinary entry operations available with the SSE stream offline.

## Input Dependencies

Hard dependencies on task 6's revised shared control model/targets and task 5's account service wiring. No soft dependencies.

## Output Artifacts

Server-backed native timer controls, persisted server-revision action targets, capability-aware content and rewritten native checks.

## Implementation Notes

<details>
<summary>Native action conversion</summary>

1. List `ChainMark`, `TimerSession`, chain id/epoch/revision and `TimerControls` callers in running notifications, widget callbacks, tile tap/long-press controls and reminder receivers.
2. Replace target encoding with the server revision for Resume/Discard. Continue validating ordinary Start/Stop entry targets using the cache's existing entry protections, rather than carrying forward a local pause-chain identity.
3. Source carried/frozen elapsed values from the shared read model. Keep launcher/system Chronometer ticking and existing event-driven publication. Do not schedule a widget timer or short poll to display seconds.
4. Apply capability and Pomodoro restrictions consistently to content and action execution. A stale intent from before capability loss must not reach removed local behavior. Respect account isolation on cold receiver/callback paths.
5. Preserve native failure feedback and verify the exact offline message for notification, widget, tile and reminder Pause. Running-entry Stop remains the queue's operation even when that entry is a resumed clone.
6. Rewrite tests whose pause behavior now belongs to server state; keep ordinary queued operation checks. Use the maintained instrumented runner and the host's OOM-safe scope for verification. Do not change reminder cadence or session notification behavior here.

</details>
