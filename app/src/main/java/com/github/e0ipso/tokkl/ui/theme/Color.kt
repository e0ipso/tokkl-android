package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokkl's palette, the Spectrum palette of tokkl 524deaa (TT-0298). Every value comes from the
// first :root block of ../tokkl/packages/web/src/styles.css, the source of truth for the app's
// colours (docs/PRD.md, Visual direction, Colour). styles.css writes each token once as
// light-dark(light, dark), and the comment on each value names its token there. Find a token by
// name, not by line. res/values/colors.xml and res/values-night/colors.xml repeat --bg for the
// window behind Compose and --wordmark-halo for the splash; change them with it.

/** One side, light or dark, of the styles.css colour tokens the app uses. */
private class Tokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryText: Color,
    val selected: Color,
    val onSelected: Color,
    val secondary: Color,
    val accentPink: Color,
    val accentLime: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val backdrop: Color,
    val stateRunning: Color,
    val stateSelected: Color,
    val stateBillable: Color,
    val textureGlow: Color,
    val textureSwell: Color,
    val textureStripe: Color,
    val sceneGlow: Color,
    val sceneSwell: Color,
    val sceneStripe: Color,
    val wordmarkHalo: Color,
    val chartBillable: Color,
    val chartOther: Color,
    val chartOtherWash: Color,
    val chartBand: Color,
    val projectMix: Color,
)

private val Light = Tokens(
    bg = Color(0xFFFFFDF7), // --bg
    surface = Color(0xFFFFFFFF), // --surface
    surface2 = Color(0xFFF3F1EA), // --surface-2
    text = Color(0xFF08061F), // --text
    muted = Color(0xFF4D4A5C), // --muted
    border = Color(0xFF08061F), // --border
    primary = Color(0xFFF05A00), // --primary
    onPrimary = Color(0xFF08061F), // --on-primary
    primaryText = Color(0xFFB23C00), // --primary-text
    selected = Color(0xFFFFE600), // --selected
    onSelected = Color(0xFF08061F), // --on-selected
    secondary = Color(0xFF02A4A4), // --secondary
    accentPink = Color(0xFFE6007E), // --accent-pink
    accentLime = Color(0xFF5EA556), // --accent-lime
    danger = Color(0xFFC4283D), // --danger
    onDanger = Color(0xFFFFFFFF), // --on-danger
    dangerSoft = Color(0xFFF7E1E4), // --danger-soft
    backdrop = Color(0xFF08061F), // --backdrop, rgb(8 6 31) without its 0.45 alpha
    stateRunning = Color(0xFFE6007E), // --state-running, which is --accent-pink
    stateSelected = Color(0xFFFFF8B3), // --state-selected
    stateBillable = Color(0xFF0086D1), // --state-billable
    textureGlow = Color(0xFFFDE8DB), // --texture-glow
    textureSwell = Color(0xFFE1F4F4), // --texture-swell
    textureStripe = Color(0xFFC7EBEB), // --texture-stripe
    sceneGlow = Color(0xFFFBCFB2), // --scene-glow
    sceneSwell = Color(0xFFC2E8E3), // --scene-swell
    sceneStripe = Color(0xFFFDE6D4), // --scene-stripe
    wordmarkHalo = Color.Transparent, // --wordmark-halo, transparent: only dark mode draws the halo
    chartBillable = Color(0xFF0086D1), // --chart-billable
    chartOther = Color(0xFFF05A00), // --chart-other
    chartOtherWash = Color(0xFFFBCEB3), // --chart-other-wash
    chartBand = Color(0xFFF9F8F5), // --chart-band
    projectMix = Color(0xFF08061F), // --project-mix
)

