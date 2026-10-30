# Device support diagnosis — 2026-10-30

## Confirmed failure

The Device hub cannot authenticate to the runner-owned emulator's gRPC endpoint. This is separate from the app's assertions.

- T3 0.1.5, Device hub 0.19.0-canary-20261023-1357114, agent-device 0.21.31 were running. Device discovery returned the runner's API36 emulator successfully. API31 was neither opened nor booted.
- The maintained runner booted API36 as emulator-5554 in `tokkl-emulator-5554-66f429e6.scope`, with lavapipe, read-only disks and an isolated runtime directory.
- `device_open` attached this thread to that already-running emulator. `device_screenshot` returned `Could not communicate with device support. Try refreshing devices.`
- A read-only request to the hub's diagnostic `/vendor/serve-emu/api?device=emulator-5554` endpoint exposed the actual HTTP 503 response: `{"ok":false,"error":"getScreenshot: grpc-status 16 (Missing the 'authorization' header with security credentials.)"}`. Its temporary cooldown response was `serve-emu start for emulator-5554 is cooling down after a failure`.
- The refresh advice was followed once with `device_list`. The single screenshot retry failed. No alternate UI-control or screenshot transport was used. This thread's panel attachment was then closed without a shutdown request; the runner cleaned up its own emulator.

## Cause, supported by installed source and runtime metadata

`scripts/instrumented-tests.sh` deliberately gives each concurrent emulator a separate `XDG_RUNTIME_DIR` to prevent sharing netsimd and killing another run's emulator. For this run it was `/run/user/1000/tokkl-instrumented-tests-1000-runs/33749c93/runtime`.

The live emulator wrote its discovery record under that directory's `avd/running/pid_3381668.ini`. Metadata inspection confirmed console port 5554, gRPC port 8554, AVD tokkl-api36, and a **present** gRPC token. No token value was printed or saved in this report.

The installed Device hub's `vendor/serve-emu/dist/emulator-grpc.js`, `discoveryDirs()`, searches standard home/temp locations and **the hub process's** `XDG_RUNTIME_DIR/avd/running`. It does not search the runner's private directories. `ensureEmulatorGrpcEndpoint()` then tries activating the emulator endpoint and can return a reachable endpoint with `token:null`. The authenticated emulator rejects that connection with gRPC status 16.

The installed middleware's `createRouter()` calls `createApp()` and requires a working stream even for its inspection routes. Consequently the Device screenshot route never reaches its own PNG capture while initial gRPC stream authentication is broken. T3's `makeDeviceHubOperations()` maps this non-2xx HTTP result into the generic `request_failed` message. Refresh only repeats discovery; it cannot make the missing authentication record visible.

No global T3 restart, tool downgrade, authentication bypass, or change to emulator isolation is justified by this diagnosis.

## Repair to implement on resume

Have the maintained runner publish a symlink to its existing emulator discovery record in the same user's standard `$XDG_RUNTIME_DIR/avd/running` directory once the owned emulator is ready. Keep the record in the private run directory, retain its existing permissions, and use the emulator's PID-based filename. Never print, duplicate into logs, or disable its token.

Make publication and cleanup ownership-aware: never overwrite an existing unrelated path; remove only links targeting this run's private discovery record; include normal cleanup and stale-run cleanup after ownership/liveness checks. Retain the per-emulator runtime directory and netsimd isolation. Add filesystem lifecycle tests covering publication, unrelated-path protection, permissions and cleanup.

Then boot only API36 through the maintained runner, attach with `device_open`, and verify `device_screenshot` before treating support as restored. The attempted ad hoc link publication in this diagnostic made no changes: the short test run had already exited and removed its record, so its source-count assertion stopped the command. **No repair or successful screenshot is claimed.**

## Focused test results, separate from Device support

The diagnostic used the two previously failing test selectors, not the full widget matrix. See `task5/api36-diagnostic-focused/` for the exact argv, before/after hashes, terminal XML, runner log and process-isolation receipt. The runner finished naturally with exit 1, two executed cases, two failures, zero skips, and unchanged hashed inputs.

1. `signedOutAndReplacementAccountRejectOldNativeSessionTapsAndForgetFields`: `NullPointerException` at `PomodoroWidgetServerTest.kt:144`, in replacement-account setup. The test reads `app.accountSettings.state.value!!` before it has a value.
2. `remoteStartArrivesWithCorrectedCountdownAndNativeStartUsesDefaultsAndLastFields`: cleanup assertion at `NativeWidget.kt:281`, `the disposable host has no widgets expected:<0> but was:<1>`. Investigate native host allocation/lifetime and whether the assertion counts another provider host. Do not label this a production failure or a passing scenario without isolating it.

