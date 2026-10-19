# kenkeep Index: design

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) to learn about: Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources. #ui #accessibility #i18n
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) to learn about: Owner rule of 2026-10-19: no wordmark in page headers or top bars, logo only as the launcher icon. The Timer page breaks it until TKA-0182. #ui #brand #design

## Components (what exists)
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) to learn about: ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup. #ui #theme #accessibility #android
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](map-tokkl-colour-tokens-and-font.md) to learn about: The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1. #ui #tokkl
- Open [**UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) to learn about: Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard and a dialog that lays itself out on every frame. #ui #android #accessibility #compose

## By topic

### #ui
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard and a dialog that lays itself out on every frame.
- Open [**Match Tokkl web app behavior in the account timezone**](../data-and-offline/practice-match-web-app-behavior-account-timezone.md) — Use the web app's time parsing, favorites, grouping and undo, in the account timezone read from GET /api/v9/me.
### #accessibility
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard and a dialog that lays itself out on every frame.
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #android
- Open [**Record screenshot references on tokkl-api36, in the commit that changes the look**](../testing/practice-screenshot-references-on-tokkl-api36.md) — Four classes compare every screen with PNGs in androidTest/assets/screenshots. Only tokkl-api36 renders them, and captures carry no ripple.
- Open [**Write instrumented tests that hold from API 26 to 36, with the shared helpers**](../testing/practice-instrumented-tests-on-every-api-level.md) — Skip with @SdkSuppress, never an assumption. Rules set device state. Reuse the relays, AirplaneMode, BackgroundTrip, TestAccounts.
- Open [**Run instrumented tests only through scripts/instrumented-tests.sh**](../testing/practice-run-instrumented-tests-only-through-the-script.md) — It starts a pinned Tokkl container, seeds an account, passes test filters as a file and splits runs below Android 12 into checked batches.
### #brand
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) — Owner rule of 2026-10-19: no wordmark in page headers or top bars, logo only as the launcher icon. The Timer page breaks it until TKA-0182.
### #compose
- Open [**UI components: ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard and a dialog that lays itself out on every frame.
### #design
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) — Owner rule of 2026-10-19: no wordmark in page headers or top bars, logo only as the launcher icon. The Timer page breaks it until TKA-0182.
### #i18n
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #theme
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
### #tokkl
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](map-tokkl-colour-tokens-and-font.md) — The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1.
- Open [**Port Tokkl's TypeScript with JavaScript semantics and verbatim test cases**](../data-and-offline/practice-port-tokkl-typescript-with-javascript-semantics.md) — The Kotlin time ports copy JavaScript's whitespace, rounding and replace rules, return null for NaN, and reuse the .test.ts cases verbatim.