private val Dark = Tokens(
    bg = Color(0xFF08061F), // --bg
    surface = Color(0xFF14112E), // --surface
    surface2 = Color(0xFF221E40), // --surface-2
    text = Color(0xFFFFFFFF), // --text
    muted = Color(0xFFB9B5D6), // --muted
    border = Color(0xFF8C86C0), // --border
    primary = Color(0xFFFF6500), // --primary
    onPrimary = Color(0xFF08061F), // --on-primary
    primaryText = Color(0xFFFF8A3D), // --primary-text
    selected = Color(0xFFFFE600), // --selected
    onSelected = Color(0xFF08061F), // --on-selected
    secondary = Color(0xFF00DFD5), // --secondary
    accentPink = Color(0xFFFF008C), // --accent-pink
    accentLime = Color(0xFF80E36B), // --accent-lime
    danger = Color(0xFFFF5464), // --danger
    onDanger = Color(0xFF08061F), // --on-danger
    dangerSoft = Color(0xFF431E39), // --danger-soft
    backdrop = Color(0xFF08061F), // --backdrop, rgb(8 6 31) without its 0.7 alpha
    stateRunning = Color(0xFFFF008C), // --state-running, which is --accent-pink
    stateSelected = Color(0xFF37166D), // --state-selected
    stateBillable = Color(0xFF00B9FA), // --state-billable
    textureGlow = Color(0xFF351D28), // --texture-glow
    textureSwell = Color(0xFF122A42), // --texture-swell
    textureStripe = Color(0xFF103E53), // --texture-stripe
    sceneGlow = Color(0xFF4D2116), // --scene-glow
    sceneSwell = Color(0xFF063A4B), // --scene-swell
    sceneStripe = Color(0xFF2B131B), // --scene-stripe
    wordmarkHalo = Color(0xFFB9B5D6), // --wordmark-halo
    chartBillable = Color(0xFF00B9FA), // --chart-billable
    chartOther = Color(0xFFFF9500), // --chart-other
    chartOtherWash = Color(0xFF5B3920), // --chart-other-wash
    chartBand = Color(0xFF1B1837), // --chart-band
    projectMix = Color(0xFFFFFFFF), // --project-mix
)

/**
 * The section hues of styles.css, the wordmark's and the same in light and dark, with the colour
 * of text and icons on each. TKA-0186 gives each top-level tab one of them; until then no screen
 * draws them, and ThemeContrastTest keeps their pairs checked.
 */
internal object TokklHues {
    val orange = Color(0xFFF05A00) // --hue-orange
    val sky = Color(0xFF00B9FA) // --hue-sky
    val violet = Color(0xFF8722FF) // --hue-violet
    val yellow = Color(0xFFFFE600) // --hue-yellow
    val pink = Color(0xFFFF008C) // --hue-pink

    /** `--on-hue`, the ink, on every hue but violet. */
    val onHue = Color(0xFF08061F)

    /** `--on-hue-violet`, white, on violet, where the ink is only 3.50:1. */
    val onHueViolet = Color(0xFFFFFFFF)
}

/**
 * Maps the Material 3 roles onto the tokens of one scheme. A role with no token of its own takes
 * the nearest one, and its comment says which.
 *
 * styles.css splits primary in two: --primary is a fill, with --on-primary content, and text in
 * the primary colour reads --primary-text, because the light --primary is 3.40:1 on surface.
 * Material 3 draws text in its primary role (text buttons, tabs, focused field labels), so the
 * role takes --primary-text, and the fills that styles.css paints in --primary take
 * [TokklExtraColors.primaryFill].
 *
 * [other] is the opposite scheme, for inversePrimary. [surfaceBright] and [surfaceDim] are the
 * lightest and darkest surface tokens, which differ between light and dark.
 */
