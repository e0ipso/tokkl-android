package com.github.e0ipso.tokkl.time

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

// Helpers that give the ports of Tokkl's TypeScript the JavaScript behaviour where Kotlin's differs.

/**
 * A regex class of the characters JavaScript's `\s` and `String.prototype.trim` treat as white
 * space: ECMAScript WhiteSpace and LineTerminator. Java's `\s` matches only ASCII white space, and
 * Kotlin's `trim()` uses yet another set.
 */
internal const val JS_WHITESPACE =
    """[\t\n\u000B\f\r \u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]"""

/** JavaScript's `/\s+/g`. */
internal val JS_WHITESPACE_RUN = Regex("$JS_WHITESPACE+")

private val JS_TRIM = Regex("""^$JS_WHITESPACE+|$JS_WHITESPACE+\z""")

/** JavaScript's `String.prototype.trim`. */
internal fun String.jsTrim(): String = replace(JS_TRIM, "")

/** JavaScript's `Math.round`, which rounds halves up. [kotlin.math.round] rounds them to even. */
internal fun jsRound(x: Double): Double {
    if (!x.isFinite()) return x
    val whole = floor(x)
    return if (x - whole >= 0.5) whole + 1 else whole
}

/**
 * JavaScript's `x.toFixed(digits)` for an [x] from 0 up to 1e21, where JavaScript switches to
 * exponent notation. It rounds the double's exact value with halves up. 1.005 is stored as
 * 1.00499999999999989..., so it gives `1.00`, where `String.format("%.2f")` gives `1.01`. The point
 * is a dot in every locale.
 */
internal fun jsToFixed(x: Double, digits: Int): String {
    require(x >= 0 && x < 1e21) { "jsToFixed takes 0 <= x < 1e21, not $x" }
    return BigDecimal(x).setScale(digits, RoundingMode.HALF_UP).toPlainString()
}
