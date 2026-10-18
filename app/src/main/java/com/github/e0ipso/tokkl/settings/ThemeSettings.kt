package com.github.e0ipso.tokkl.settings

import android.os.Build

/** Light, dark, or whatever the system uses (docs/PRD.md G1). */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Whether the app draws in dark colours, given whether the system is dark now. */
    fun isDark(systemIsDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemIsDark
            LIGHT -> false
            DARK -> true
        }
}

/**
 * The theme the user chose on the settings screen. Out of the box the app follows the system
 * and keeps Tokkl's palette. Wallpaper colours are opt-in (docs/PRD.md:77 and G2).
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

/**
 * Whether the phone has wallpaper colours to offer. Android added them in 12 (API 31), so the
 * settings screen hides the switch below that.
 */
fun dynamicColorAvailable(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S
