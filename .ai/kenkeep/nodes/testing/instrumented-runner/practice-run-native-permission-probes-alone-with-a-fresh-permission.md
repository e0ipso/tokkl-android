---
type: practice
title: Run native permission probes alone with a fresh notification permission
description: >-
  TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=1 resets POST_NOTIFICATIONS before
  one probe method starts, on an API 33+ emulator the script boots.
tags:
  - testing
  - android
  - emulator
  - notification
kk_schema_version: 3
kk_id: practice-run-native-permission-probes-alone-with-a-fresh-permission
kk_derived_from: []
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
  - practice-pass-class-and-package-lists-through-runner-filter-files
kk_depends_on: []
kk_confidence: high
---
The probes that answer Android's real notification permission dialog need the permission revoked before their instrumentation process starts. Revoking it inside a test kills that test's process. So they live in `app/src/notificationPermissionTest/java`, which `app/build.gradle.kts` adds to the androidTest sources only when the Gradle property `tokklFreshNotificationPermission` is `true`. Ordinary suites never compile them and keep their own permission and process-restart setup.

Run each probe method on its own, through the script:

```sh
TOKKL_TEST_AVD=tokkl-api36 TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=1 scripts/instrumented-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.notification.NativeNotificationPermissionTest#allowingTheNativeDialogPostsTheAlreadyRunningTimer
```

The script accepts exactly one `class=` argument naming one of four methods: `NativeNotificationPermissionTest#allowingTheNativeDialogPostsTheAlreadyRunningTimer`, `NativeNotificationPermissionTest#denyingTheNativeDialogKeepsTheTimerAndOpensItsSettings`, `NativePomodoroPermissionTest#grantingTheFirstSessionDialogKeepsTheSameStartedSession` and `NativePomodoroPermissionTest#denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry`. It checks that before it allocates a device. It then requires an API 33 or later emulator that it booted itself and never touches an attached device, even one vouched for with `TOKKL_TEST_THROWAWAY_EMULATOR=1`. It installs the debug app with the property set, runs `pm revoke` for `POST_NOTIFICATIONS` and clears the `user-set` and `user-fixed` flags, and only then starts the test.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-pass-class-and-package-lists-through-runner-filter-files](practice-pass-class-and-package-lists-through-runner-filter-files.md)
<!-- kk:related:end -->
