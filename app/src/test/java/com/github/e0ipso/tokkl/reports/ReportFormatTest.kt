package com.github.e0ipso.tokkl.reports

import androidx.compose.ui.graphics.Color
import com.github.e0ipso.tokkl.time.jsToFixed
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Percentages and money as the web app's format.ts writes them in en-US, and the project dot's colour mix. */
class ReportFormatTest {

    @Test
    fun percentHasOneDecimal() {
        assertEquals("33.3", sharePercent(1, 3))
        assertEquals("66.7", sharePercent(2, 3))
        assertEquals("12.5", sharePercent(1, 8))
        assertEquals("100.0", sharePercent(7, 7))
        // (29 / 200) * 100 is 14.499999999999998 as a double.
        assertEquals("14.5", sharePercent(29, 200))
        // The double is 2.54999999999999982..., which toFixed rounds down. Intl.NumberFormat rounds 2.55.
        assertEquals("2.6", sharePercent(15300, 600000))
    }

    @Test
    fun percentMatchesTheWebAppInNode() {
        // Written by scripts/share-percent-cases.mjs, which runs the web app's format.ts in Node.
        val stream = javaClass.getResourceAsStream("/reports/share-percent-cases.tsv")!!
        val cases = stream.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") }.map { it.split('\t') }.toList()
        }
        assertTrue("only ${cases.size} cases", cases.size >= 1000)
        val mismatches = cases.filter { (part, whole, web) -> "${sharePercent(part.toLong(), whole.toLong())}%" != web }
        assertEquals("part, whole and web app output: ${mismatches.take(10)}", 0, mismatches.size)
        // The cases must hold halves where toFixed's rounding would fail this test.
        val toFixedMisses = cases.count { (part, whole, web) ->
            val percent = if (whole.toLong() > 0) part.toDouble() / whole.toLong() * 100 else 0.0
            "${jsToFixed(percent, 1)}%" != web
        }
        assertTrue("only $toFixedMisses cases where toFixed rounds otherwise", toFixedMisses >= 100)
    }

    /** SharePercentOnDeviceTest runs a sample of the cases on ART, which must come from this file. */
    @Test
    fun theDevicesSampleComesFromTheNodeCases() {
        val all = javaClass.getResourceAsStream("/reports/share-percent-cases.tsv")!!.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") }.toSet()
        }
        // Unit tests run in the module's directory.
        val sample = File("src/androidTest/assets/reports/share-percent-sample.tsv")
        assertTrue("no ${sample.absolutePath}", sample.isFile)
        val cases = sample.readLines().filterNot { it.startsWith("#") }
        assertTrue("only ${cases.size} cases", cases.size >= 200)
        assertEquals("cases not in share-percent-cases.tsv", emptyList<String>(), cases.filterNot { it in all })
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