private fun colorScheme(t: Tokens, other: Tokens, surfaceBright: Color, surfaceDim: Color) =
    ColorScheme(
        primary = t.primaryText,
        // styles.css has no colour for content on --primary-text. --on-danger is white in light
        // and the ink in dark, the one that reads on it in each scheme.
        onPrimary = t.onDanger,
        // --state-selected is the web's tint behind selected rows and today's column, with --text
        // on it.
        primaryContainer = t.stateSelected,
        onPrimaryContainer = t.text,
        // The primary text colour of the opposite scheme, as Material 3 defines it.
        inversePrimary = other.primaryText,
        // A mark only, never text, and only on bg and surface: the light --secondary is 3.01:1 on bg
        // and under 3:1 on surface-2.
        secondary = t.secondary,
        // styles.css has no --on-secondary. The report header band puts --on-primary, the ink, on
        // --secondary.
        onSecondary = t.onPrimary,
        // --selected is the web's fill for the active segmented button, with --on-selected on it.
        // Material 3's segmented buttons take this role for their active segment.
        secondaryContainer = t.selected,
        onSecondaryContainer = t.onSelected,
        // The pink accent. It is also the running state, so it never marks a state alone.
        tertiary = t.accentPink,
        // styles.css has no colour for text on the pink. The ink is 4.41:1 on the light pink, so
        // this takes --on-danger, white in light and the ink in dark.
        onTertiary = t.onDanger,
        tertiaryContainer = t.stateSelected,
        onTertiaryContainer = t.text,
        background = t.bg,
        onBackground = t.text,
        surface = t.surface,
        onSurface = t.text,
        surfaceVariant = t.surface2,
        onSurfaceVariant = t.muted,
        // Surface itself, so tonal elevation adds no tint and every surface stays a tested colour.
        surfaceTint = t.surface,
        inverseSurface = t.text,
        inverseOnSurface = t.surface,
        error = t.danger,
        onError = t.onDanger,
        errorContainer = t.dangerSoft,
        onErrorContainer = t.danger,
        outline = t.border,
        outlineVariant = t.border,
        scrim = t.backdrop,
        surfaceBright = surfaceBright,
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.surface,
        surfaceContainerHighest = t.surface2,
        surfaceContainerLow = t.surface,
        surfaceContainerLowest = t.surface,
        surfaceDim = surfaceDim,
        // Fixed roles stay the same in light and dark, so both schemes take the light
        // --state-selected with the light --text on it.
        primaryFixed = Light.stateSelected,
        primaryFixedDim = Light.stateSelected,
        onPrimaryFixed = Light.text,
        onPrimaryFixedVariant = Light.text,
        secondaryFixed = Light.stateSelected,
        secondaryFixedDim = Light.stateSelected,
        onSecondaryFixed = Light.text,
        onSecondaryFixedVariant = Light.text,
        tertiaryFixed = Light.stateSelected,
        tertiaryFixedDim = Light.stateSelected,
        onTertiaryFixed = Light.text,
        onTertiaryFixedVariant = Light.text,
    )

internal val TokklLightColorScheme =
    colorScheme(Light, other = Dark, surfaceBright = Light.surface, surfaceDim = Light.surface2)

internal val TokklDarkColorScheme =
    colorScheme(Dark, other = Light, surfaceBright = Dark.surface2, surfaceDim = Dark.bg)

/**
 * Tokkl colours with no Material 3 role. Screens show [running] and [billable] together with an
 * icon or a label, never as the only sign of the state.
 *
 * The texture and scene colours are the stops of gradients behind the top bar strip and the
 * sign-in scene. Draw them as gradients, never as images, and never behind an input or data.
 */
