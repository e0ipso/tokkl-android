---
schema_version: 1
summaries:
  api-and-security: >-
    the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token
    storage and request rules; read before adding a request or endpoint, or
    changing trust, credentials or privacy
  build-and-tooling: >-
    the JDK, Android SDK, Gradle build, Android stack, commit hook and agent
    host limits on this machine; read before building, committing, adding a
    dependency or running a long job
  data-and-offline: >-
    account settings and time, the Room cache, the change queue, the running
    timer's notification and the TypeScript ports; read before changing what the
    phone stores or sends, or how it reads and writes times
  data-and-offline/change-queue: >-
    offline entry mutations, delivery, recovery and picker data; read before
    changing the entry queue or its sender
  data-and-offline/typescript-time-ports: >-
    JavaScript-compatible time and formatting ports; read before porting Tokkl
    parsing, rounding or timezone behavior
  design: >-
    Tokkl's theme, palette, brand rules and shared UI components; read before
    changing how anything looks or reads to TalkBack
  release: >-
    the app ID, versioning, reproducible signed builds, CI jobs and F-Droid
    metadata; read before touching versions, signing, CI or store text
  screens: >-
    sign-in and sign-out, the navigation host with its bar, rail and window
    sizes, the Timer page, the timer and entry form, Reports, Trash and the
    catalog pages; read before changing a screen, its model or navigation
  screens/session-and-navigation: >-
    sign-in, account lifetime, navigation and adaptive chrome; read before
    changing authentication, sign-out or page routing
  screens/timer-and-entry-editing: >-
    timer controls, shared fields, entry forms and menus; read before changing
    entry editing or local start and stop behavior
  screens/timer-history-calendar: >-
    Timer layout, cached totals, history and calendar; read before changing
    timer totals, entry browsing or saved view state
  testing: >-
    instrumented, screenshot and real-server tests on the plain AOSP emulators;
    read before writing or running any test on a device or emulator
  testing/emulator-runtime: >-
    AOSP emulator images, ownership, rendering and capture; read before
    preparing a device run or investigating emulator resource use
  testing/instrumented-runner: >-
    instrumented server setup, credentials, selectors and run receipts; read
    before invoking or modifying the device test script
  testing/instrumented-test-authoring: >-
    portable device tests, shared helpers and UI assertions; read before adding
    or changing Android regression and acceptance tests
---
# kenkeep Folder Summaries

- `api-and-security`: the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token storage and request rules; read before adding a request or endpoint, or changing trust, credentials or privacy
- `build-and-tooling`: the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job
- `data-and-offline`: account settings and time, the Room cache, the change queue, the running timer's notification and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times
- `data-and-offline/change-queue`: offline entry mutations, delivery, recovery and picker data; read before changing the entry queue or its sender
- `data-and-offline/typescript-time-ports`: JavaScript-compatible time and formatting ports; read before porting Tokkl parsing, rounding or timezone behavior
- `design`: Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack
- `release`: the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text
- `screens`: sign-in and sign-out, the navigation host with its bar, rail and window sizes, the Timer page, the timer and entry form, Reports, Trash and the catalog pages; read before changing a screen, its model or navigation
- `screens/session-and-navigation`: sign-in, account lifetime, navigation and adaptive chrome; read before changing authentication, sign-out or page routing
- `screens/timer-and-entry-editing`: timer controls, shared fields, entry forms and menus; read before changing entry editing or local start and stop behavior
- `screens/timer-history-calendar`: Timer layout, cached totals, history and calendar; read before changing timer totals, entry browsing or saved view state
- `testing`: instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test on a device or emulator
- `testing/emulator-runtime`: AOSP emulator images, ownership, rendering and capture; read before preparing a device run or investigating emulator resource use
- `testing/instrumented-runner`: instrumented server setup, credentials, selectors and run receipts; read before invoking or modifying the device test script
- `testing/instrumented-test-authoring`: portable device tests, shared helpers and UI assertions; read before adding or changing Android regression and acceptance tests
