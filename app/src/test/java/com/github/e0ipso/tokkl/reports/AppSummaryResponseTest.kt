package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSummaryResponseTest {
    @Test
    fun serverValuesAndAllOrdersAreAuthoritative() {
        val report = decode()
        assertEquals(ReportTotals(7317, 6001, 9, listOf(Amount("XYZ", 101), Amount("USD", 0), Amount("EUR", 723))), report.totals)
        assertEquals(Resolution.WEEK, report.resolution)
        assertEquals(listOf("2026-10-28", "2026-10-21"), report.series.map { it.start.toString() })
        assertEquals(listOf(3L, 7L), report.series.map { it.seconds })
        assertEquals(listOf(1L, 5L), report.series.map { it.billableSeconds })
        assertEquals(listOf(2L, 1L, null), report.projects.map { it.id })
        assertEquals(listOf("Small first", "Large second", "Server label"), report.projects.map { it.title })
        assertEquals(listOf("#123456", "#abcdef", null), report.projects.map { it.color })
        assertEquals(listOf(0L, null, null), report.projects.map { it.amountCents })
        assertNull(report.currency)
        assertEquals(ReportRange(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-27")), report.range)
    }

    @Test
    fun absentNullAndEmptyAmountsStayAbsentWhileZeroIsPresent() {
        for (amounts in listOf("", ",\"amounts\":null", ",\"amounts\":[]")) {
            val body = """{"start_date":"2026-10-21","end_date":"2026-10-21","resolution":"day",
                "totals":{"seconds":0,"billable_seconds":0,"entry_count":0$amounts},"series":[],"groups":[]}"""
            assertTrue(decode(body).totals.amounts.isEmpty())
        }
        val report = decode(APP_SUMMARY_FIXTURE.replace("101", "null").replace("723", "null"))
        assertEquals(listOf(Amount("USD", 0)), report.totals.amounts)
        assertEquals("USD", report.currency)
    }

    @Test
    fun unknownCurrenciesUseTheExistingLiteralCodeFallback() {
        assertEquals("1.01 XYZ", formatMoney(decode().totals.amounts.first().cents, "XYZ", Locale.US))
    }

    @Test
    fun incompatibleRecognizedValuesFailInsteadOfBecomingEmptyReports() {
        val invalid = listOf(
            "{}", APP_SUMMARY_FIXTURE.replace("7317", "\"not a duration\""),
            APP_SUMMARY_FIXTURE.replace("7317", "\"7317\""),
            APP_SUMMARY_FIXTURE.replace("\"week\"", "\"fortnight\""),
            APP_SUMMARY_FIXTURE.replace("2026-10-28", "yesterday"),
            APP_SUMMARY_FIXTURE.replace("\"groups\":[", "\"groups\":null,\"ignored\":["),
            APP_SUMMARY_FIXTURE.replace("\"billable_seconds\":6001,", ""),
            APP_SUMMARY_FIXTURE.replace("\"amount_cents\":0", "\"amount_cents\":{}"),
        )
        for (body in invalid) {
            val answer = decodeAppSummary(Json.parseToJsonElement(body).jsonObject)
            assertTrue(body, (answer as ApiResult.Failure).error is ApiError.InvalidResponse)
        }
    }

    @Test
    fun additiveValuesAreGenericAndKeepTheirOwnerAndLiteralUnits() {
        val body = Json.parseToJsonElement(APP_SUMMARY_FIXTURE).jsonObject
        val totals = body.getValue("totals").jsonObject
        // These keys have no production counterparts; no currency/rate schema is assumed.
        val fields = mapOf(
            "newCounter" to JsonPrimitive(0),
            "future_rate_rows" to JsonArray(listOf(JsonObject(mapOf("unitCode" to JsonPrimitive("widgets"), "value" to JsonPrimitive(123))))),
            "newFlag" to JsonPrimitive(false), "newNull" to JsonNull,
        )
        val amended = JsonObject(body + ("totals" to JsonObject(totals + fields)) + ("serverNote" to JsonPrimitive("<b>literal</b>")))
        val report = (decodeAppSummary(amended) as ApiResult.Success).value
        assertEquals(listOf("New Counter", "Future rate rows", "New Flag", "New Null"), report.totals.additionalData.map { it.name })
        assertEquals(listOf("0", "[{Unit Code: \"widgets\", Value: 123}]", "false", "null"), report.totals.additionalData.map { it.value })
        assertEquals(listOf(ReportExtraField("Server Note", "\"<b>literal</b>\"")), report.additionalData)
        assertEquals(7317L, report.totals.seconds)
        assertFalse(report.additionalDataTruncated)
    }

    @Test
    fun groupBucketAndCurrencyAdditionsKeepTheirOwnAssociations() {
        val amended = APP_SUMMARY_FIXTURE
            .replace("\"title\":\"Small first\"", "\"title\":\"Small first\",\"newHint\":0")
            .replace("\"start\":\"2026-10-28\"", "\"start\":\"2026-10-28\",\"newHint\":false")
            .replace("\"currency\":\"XYZ\"", "\"currency\":\"XYZ\",\"newHint\":\"credit\"")
        val report = decode(amended)
        assertEquals("0", report.projects.first().additionalData.single().value)
        assertTrue(report.projects[1].additionalData.isEmpty())
        assertEquals("false", report.series.first().additionalData.single().value)
        assertTrue(report.series[1].additionalData.isEmpty())
        assertEquals("\"credit\"", report.totals.amounts.first().additionalData.single().value)
    }

    @Test
    fun additiveProjectionBoundsDepthWidthAndTextAcrossTheWholeReport() {
        val deep = JsonObject(mapOf("a" to JsonObject(mapOf("b" to JsonObject(mapOf("c" to JsonObject(mapOf("d" to JsonPrimitive(9)))))))))
        val huge = JsonObject((0..1000).associate { "extra_$it" to JsonPrimitive("x".repeat(10_000)) } + ("deep" to deep))
        val projection = ReportExtraProjection()
        val fields = projection.fields(huge, emptySet())
        assertEquals(12, fields.size)
        assertTrue(fields.all { it.value.length <= 241 })
        assertTrue(projection.truncated)
        val depths = ReportExtraProjection()
        assertTrue(depths.fields(JsonObject(mapOf("deep" to deep)), emptySet()).single().value.contains("…"))
        assertTrue(depths.truncated)
        val shared = ReportExtraProjection()
        val all = (1..100).flatMap { shared.fields(huge, emptySet()) }
        assertTrue(all.sumOf { it.name.length + it.value.length } < 14_000)
        assertTrue(shared.truncated)
    }

    @Test
    fun unsafeFormattingCharactersAndMarkupStayPlainText() {
        val projection = ReportExtraProjection()
        val field = projection.fields(JsonObject(mapOf("a\u202Eb" to JsonPrimitive("<script>\n\u202E"))), emptySet()).single()
        assertEquals("A b", field.name)
        assertFalse(field.value.contains('\u202E'))
        assertTrue(field.value.contains("<script>"))
        assertTrue(field.value.contains("\\n"))
    }

    private fun decode(body: String = APP_SUMMARY_FIXTURE): SummaryReport =
        (decodeAppSummary(Json.parseToJsonElement(body).jsonObject) as ApiResult.Success).value
}