@Immutable
class TokklExtraColors(
    /**
     * `--primary`, the fill of a primary button and of the week strip's today badge. The
     * Material primary role is `--primary-text`; see colorScheme in this file.
     */
    val primaryFill: Color,
    /** `--on-primary`, the ink, for text and icons on [primaryFill]. */
    val onPrimaryFill: Color,
    /** A running time entry. Readable as text on surface, and as a mark on any surface. */
    val running: Color,
    /** A billable time entry. A mark only, never text. */
    val billable: Color,
    /**
     * The lime accent. A mark only, never text, and only on surface: in light it is 3.00:1 there
     * and under 3:1 on bg and surface-2.
     */
    val accentLime: Color,
    /** `--texture-glow`, the sunset glow over surface behind the top bar strip. */
    val textureGlow: Color,
    /** `--texture-swell`, the striped swell's base over surface behind the top bar strip. */
    val textureSwell: Color,
    /** `--texture-stripe`, the swell's stripes over surface behind the top bar strip. */
    val textureStripe: Color,
    /** `--scene-glow`, the sunset glow over bg behind the sign-in scene and the splash. */
    val sceneGlow: Color,
    /** `--scene-swell`, the swell's base over bg behind the sign-in scene and the splash. */
    val sceneSwell: Color,
    /** `--scene-stripe`, the stripes over bg behind the sign-in scene and the splash. */
    val sceneStripe: Color,
    /** `--wordmark-halo`, the 1dp outline around the wordmark. Transparent in light. */
    val wordmarkHalo: Color,
    /** `--chart-billable`, a report chart's billable time, drawn solid. A mark only, never text. */
    val chartBillable: Color,
    /** `--chart-other`, the stripes of a report chart's non-billable time. A mark only, never text. */
    val chartOther: Color,
    /** `--chart-other-wash`, the wash behind [chartOther]'s stripes. */
    val chartOtherWash: Color,
    /**
     * `--chart-band`, the faint track behind a report chart's bars. [chartBillable] and
     * [chartOther] reach 3:1 on it in light and dark, which they do not on surface-2 in light.
     */
    val chartBand: Color,
    /**
     * `--project-mix`, what a project's colour is mixed with, 40% of it, for its dot, so every
     * colour of Tokkl's project palette reaches 3:1 as a mark. Black in light, white in dark.
     */
    val projectMix: Color,
)

internal val TokklLightExtraColors = TokklExtraColors(
    primaryFill = Light.primary,
    onPrimaryFill = Light.onPrimary,
    running = Light.stateRunning,
    billable = Light.stateBillable,
    accentLime = Light.accentLime,
    textureGlow = Light.textureGlow,
    textureSwell = Light.textureSwell,
    textureStripe = Light.textureStripe,
    sceneGlow = Light.sceneGlow,
    sceneSwell = Light.sceneSwell,
    sceneStripe = Light.sceneStripe,
    wordmarkHalo = Light.wordmarkHalo,
    chartBillable = Light.chartBillable,
    chartOther = Light.chartOther,
    chartOtherWash = Light.chartOtherWash,
    chartBand = Light.chartBand,
    projectMix = Light.projectMix,
)

internal val TokklDarkExtraColors = TokklExtraColors(
    primaryFill = Dark.primary,
    onPrimaryFill = Dark.onPrimary,
    running = Dark.stateRunning,
    billable = Dark.stateBillable,
    accentLime = Dark.accentLime,
    textureGlow = Dark.textureGlow,
    textureSwell = Dark.textureSwell,
    textureStripe = Dark.textureStripe,
    sceneGlow = Dark.sceneGlow,
    sceneSwell = Dark.sceneSwell,
    sceneStripe = Dark.sceneStripe,
    wordmarkHalo = Dark.wordmarkHalo,
    chartBillable = Dark.chartBillable,
    chartOther = Dark.chartOther,
    chartOtherWash = Dark.chartOtherWash,
    chartBand = Dark.chartBand,
    projectMix = Dark.projectMix,
)

/**
 * These colours with [fill] and [onFill] as the primary fill, for a colour scheme from the
 * wallpaper, whose primary role is a fill and a text colour at once.
 */
internal fun TokklExtraColors.withPrimaryFill(fill: Color, onFill: Color) = TokklExtraColors(
    primaryFill = fill,
    onPrimaryFill = onFill,
    running = running,
    billable = billable,
    accentLime = accentLime,
    textureGlow = textureGlow,
    textureSwell = textureSwell,
    textureStripe = textureStripe,
    sceneGlow = sceneGlow,
    sceneSwell = sceneSwell,
    sceneStripe = sceneStripe,
    wordmarkHalo = wordmarkHalo,
    chartBillable = chartBillable,
    chartOther = chartOther,
    chartOtherWash = chartOtherWash,
    chartBand = chartBand,
    projectMix = projectMix,
)
