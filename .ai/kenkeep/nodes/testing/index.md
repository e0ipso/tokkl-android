# kenkeep Index: testing

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) to learn about: It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests. #testing #android #emulator #docker
- Open [**Test on plain AOSP emulators, headless, and stop them afterwards**](practice-emulator-plain-aosp-image-headless.md) to learn about: tokkl-api36 and tokkl-api31 use plain AOSP images, since ATD drops SystemUI. Only the script boots them, headless and capped. #testing #android #emulator #energy #memory
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) to learn about: Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one. #testing #ci #performance
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) to learn about: Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple. #testing #android #emulator #ui
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](practice-instrumented-tests-on-every-api-level.md) to learn about: Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts. #testing #android #emulator

## Components (what exists)
_None yet._

## By topic

### #testing
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests.
### #emulator
- Open [**Write instrumented tests that hold on API 31 and 36, with the shared helpers**](practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse LoopbackRelay, openBarPage, SessionRule, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests.
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
### #ci
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
- Open [**Keep release builds reproducible and signed with the project key**](../release/practice-reproducible-builds-project-key.md) — Reproducible builds let F-Droid publish the APK signed with the project key. The key lives in Actions secrets and CI pins actions by SHA.
### #docker
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container behind a TLS proxy, seeds an account, passes test filters as a file and fails a run Gradle passed without tests.
### #energy
- Open [**Test on plain AOSP emulators, headless, and stop them afterwards**](practice-emulator-plain-aosp-image-headless.md) — tokkl-api36 and tokkl-api31 use plain AOSP images, since ATD drops SystemUI. Only the script boots them, headless and capped.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](../build-and-tooling/map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Gate drain sittings on free memory, not on a sitting count**](../practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start through the memory gate; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.
- Open [**Test on plain AOSP emulators, headless, and stop them afterwards**](practice-emulator-plain-aosp-image-headless.md) — tokkl-api36 and tokkl-api31 use plain AOSP images, since ATD drops SystemUI. Only the script boots them, headless and capped.
### #performance
- Open [**Test against a real Tokkl server and catch speed regressions in CI**](practice-test-real-tokkl-server-speed-regressions.md) — Instrumented tests use the real ghcr.io/e0ipso/tokkl image, never a fake. Macrobenchmark fails a build slower than the previous one.
### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](../design/map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](../design/map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.