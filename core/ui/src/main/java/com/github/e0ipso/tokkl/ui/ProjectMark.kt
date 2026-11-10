package com.github.e0ipso.tokkl.ui

import androidx.compose.ui.graphics.Color

/**
 * A project's dot colour: its hex mixed 60% with [mix] in sRGB, as styles.css does, so it shows
 * against the card. A project without a colour, or with one that is not `#rrggbb`, takes [fallback].
 */
fun projectMark(hex: String?, mix: Color, fallback: Color): Color {
    val colour = hex?.takeIf { HEX.matches(it) }?.let { Color(0xFF000000 or it.substring(1).toLong(16)) } ?: fallback
    return Color(
        red = colour.red * 0.6f + mix.red * 0.4f,
        green = colour.green * 0.6f + mix.green * 0.4f,
        blue = colour.blue * 0.6f + mix.blue * 0.4f,
    )
}

private val HEX = Regex("#[0-9a-fA-F]{6}")
