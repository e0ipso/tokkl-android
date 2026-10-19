package com.github.e0ipso.tokkl.reports

import androidx.compose.ui.graphics.Color
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Percentages and money as the web app's format.ts writes them in en-US, and the project dot's
 * colour mix. No percentage here sits at a half of the last digit, where TKA-0148 changes the
 * rounding.
 */
class ReportFormatTest {

    @Test
    fun percentHasOneDecimal() {
        assertEquals("33.3", sharePercent(1, 3))
        assertEquals("66.7", sharePercent(2, 3))
        assertEquals("12.5", sharePercent(1, 8))
        assertEquals("100.0", sharePercent(7, 7))
        // (29 / 200) * 100 is 14.499999999999998 as a double.
        assertEquals("14.5", sharePercent(29, 200))
    }

    @Test
    fun percentOfNothingIsZero() {
        assertEquals("0.0", sharePercent(5, 0))
        assertEquals("0.0", sharePercent(0, 3600))
    }

    @Test
    fun moneyUsesTheCurrencysOwnDecimals() {
        assertEquals("€123.45", formatMoney(12345, "EUR", Locale.US))
        assertEquals("$1,234.50", formatMoney(123450, "USD", Locale.US))
        assertEquals("¥123", formatMoney(12345, "JPY", Locale.US))
        // Intl rounds a half away from zero.
        assertEquals("¥124", formatMoney(12350, "JPY", Locale.US))
    }

    @Test
    fun anUnknownCurrencyCodeFollowsTheAmount() {
        assertEquals("12.50 XYZ1", formatMoney(1250, "XYZ1", Locale.US))
    }

    @Test
    fun projectMarkMixesTheProjectColourSixtyPercentWithTheMix() {
        val mark = projectMark("#ffffff", Color.Black, Color.Red)
        assertEquals(0.6f, mark.red, 0.005f)
        assertEquals(0.6f, mark.green, 0.005f)
        assertEquals(0.6f, mark.blue, 0.005f)
    }

    @Test
    fun aProjectWithoutAColourMixesTheFallback() {
        assertEquals(0.4f, projectMark(null, Color.White, Color.Black).green, 0.005f)
        assertEquals(0.4f, projectMark("blue", Color.White, Color.Black).green, 0.005f)
    }
}
