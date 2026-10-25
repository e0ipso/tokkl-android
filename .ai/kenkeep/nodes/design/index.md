# kenkeep Index: design

↑ Parent: [kenkeep](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) to learn about: Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources. #ui #accessibility #i18n
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) to learn about: Owner rule of 2026-10-19, now in the PRD: no wordmark in page headers or top bars, logo only as the launcher icon. Pages show their title. #ui #brand #design

## Components (what exists)
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) to learn about: ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup. #ui #theme #accessibility #android
- Open [**Tokkl colour tokens and Anybody font in ../tokkl**](map-tokkl-colour-tokens-and-font.md) to learn about: The colour tokens in ../tokkl/packages/web/src/styles.css are the palette's source of truth. Tokkl ships Anybody under the SIL OFL 1.1. #ui #tokkl
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) to learn about: Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar. #ui #android #accessibility #compose

## By topic

### #ui
- Open [**Timer page layout and scroll-away header**](../screens/timer-history-calendar/map-timer-page-layout-and-scroll-away-header.md) — Bounded Timer content and the shared scroll-away header adapt to short and wide windows.
- Open [**Timer List and Calendar preference sync**](../screens/timer-history-calendar/map-timer-list-and-calendar-preference-sync.md) — View selection persists locally and writes the supported server preference without blocking navigation.
- Open [**Tokkl favorites storage and capability**](../screens/timer-and-entry-editing/map-tokkl-favorites-storage-and-capability.md) — Favorite starts share cached fields while preference writes use supported server capabilities.
### #accessibility
- Open [**Shared timer and entry fields**](../screens/timer-and-entry-editing/map-shared-timer-and-entry-fields.md) — Responsive field rows, picker dialogs and accessibility are shared between the timer and entry form.
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
### #android
- Open [**Let the runner own emulator boot and settings**](../testing/emulator-runtime/practice-let-the-runner-own-emulator-boot-and-settings.md) — The runner owns adb startup, emulator readiness and temporary device settings.
- Open [**Account for emulator recording and screenshot quirks**](../testing/emulator-runtime/practice-account-for-emulator-recording-and-screenshot-quirks.md) — Hold the final video frame and distinguish framebuffer captures from launch splash failures.
- Open [**Choose plain AOSP images and compatible renderers**](../testing/emulator-runtime/practice-choose-plain-aosp-images-and-compatible-renderers.md) — API 36 references and API 31 compatibility use launcher-capable AOSP images and bounded renderers.
### #brand
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) — Owner rule of 2026-10-19, now in the PRD: no wordmark in page headers or top bars, logo only as the launcher icon. Pages show their title.
### #compose
- Open [**UI components: TopBar, ErrorBanner, Scene, ScrollAwayColumn, and Compose layout gotchas**](map-ui-components-and-compose-layout-gotchas.md) — Shared ui/ pieces beyond the theme, plus fixes for a field under the keyboard, a dialog that never settles and the More sheet's navigation bar.
### #design
- Open [**Show the wordmark only on sign-in and the launch splash, never in a header**](practice-wordmark-only-on-sign-in-and-splash.md) — Owner rule of 2026-10-19, now in the PRD: no wordmark in page headers or top bars, logo only as the launcher icon. Pages show their title.
### #i18n
- Open [**Use Tokkl's palette, WCAG AA contrast and English string resources**](practice-tokkl-palette-wcag-english-strings.md) — Always Tokkl's palette, with wallpaper dynamic colour only as an opt-in. WCAG AA contrast, colour never the only signal, text in resources.
### #theme
- Open [**Design foundation: spacing, shapes, type roles, option rows, brand and insets**](map-design-foundation-spacing-shapes-type-brand.md) — ui/theme and ui/ hold Tokkl's spacing, screen margin, corners, type roles, option rows and Brand, plus the edge-to-edge and keyboard setup.
### #tokkl
- Open [**Honor account clock formatting in time ports**](../data-and-offline/typescript-time-ports/practice-honor-account-clock-formatting-in-time-ports.md) — FormatTime matches the account 12-hour or 24-hour preference and server value semantics.
- Open [**Match JavaScript decimal and percentage formatting**](../data-and-offline/typescript-time-ports/practice-match-javascript-decimal-and-percentage-formatting.md) — Binary toFixed and shortest-decimal Intl formatting require separate helpers.
- Open [**Preserve JavaScript parsing and numeric boundaries**](../data-and-offline/typescript-time-ports/practice-preserve-javascript-parsing-and-numeric-boundaries.md) — Match JavaScript whitespace and rounding while representing invalid numeric results safely.