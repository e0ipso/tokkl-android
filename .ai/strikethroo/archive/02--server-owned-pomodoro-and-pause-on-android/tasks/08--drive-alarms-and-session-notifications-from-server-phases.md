---
id: 8
group: "timer-surfaces"
dependencies: [5, 7]
status: "completed"
created: 2026-10-28
skills:
  - android-alarms
  - android-notifications
complexity_score: 6
complexity_notes: "Alarm scheduling, ringing and its session notification form one phase lifecycle keyed by phase.start. Their cancellation and revision targets must agree. Transport, screen modeling and platform acceptance are separate tasks."
execution_profile: "complex-architecture"
---
# Drive alarms and session notifications from server phases

## Objective

Schedule and ring the existing Android Pomodoro alarm from the server phase, and silence it when any client changes that phase.

## Skills Required

Android exact alarms and notification/ringing lifecycle.

## Acceptance Criteria

- [x] One `setAlarmClock` is scheduled at `phase.planned_end - clockOffset`, keyed by `phase.start`. Accepting a state recomputes the schedule, and a missed end rings immediately without advancing a phase.
- [x] Reboot, app update, clock/timezone changes and exact-alarm permission grants rearm from persisted server state without requiring a request. Capability false or signed-out state schedules no session alarm.
- [x] A new `phase.start` or null Pomodoro cancels the old alarm, stops the ringing service and closes the full-screen alarm. A revision change within the same phase does not dismiss its ringing.
- [x] Sounds follow the server `transition`. Existing sound, vibration, full-screen access, channels and denied-permission behavior remain. Stale phase deliveries cannot ring an old phase.
- [x] Session notification and alarm content use held phase/countdown/overtime, upcoming phase and server number. Next/End intents carry the displayed server revision and call the direct client; offline failure leaves state and ringing unchanged and shows the standard message.
- [x] Alarm/direct entry points refuse the Pomodoro screen when the capability is absent. Process restart restores content from the persisted holder rather than the removed Room session.
- [x] Rewrite `PomodoroPhaseAlarmTest`, `PomodoroRingingTest`, `PomodoroAlarmModelTest` and `PomodoroSessionNotificationTest` around held states. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarmTest' --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroRingingTest' --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmModelTest' --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationTest'`; expect zero failures and nonzero tests.
- [x] Run the screenshot package on tokkl-api36 through the instrumented runner, review changed alarm captures and commit updated references with the visual change. The rerun reports zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Own alarm scheduler/rearm receiver, alarm model/activity, phase-end tracking, ringing lifecycle and session notification sync/content/action receivers in `pomodoro/`. The `mediaPlayback` ringing service remains separate from task 5's silent SSE service.

## Input Dependencies

Hard dependencies on task 5's persistent service/account lifecycle and task 7's server-backed Pomodoro presentation. No soft dependencies.

## Output Artifacts

Server-phase exact alarms, remote silencing and full-screen dismissal, server-state session notifications/action intents, rewritten lifecycle checks and reviewed alarm references.

## Implementation Notes

<details>
<summary>Phase alarm and notification migration</summary>

1. Inventory callers of `PomodoroSessionStore`, phase-end storage and existing alarm/session intents in these consumers. Replace local session ids/phase objects with held server state and a `phase.start` identity.
2. Keep scheduler times in device epoch milliseconds by subtracting the holder's offset from the server planned end. Derive overtime for display; never call a local next-phase rule or save an entry at alarm delivery.
3. Use the persisted holder in cold receivers. Validate phase identity before ringing, cancelling or executing an old action. Cancel alarms and ringing on phase replacement/session removal even when the SSE update arrived with the app backgrounded.
4. Preserve `transition` sound mapping and full-screen permission checks. Bind the alarm activity to the held phase so a remote confirm finishes it. A metadata-only revision change keeps the same phase ringing.
5. Revisioned notification and alarm actions use task 3's adapter. Route their offline/conflict errors through the existing failure paths without dismissing an alarm solely because a request was attempted.
6. Retain reboot/update/time/permission rearm handling and coordinate its receiver with task 5's sync-service start. Rewrite tests of custom scheduling/cancellation logic; delete local-rules-only assertions as their callers leave. Use OOM-safe verification scopes and review screenshot diffs before accepting new references.

</details>
