---
schema_version: 1
summaries:
  api-and-security: >-
    the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token
    storage and request rules; read before adding a request or endpoint, or
    changing trust, credentials or privacy
  api-and-security/api-client: >-
    typed API requests, HTTP execution, configured-server security and test
    fixtures; read before changing requests, transport or client wiring
  build-and-tooling: >-
    the JDK, Android SDK, Gradle build, Android stack, commit hook and agent
    host limits on this machine; read before building, committing, adding a
    dependency or running a long job
  build-and-tooling/android-build: >-
    the Android stack, Gradle configuration and static checks; read before
    changing modules, dependencies or build conventions
  build-and-tooling/android-build/gradle-and-modules: >-
    module boundaries, Koin composition, build conventions, Android
    compatibility and Gradle caches; read before changing dependencies, wiring
    or build settings
  build-and-tooling/contributor-guidance: >-
    commit hooks, project documentation and generated artifacts; read before
    committing or changing developer guidance
  build-and-tooling/host-runtime: >-
    the JDK, SDK, daemon isolation and host resource limits; read before
    building or running devices on this machine
  data-and-offline: >-
    account settings and time, the Room cache, the change queue, the running
    timer's notification and the TypeScript ports; read before changing what the
    phone stores or sends, or how it reads and writes times
  data-and-offline/account-cache: >-
    the Room cache ownership, capability probes and account refresh ordering;
    read before changing cached data, optional server features or refresh steps
  data-and-offline/account-settings-and-time: >-
    account time fields, timezone validation and settings refresh; read before
    changing time formatting, account settings or cache refresh ordering
  data-and-offline/change-queue: >-
    offline entry mutations, delivery, recovery and picker data; read before
    changing the entry queue or its sender
  data-and-offline/timer-state-and-sync: >-
    server timer acceptance, account lifetime, online actions and event-stream
    services; read before changing Pomodoro, pause, native timer controls or
    timer synchronization
  data-and-offline/typescript-time-ports: >-
    JavaScript-compatible time and formatting ports; read before porting Tokkl
    parsing, rounding or timezone behavior
  design: >-
    Tokkl's theme, palette, brand rules and shared UI components; read before
    changing how anything looks or reads to TalkBack
  release: >-
    the app ID, versioning, reproducible signed builds, CI jobs and F-Droid
    metadata; read before touching versions, signing, CI or store text
  release/reproducible-builds: >-
    project signing, optimized APK validation, reproducibility and CI; read
    before changing release signing, shrinking, build pins or release checks
  screens: >-
    sign-in and sign-out, the navigation host with its bar, rail and window
    sizes, the Timer page, the timer and entry form, Reports, Trash and the
    catalog pages; read before changing a screen, its model or navigation
  screens/catalog-pages: >-
    catalog loading, saved forms, project tasks and immediate server mutations;
    read before changing Projects, Clients, Tags or their shared coordinator
  screens/reports: >-
    report API calculations, filters, ranges and screen state; read before
    changing report requests, formatting, restoration or widget day navigation
  screens/session-and-navigation: >-
    sign-in, account lifetime, navigation and adaptive chrome; read before
    changing authentication, sign-out or page routing
  screens/session-and-navigation/navigation-state: >-
    route serialization, navigation stores, session restoration and incoming
    page taps; read before changing navigation identity, account guards or
    intent routing
  screens/timer-and-entry-editing: >-
    timer controls, shared fields, entry forms and menus; read before changing
    entry editing or local start and stop behavior
  screens/timer-history-calendar: >-
    Timer layout, cached totals, history and calendar; read before changing
    timer totals, entry browsing or saved view state
  testing: >-
    JVM coroutine tests in virtual time, and instrumented, screenshot and
    real-server tests on the plain AOSP emulators; read before writing or
    running any test
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
- `api-and-security/api-client`: typed API requests, HTTP execution, configured-server security and test fixtures; read before changing requests, transport or client wiring
- `build-and-tooling`: the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job
- `build-and-tooling/android-build`: the Android stack, Gradle configuration and static checks; read before changing modules, dependencies or build conventions
- `build-and-tooling/android-build/gradle-and-modules`: module boundaries, Koin composition, build conventions, Android compatibility and Gradle caches; read before changing dependencies, wiring or build settings
- `build-and-tooling/contributor-guidance`: commit hooks, project documentation and generated artifacts; read before committing or changing developer guidance
- `build-and-tooling/host-runtime`: the JDK, SDK, daemon isolation and host resource limits; read before building or running devices on this machine
- `data-and-offline`: account settings and time, the Room cache, the change queue, the running timer's notification and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times
- `data-and-offline/account-cache`: the Room cache ownership, capability probes and account refresh ordering; read before changing cached data, optional server features or refresh steps
- `data-and-offline/account-settings-and-time`: account time fields, timezone validation and settings refresh; read before changing time formatting, account settings or cache refresh ordering
- `data-and-offline/change-queue`: offline entry mutations, delivery, recovery and picker data; read before changing the entry queue or its sender
- `data-and-offline/timer-state-and-sync`: server timer acceptance, account lifetime, online actions and event-stream services; read before changing Pomodoro, pause, native timer controls or timer synchronization
- `data-and-offline/typescript-time-ports`: JavaScript-compatible time and formatting ports; read before porting Tokkl parsing, rounding or timezone behavior
- `design`: Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack
- `release`: the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text
- `release/reproducible-builds`: project signing, optimized APK validation, reproducibility and CI; read before changing release signing, shrinking, build pins or release checks
- `screens`: sign-in and sign-out, the navigation host with its bar, rail and window sizes, the Timer page, the timer and entry form, Reports, Trash and the catalog pages; read before changing a screen, its model or navigation
- `screens/catalog-pages`: catalog loading, saved forms, project tasks and immediate server mutations; read before changing Projects, Clients, Tags or their shared coordinator
- `screens/reports`: report API calculations, filters, ranges and screen state; read before changing report requests, formatting, restoration or widget day navigation
- `screens/session-and-navigation`: sign-in, account lifetime, navigation and adaptive chrome; read before changing authentication, sign-out or page routing
- `screens/session-and-navigation/navigation-state`: route serialization, navigation stores, session restoration and incoming page taps; read before changing navigation identity, account guards or intent routing
- `screens/timer-and-entry-editing`: timer controls, shared fields, entry forms and menus; read before changing entry editing or local start and stop behavior
- `screens/timer-history-calendar`: Timer layout, cached totals, history and calendar; read before changing timer totals, entry browsing or saved view state
- `testing`: JVM coroutine tests in virtual time, and instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test
- `testing/emulator-runtime`: AOSP emulator images, ownership, rendering and capture; read before preparing a device run or investigating emulator resource use
- `testing/instrumented-runner`: instrumented server setup, credentials, selectors and run receipts; read before invoking or modifying the device test script
- `testing/instrumented-test-authoring`: portable device tests, shared helpers and UI assertions; read before adding or changing Android regression and acceptance tests
