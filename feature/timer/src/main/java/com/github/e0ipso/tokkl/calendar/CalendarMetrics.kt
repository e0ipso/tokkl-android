package com.github.e0ipso.tokkl.calendar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** Each hour's label in the account's clock format: `07:00`, or `7:00 AM`. */
internal fun hourLabels(settings: AccountSettings): List<String> =
    (0 until HOURS).map { hour -> settings.formatTime(Instant.ofEpochSecond(hour * SECONDS_PER_HOUR), ZoneOffset.UTC) }

/** [colour] at [fraction] over [base], as CSS's `color-mix(in srgb, colour fraction, base)`. */
internal fun mix(colour: Color, base: Color, fraction: Float): Color = Color(
    red = colour.red * fraction + base.red * (1 - fraction),
    green = colour.green * fraction + base.green * (1 - fraction),
    blue = colour.blue * fraction + base.blue * (1 - fraction),
)

/** A project's `#rrggbb` colour, or null when it has none the app can read. */
internal fun projectColour(hex: String?): Color? =
    hex?.takeIf { HEX.matches(it) }?.let { Color(OPAQUE or it.substring(1).toLong(HEX_RADIX)) }

private val HEX = Regex("#[0-9a-fA-F]{6}")

/** The alpha of an opaque ARGB colour. */
private const val OPAQUE = 0xFF000000

private const val HEX_RADIX = 16

/**
 * The days a finished swipe of [dx] by [dy] pixels moves: 1 after a swipe to the left and -1
 * after one to the right, as classifySwipe in the web app's lib/calendarDay.ts. A swipe goes at
 * least [minDistance] pixels sideways and [SWIPE_RATIO] times further sideways than up or down,
 * so scrolling the grid never changes the week. Anything else gives null.
 */
internal fun swipeSteps(dx: Float, dy: Float, minDistance: Float): Long? =
    if (abs(dx) >= minDistance && abs(dx) >= SWIPE_RATIO * abs(dy)) (if (dx < 0) 1L else -1L) else null

/**
 * Where a sideways strip of days [dayWidth] pixels wide, after [inset] pixels at each end, scrolls
 * so the day at [index] shows whole, from [scrolled] pixels in a viewport [viewport] pixels wide, or null
 * when it already shows. The day's edges include the inset on either side.
 */
internal fun stripScrollToShow(index: Int, dayWidth: Int, inset: Int, scrolled: Int, viewport: Int): Int? {
    val from = dayWidth * index
    val to = from + dayWidth + 2 * inset
    return when {
        from < scrolled -> from
        to > scrolled + viewport -> to - viewport
        else -> null
    }
}

/** The height of an hour row: HOUR_HEIGHT in the web app's WeekCalendar.tsx, 48px, which is --space-8. */
internal val HourHeight: Dp = TokklSpacing.space8

internal const val HOURS = 24

/** The hour the grid shows at the top when it first shows, as in the web app. */
internal const val FIRST_HOUR = 7

/** A block this many minutes tall or more shows two lines, as in the web app. */
internal const val TWO_LINE_MINUTES = 45.0

/** How much of a block is its project's colour over surface, 18% in the web app's styles.css. */
internal const val BLOCK_TINT = 0.18f

/** How much of today's column is --state-selected over surface: --calendar-today, 35%. */
internal const val TODAY_TINT = 0.35f

/** The tallest the All day row grows before it scrolls: two chips of a touch target's height. */
internal val AllDayMaxHeight: Dp = 112.dp

/** SWIPE_MIN_PX in the web app's lib/calendarDay.ts. */
internal val SwipeMinDistance: Dp = 50.dp

/** SWIPE_RATIO in the web app's lib/calendarDay.ts. */
internal const val SWIPE_RATIO = 1.5f

/** The gap on each side of a block, 2px in WeekCalendar.tsx: half of --space-1. */
internal val BlockGap: Dp = TokklSpacing.space1 / 2

/** How far into a block from its top or bottom a press takes that edge for a resize, a 15-minute row. */
internal val EdgeZone: Dp = TokklSpacing.space3

/** The bar in the project's colour on a block's start side, --space-1. The web app's is 5px. */
internal val BlockBar: Dp = TokklSpacing.space1

/** `--radius-pill`. */
internal val PillShape = RoundedCornerShape(percent = 50)

/** How far a day's column travels as it slides in: Material's shared axis transition moves 30dp. */
internal val SlideDistance: Dp = 30.dp

/** How long a day's column takes to slide in: Material's shared axis transition takes 300ms. */
internal const val SLIDE_MS = 300

/**
 * The smallest a line of a strip day shrinks to. Low enough that a short total such as
 * "45 min" stays whole in a day of the strip at the largest font scale and display size.
 */
internal val StripMinFontSize = 4.sp

internal const val MINUTE_MS = 60_000L

private const val SECONDS_PER_HOUR = 3600L
