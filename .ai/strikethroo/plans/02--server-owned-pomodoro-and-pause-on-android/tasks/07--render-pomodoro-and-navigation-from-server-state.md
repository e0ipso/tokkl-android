---
id: 7
group: "timer-surfaces"
dependencies: [3, 4]
status: "pending"
created: 2026-10-28
skills:
  - jetpack-compose
  - kotlin
complexity_score: 5
execution_profile: "standard-implementation"
---
# Render Pomodoro and navigation from server state

## Objective

Turn the Pomodoro page into a view/controller of the server session and gate its navigation destination by capability.

## Skills Required

Jetpack Compose screens/navigation and Kotlin presentation models.

## Acceptance Criteria

- [ ] The idle form retains entry fields, per-session lengths and all four persisted defaults. Start sends those values through the server action client; no local session is created.
- [ ] Active and focused Pomodoro views show the held phase, corrected countdown/overtime, upcoming phase and `Pomodoro N of M` using the server's `number` and `long_rest_every`. Next and End use the displayed revision and standard conflict/offline messages.
- [ ] Edits of active description, project, task, tags and billable update `pomodoro.entry_id` through the existing queued running-entry edit path. The server carries those fields to later pomodoros; the phone does not maintain a second copy of session rules or fields.
- [ ] Without `timerState`, the Pomodoro destination is absent from bar/rail and unavailable through restored/direct app navigation. A capability change while it is open returns to an available destination. Alarm entry-point gating belongs to task 8.
- [ ] Permission-denied guidance and its request controls remain visible where relevant on the Pomodoro page. The four defaults and their Settings controls are retained.
- [ ] Rewrite `PomodoroScreenModelTest` and phase-action presentation tests for server states. Run `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroScreenModelTest' --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroPagePhaseActionsTest' --tests 'com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSettingTest'`; expect nonzero tests and zero failures.
- [ ] Run the screenshot package on tokkl-api36 through `scripts/instrumented-tests.sh -Pandroid.testInstrumentationRunnerArguments.package=com.github.e0ipso.tokkl.ui.screenshot --no-daemon`. Review and update affected Pomodoro/bar/rail references in this task's commit, then rerun to zero failures.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

Own `PomodoroScreenModel`, `PomodoroViewModel`, `PomodoroScreen`, their focused state and `navigation/AppNavigation.kt` capability routing. Reuse shared state/clock values and task 3's actions. The alarm presentation and session notification are separate consumers in task 8.

## Input Dependencies

Hard dependencies on tasks 3 and 4. No soft dependencies; the service and Timer UI are independent producers/consumers of the same established state contract.

## Output Artifacts

Server-driven Pomodoro presentation/start/edit actions, capability-gated navigation, retained defaults/permission UI and updated presentation checks/references.

## Implementation Notes

<details>
<summary>Pomodoro page migration</summary>

1. Read all Pomodoro screen/model callers and defaults code. Keep the start-form length validation and default persistence, while replacing session/rules reads with task 2's state flow and derived values.
2. Send Start/Next/End through the direct client. Preserve the state that rendered each action's revision so a remote transition cannot cause an old tap to advance again. Failure displays the standard message and leaves the displayed held state intact.
3. Route active fields through the existing running-entry edit operation for the server's open entry id. Do not snap/save a pomodoro or change the local phase as part of an edit.
4. Filter the destination list used by both bar and rail. Guard restored/deep navigation using the same flag, without renaming saved destinations unnecessarily.
5. Keep notification, exact-alarm and full-screen permission status/request UI. This task must not delete the sound or alarm implementations, which task 8 converts.
6. Rewrite existing presentation fixtures to hold server `TimerState` values instead of constructing local rules. Capture reviewed screenshot changes in the same commit. All Gradle/device runs use the host's OOM-safe scope.

</details>
