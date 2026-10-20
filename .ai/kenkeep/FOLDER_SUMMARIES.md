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
    account settings and time, the Room cache, the change queue and the
    TypeScript ports; read before changing what the phone stores or sends, or
    how it reads and writes times
  design: >-
    Tokkl's theme, palette, brand rules and shared UI components; read before
    changing how anything looks or reads to TalkBack
  release: >-
    the app ID, versioning, reproducible signed builds, CI jobs and F-Droid
    metadata; read before touching versions, signing, CI or store text
  screens: >-
    sign-in and sign-out, the navigation host and its bars, the Timer page, the
    timer and entry form, Reports and the catalog pages; read before changing a
    screen, its model or navigation
  testing: >-
    instrumented, screenshot and real-server tests on the plain AOSP emulators;
    read before writing or running any test on a device or emulator
---
# kenkeep Folder Summaries

- `api-and-security`: the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token storage and request rules; read before adding a request or endpoint, or changing trust, credentials or privacy
- `build-and-tooling`: the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job
- `data-and-offline`: account settings and time, the Room cache, the change queue and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times
- `design`: Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack
- `release`: the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text
- `screens`: sign-in and sign-out, the navigation host and its bars, the Timer page, the timer and entry form, Reports and the catalog pages; read before changing a screen, its model or navigation
- `testing`: instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test on a device or emulator
