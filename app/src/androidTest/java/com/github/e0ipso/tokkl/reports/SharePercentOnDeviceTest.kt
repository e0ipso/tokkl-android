package com.github.e0ipso.tokkl.reports

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.time.jsShortestDecimal
import com.github.e0ipso.tokkl.time.jsToFixed
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report's percentages on ART, whose BigDecimal and parseDouble are not the JVM's: the phone
 * has to write each share as the web app's Intl.NumberFormat does in Node. The cases are a sample
 * of the file ReportFormatTest checks on the JVM, in the test APK's assets.
 */
class SharePercentOnDeviceTest {

    @Test
    fun sharesMatchTheWebAppInNode() {
        val cases = InstrumentationRegistry.getInstrumentation().context.assets.open("reports/share-percent-sample.tsv")
            .bufferedReader()
            .useLines { lines -> lines.filterNot { it.startsWith("#") }.map { it.split('\t') }.toList() }
        assertTrue("only ${cases.size} cases", cases.size >= 200)

        val mismatches = cases.filter { (part, whole, web) -> "${sharePercent(part.toLong(), whole.toLong())}%" != web }

        assertEquals("part, whole and the web app's output: ${mismatches.take(10)}", 0, mismatches.size)
        // The sample holds the halves where rounding the double's exact value, as toFixed does, fails.
        val toFixedMisses = cases.count { (part, whole, web) ->
            val percent = if (whole.toLong() > 0) part.toDouble() / whole.toLong() * 100 else 0.0
            "${jsToFixed(percent, 1)}%" != web
        }
        assertTrue("only $toFixedMisses cases where toFixed rounds otherwise", toFixedMisses >= 100)
    }

    /** The digits Node's String(x) writes for each double, from the shortest to the full 17. */
    @Test
    fun shortestDecimalsMatchJavaScript() {
        val cases = listOf(
            15300.0 / 600000 * 100 to "2.55",
            29.0 / 200 * 100 to "14.499999999999998",
            0.1 + 0.2 to "0.30000000000000004",
            1.0 / 3 to "0.3333333333333333",
            2.0 / 3 to "0.6666666666666666",
            100.0 to "100",
            0.000001234 to "0.000001234",
            // 2^60, next to a power of two, where the double below is nearer than the one above.
            1152921504606846976.0 to "1152921504606847000",
            Double.MAX_VALUE to "1.7976931348623157e+308",
            Double.MIN_VALUE to "5e-324",
        )
        for ((x, javaScript) in cases) {
            val expected = BigDecimal(javaScript.replace("e+", "e"))
            val actual = jsShortestDecimal(x)
            assertEquals("$x: $actual", 0, actual.compareTo(expected))
            assertEquals("$x: $actual has more digits than String(x)", expected.stripTrailingZeros().precision(), actual.stripTrailingZeros().precision())
        }
    }
}
