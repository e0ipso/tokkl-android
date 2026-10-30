---
type: practice
title: Let the runner own emulator boot and settings
description: 'The runner owns adb startup, emulator readiness and temporary device settings.'
tags:
  - testing
  - android
  - emulator
kk_schema_version: 3
kk_id: practice-let-the-runner-own-emulator-boot-and-settings
kk_derived_from:
  - scripts/instrumented-tests.sh
  - scripts/emulator-discovery.py
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
kk_depends_on: []
kk_confidence: high
---
Never start an emulator or an adb server by hand for a test run, not even to poll for readiness. The slots and locks cover only runs of the script, an emulator started by hand gets no scope or memory cap, and an OOM kill of it ends the whole agent session. The script stops the adb server at the end only when it was not running before, so a server started by hand stays up and later runs reuse it.

A fresh headless boot often leaves a "System UI isn't responding" dialog that holds window focus, so injected keys go to the dialog instead of the app. `adb shell settings put global hide_error_dialogs 1` stops new ones and `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` closes open ones. The script does both on an emulator it boots, so tests no longer close dialogs themselves. It sets `font_scale` to 1.0 there too: the AVD keeps its font scale across boots, and `SystemFontScale` puts back whatever it read, so one run left at 2.0 made every later class run at 200%. It also sets `show_ime_with_hard_keyboard` to 1, because without it focusing a field never opened the soft keyboard on the headless emulator. It changes no setting on a device it did not boot. CI's emulator step sets `hide_error_dialogs` and `show_ime_with_hard_keyboard` itself.

On its own emulator boot, the runner also checks that `com.android.inputmethod.latin/.LatinIME` is installed, enables and selects it, and verifies the selection. An AVD can retain agent-device's nonvisual `com.callstack.agentdevice.imehelper/.TestInputMethodService` as its default input method. The hardware-keyboard setting alone cannot make that helper draw a keyboard: input can be active and accepting text while the visible IME height stays zero. Preserve real keyboard, touch and inset assertions; use the runner's boot setup rather than changing those tests or uninstalling the automation helper. Attached devices are left unchanged.

Concurrent boots keep their private runtime directories and netsimd isolation. When a standard user runtime directory is available, the runner starts authenticated localhost gRPC and publishes an owned symlink to its existing discovery record through `scripts/emulator-discovery.py`. This lets the Device hub find its authentication token without exposing it in logs or disabling authentication. Normal and stale-run cleanup remove only links into the run whose ownership checks passed. Manual device control and screen capture still use the Device panel tools; the discovery link supplies their backend, not an alternate UI transport.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] [scripts/instrumented-tests.sh](../../../../../scripts/instrumented-tests.sh)
[2] [scripts/emulator-discovery.py](../../../../../scripts/emulator-discovery.py)
<!-- kk:citations:end -->