These are complete focused-run results; they supersede the previous “corrected but not rerun” status for those selectors. They do not establish full API36 acceptance. No references were recorded/replaced, no task was accepted, and no commit or push was made. Task 5 and phase 3 remain halted pending user direction under the blueprint's Failure Modes rule. API31 remains deferred to CI.

## Authorized recovery — 2026-10-30

The user directed applying the discovery fix, isolating both tests, and resuming API36 acceptance. `scripts/emulator-discovery.py` now publishes links to the matching live emulator's existing discovery record, requires private user-owned directories, refuses unrelated collisions, and removes only links into the run being cleaned up. The runner invokes cleanup after its existing scope/ownership checks, including stale-run recovery.

The initial publication attempt revealed that this emulator does not create a discovery record until gRPC starts. That attempt stopped during setup and executed **zero tests**. Its receipt originally copied old build XML; those stale files are explicitly separated in `task5/api36-discovery-fix-focused/stale-artifacts-not-from-this-run/`, and its corrected summary credits zero cases. The evidence wrapper now excludes artifacts older than its start time.

The runner now explicitly starts a localhost gRPC endpoint on a free port with `-grpc-use-token`, then publishes its record after boot readiness. It retains the bounded scope, read-only AVD, private runtime and netsimd isolation. No credentials are logged and no global service restart is needed.

Verification: five filesystem lifecycle/security tests passed with `python3 test/emulator-discovery.test.py` after a recorded failing stub run. `bash -n scripts/instrumented-tests.sh` and `git diff --check` passed. On the subsequent maintained-runner API36 boot, `device_list` and `device_open` attached emulator-5554 and `device_screenshot` **succeeded**, returning an actual 1080×2400 PNG of the launcher to this conversation. This proves capture recovery for the authenticated, runner-owned instance; app acceptance remains a separate result. Focused run receipt: `task5/api36-discovery-auth-focused/`.

## Full-suite follow-up: nonvisual keyboard selected in the AVD

The first full API36 run completed naturally under the detached service: 736 terminal JUnit cases, 10 failures, zero skips and unchanged inputs (`task5/api36-full-final/`). All 33 widget cases passed, including all eight reference classes and their 208 comparisons. Four failures were broad WorkManager queries counting framework Glance work; six were keyboard-dependent tests elsewhere in the app.

The keyboard failures must not be dismissed as six separate focus/assertion mistakes. Native logs consistently show the IME rejecting show requests at `PHASE_IME_ON_SHOW_SOFT_INPUT_TRUE`. Android16's [InputMethodService implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/inputmethodservice/InputMethodService.java) records that failure when its show-input policy declines the request; this can happen after the app has valid focus. No automatic keyboard-setting write on panel attachment was found in the installed hub's client: initial settings synchronization reads with GET, while settings changes use the explicit user-change callback.

An isolated fresh-boot diagnostic then identified `com.callstack.agentdevice.imehelper/.TestInputMethodService` as the current IME before any panel attachment/action on that boot. The secure hardware-keyboard override was 1, orientation portrait/rotation0, input active and accepting text, focused Compose view attached with a window token, and fullscreen false. The selected helper produced zero visible IME height, with native input view/show flags false. It is the automation helper, not the stock visual AOSP keyboard. A fresh read-only run inherited this installed/default helper from its AVD state.

Authorized remediation: the maintained runner selects and verifies the existing stock AOSP IME only when it owns the emulator boot, alongside its existing keyboard setting. Keep the original keyboard test actions and geometry/state assertions. Do not uninstall or globally disable the automation helper, change external devices, or silently accept missing keyboard prerequisites. Retain diagnostic receipts, remove temporary diagnostic instrumentation once it has served its purpose, and prove the complete failure subset and final suite on the corrected environment. This section records the diagnosis and approved repair scope; passing recovery results will be recorded separately by task 5.

The exact ten-case diagnostic reproduction completed with ten failures and unchanged inputs. After the runner selected and verified `com.android.inputmethod.latin/.LatinIME`, the exact same ten-case selection completed with **10 passes, zero failures/skips, exit0** (`task5/api36-full-failures-green/`). The three files containing the six keyboard cases are byte-identical to the first full-run baseline (`task5/ime-tests-preserved.json`); temporary diagnostics were removed. The two scheduling fixtures now query the positive union of all three Tokkl Worker class tags, preserving their unique-work, idle, retry and cancellation assertions while excluding Glance's independent sessions. Device panel attachment and captures succeeded on the corrected boot. Surrounding and full-suite validation remain separate gates.
