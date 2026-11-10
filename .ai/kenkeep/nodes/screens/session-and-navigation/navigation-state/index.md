# kenkeep Index: screens / session-and-navigation / navigation-state

↑ Parent: [session-and-navigation](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
_None yet._

## Components (what exists)
- Open [**External navigation requests and intent consumption**](map-external-navigation-requests-and-intent-consumption.md) to learn about: Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death. #android #navigation #ui
- Open [**Navigation route serialization and feature registration**](map-navigation-route-serialization-and-feature-registration.md) to learn about: Feature serializers preserve route identities and numeric IDs, while navigation contracts isolate feature screens and entries. #android #ui #navigation
- Open [**Navigation stores and restored session guard**](map-navigation-stores-and-restored-session-guard.md) to learn about: Route and session ViewModel stores survive configuration changes, restore saved state, and reject expired sessions or previous-account drafts. #android #navigation #credentials

## By topic

### #android
- Open [**Wait on the app's own handlers, never on a latch in production code**](../../../testing/instrumented-test-authoring/practice-wait-on-the-apps-handlers-not-production-latches.md) — Device tests wait for receiver and service work by wrapping the handler in the Koin graph, or on an effect the app already shows. Production has no test latches or test-only branches.
- Open [**Let the runner own emulator boot and settings**](../../../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Choose plain AOSP images and compatible renderers**](../../../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #navigation
- Open [**Signed-in navigation chrome and adaptive windows**](../map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
- Open [**Navigation route serialization and feature registration**](map-navigation-route-serialization-and-feature-registration.md) — Feature serializers preserve route identities and numeric IDs, while navigation contracts isolate feature screens and entries.
### #ui
- Open [**Timer page layout and scroll-away header**](../../timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Signed-in navigation chrome and adaptive windows**](../map-signed-in-navigation-chrome-and-adaptive-windows.md) — Bar, rail, back-stack and width rules control signed-in page layout.
- Open [**External navigation requests and intent consumption**](map-external-navigation-requests-and-intent-consumption.md) — Features decode serializable taps, consume intent extras once, and retain pending requests through configuration changes and process death.
### #credentials
- Open [**Sign-out ordering and account cleanup**](../map-sign-out-ordering-and-account-cleanup.md) — Warn about unsent work and retire account tasks before clearing local session data.
- Open [**Session stores and pre-frame preferences**](../map-session-stores-and-pre-frame-preferences.md) — Private session stores separate credentials, account settings, trust and device preferences; their holders load at process start and write through suspend calls.
- Open [**Sign-in request and credential admission**](../map-sign-in-request-and-credential-admission.md) — Validate configured-server credentials and required account settings before storing a session.