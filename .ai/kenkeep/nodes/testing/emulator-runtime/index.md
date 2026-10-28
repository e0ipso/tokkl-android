# kenkeep Index: testing / emulator-runtime

↑ Parent: [testing](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Let the runner own emulator boot and settings**](practice-let-the-runner-own-emulator-boot-and-settings.md) to learn about: The runner owns adb startup, emulator readiness and temporary device settings. #testing #android #emulator
- Open [**Choose plain AOSP images and compatible renderers**](practice-choose-plain-aosp-images-and-compatible-renderers.md) to learn about: API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers. #testing #android #emulator
- Open [**Account for emulator recording and screenshot quirks**](practice-account-for-emulator-recording-and-screenshot-quirks.md) to learn about: Hold the final video frame and distinguish framebuffer captures from launch splash failures. #testing #android #emulator
- Open [**Clean up only owned emulator resources**](practice-clean-up-only-owned-emulator-resources.md) to learn about: Durable device cleanup and exact adb process matching prevent leaks and unrelated shutdowns. #testing #emulator #energy
- Open [**Respect emulator slots and resource ownership**](practice-respect-emulator-slots-and-resource-ownership.md) to learn about: Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime work. #testing #emulator #memory

## Components (what exists)
_None yet._

## By topic

### #emulator
- Open [**Let the runner own emulator boot and settings**](practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #testing
- Open [**Let the runner own emulator boot and settings**](practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #android
- Open [**Let the runner own emulator boot and settings**](practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
- Open [**Account for emulator recording and screenshot quirks**](practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
### #energy
- Open [**Clean up only owned emulator resources**](practice-clean-up-only-owned-emulator-resources.md) — Durable device cleanup and exact adb process matching prevent leaks and unrelated shutdowns.
### #memory
- Open [**An OOM kill inside an agent session's scope ends the whole session**](../../build-and-tooling/map-oom-kill-ends-agent-session.md) — Agent sessions run in t3code-claude-*.scope with OOMPolicy=stop. Run Gradle and the test script in a scope of their own with choom 1000.
- Open [**Respect emulator slots and resource ownership**](practice-respect-emulator-slots-and-resource-ownership.md) — Scopes, global locks, slot locks and read-only AVDs isolate concurrent runtime work.
- Open [**Run drain Gradle and device jobs in the OOM-safe scope, not behind a sitting cap**](../../build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md) — Drain Gradle and device runs start directly in the OOM-safe scope, one heavy job at a time; no fixed cap on sittings. T3 checkpoint refs are not reviewer writes.