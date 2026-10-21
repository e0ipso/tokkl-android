package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A section's hue, [fill], with the colour of text and icons on it, [onFill]. The active tab is
 * filled with its page's hue, and the page's day stickers take the same hue, as `data-section`
 * maps `--section-hue` and `--on-section-hue` in Tokkl's styles.css (tokkl d012228, TT-0299).
 * The hues are the same in light and dark, and stay Tokkl's when the wallpaper sets the colours.
 */
@Immutable
class SectionHue(val fill: Color, val onFill: Color)

/**
 * The hue of each section of the app, as NAV in Tokkl's App.tsx names them. The pages on the More
 * sheet, Clients, Tags and Trash, take More's pink, so the page, its sheet item and the More tab
 * that is marked while it shows agree.
 */
object SectionHues {
    val timer = SectionHue(TokklHues.orange, TokklHues.onHue)
    val reports = SectionHue(TokklHues.sky, TokklHues.onHue)
    val projects = SectionHue(TokklHues.violet, TokklHues.onHueViolet)
    val settings = SectionHue(TokklHues.yellow, TokklHues.onHue)
    val more = SectionHue(TokklHues.pink, TokklHues.onHue)

    /** Every hue, for the checks that must hold on each. */
    val all: List<SectionHue> = listOf(timer, reports, projects, settings, more)
}

/**
 * The hue of the section that shows. SignedInFrame provides the current tab's; outside it, as on
 * the web app, a page takes the Timer's.
 */
val LocalSectionHue = staticCompositionLocalOf { SectionHues.timer }
