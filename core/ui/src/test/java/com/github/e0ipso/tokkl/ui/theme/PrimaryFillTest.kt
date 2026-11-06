package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * With wallpaper colours on, [TokklTheme] passes the wallpaper scheme's primary and onPrimary to
 * [withPrimaryFill]. The primary fill follows them, and every other colour stays Tokkl's.
 */
class PrimaryFillTest {

    @Test
    fun lightTakesTheWallpaperPrimaryAndKeepsTokklsOtherColours() =
        assertOnlyThePrimaryFillChanges(TokklLightExtraColors, WALLPAPER_LIGHT_PRIMARY, WALLPAPER_LIGHT_ON_PRIMARY)

    @Test
    fun darkTakesTheWallpaperPrimaryAndKeepsTokklsOtherColours() =
        assertOnlyThePrimaryFillChanges(TokklDarkExtraColors, WALLPAPER_DARK_PRIMARY, WALLPAPER_DARK_ON_PRIMARY)

    private fun assertOnlyThePrimaryFillChanges(tokkl: TokklExtraColors, fill: Color, onFill: Color) {
        assertNotEquals("the wallpaper's primary is not Tokkl's", tokkl.primaryFill, fill)
        assertNotEquals("the wallpaper's onPrimary is not Tokkl's", tokkl.onPrimaryFill, onFill)

        val dynamic = tokkl.withPrimaryFill(fill, onFill)

        assertEquals(fill, dynamic.primaryFill)
        assertEquals(onFill, dynamic.onPrimaryFill)
        val others = colours(tokkl).keys - setOf("primaryFill", "onPrimaryFill")
        // The constructor takes each colour as a packed Long too, beside Kotlin's marker for a
        // constructor with value class parameters.
        val parameters = TokklExtraColors::class.java.declaredConstructors.first().parameterTypes
            .count { it == Long::class.javaPrimitiveType }
        assertEquals("every colour of TokklExtraColors is read: $others", parameters - 2, others.size)
        for (name in others) {
            assertEquals("$name stays Tokkl's", colours(tokkl).getValue(name), colours(dynamic).getValue(name))
        }
    }

    /**
     * Every colour of [colors] by property name, read through its getters, so a colour added to
     * [TokklExtraColors] later is checked too. Compose's Color is a value class, so each getter
     * returns the packed Long under a name such as `getRunning-0d7_KjU`.
     */
    private fun colours(colors: TokklExtraColors): Map<String, Long> =
        TokklExtraColors::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.returnType == Long::class.javaPrimitiveType }
            .associate { method ->
                val name = method.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercase() }
                name to method.invoke(colors) as Long
            }

    private companion object {
        // Material 3's baseline primary and onPrimary, as a wallpaper scheme would give them.
        val WALLPAPER_LIGHT_PRIMARY = Color(0xFF6750A4)
        val WALLPAPER_LIGHT_ON_PRIMARY = Color(0xFFFFFFFF)
        val WALLPAPER_DARK_PRIMARY = Color(0xFFD0BCFF)
        val WALLPAPER_DARK_ON_PRIMARY = Color(0xFF381E72)
    }
}
