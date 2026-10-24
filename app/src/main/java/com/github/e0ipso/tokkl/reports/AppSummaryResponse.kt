package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ApiResult
import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Typed recognized fields and a separate additive-field projection. A missing amount is not
 * zero. Required values and their types must still match the contract. Dates, resolution and
 * all ordered lists come from the server; no sums, sorting, bucketing or rate multiplication.
 */
internal fun decodeAppSummary(body: JsonObject): ApiResult<SummaryReport> = try {
    val response = ApiJson.decodeFromJsonElement<AppSummaryResponse>(body)
    body.requireStrings("start_date", "end_date", "resolution")
    val extras = ReportExtraProjection()
    val reportExtras = extras.fields(body, setOf("start_date", "end_date", "resolution", "week_start", "totals", "series", "groups"))
    val rawTotals = body.getValue("totals").jsonObject
    rawTotals.requireNumbers("seconds", "billable_seconds", "entry_count")
    val totalsExtras = extras.fields(rawTotals, setOf("seconds", "billable_seconds", "entry_count", "amounts"))
    val rawAmounts = rawTotals["amounts"] as? JsonArray
    val amounts = response.totals.amounts.orEmpty().mapIndexedNotNull { index, amount ->
        val raw = rawAmounts!![index].jsonObject
        raw.requireStrings("currency")
        raw.requireNumbers("cents")
        // A currency with no amount must not be turned into a zero-priced currency.
        amount.cents?.let {
            Amount(amount.currency, it, extras.fields(raw, setOf("currency", "cents")))
        }
    }
    val rawSeries = body.getValue("series") as JsonArray
    val points = response.series.mapIndexed { index, point ->
        rawSeries[index].jsonObject.requireStrings("start")
        rawSeries[index].jsonObject.requireNumbers("seconds", "billable_seconds")
        SeriesPoint(
            LocalDate.parse(point.start), point.seconds, point.billableSeconds,
            extras.fields(rawSeries[index].jsonObject, setOf("start", "seconds", "billable_seconds")),
        )
    }
    val rawGroups = body.getValue("groups") as JsonArray
    val projects = response.groups.mapIndexed { index, group ->
        val raw = rawGroups[index].jsonObject
        raw.requireStrings("title", "color")
        raw.requireNumbers("id", "seconds", "billable_seconds", "amount_cents")
        // Same-dimension grouping normally has no children. If a server adds children, keep
        // their association and show their raw fields rather than silently discarding them.
        val known = setOf("id", "title", "color", "seconds", "billable_seconds", "amount_cents") +
            if (raw["sub_groups"] == JsonArray(emptyList())) setOf("sub_groups") else emptySet()
        ProjectShare(group.id, group.title, group.color, group.seconds, group.billableSeconds, group.amountCents, extras.fields(raw, known))
    }
    val resolution = when (response.resolution) {
        "day" -> Resolution.DAY
        "week" -> Resolution.WEEK
        "month" -> Resolution.MONTH
        else -> throw IllegalArgumentException("unknown summary resolution")
    }
    ApiResult.Success(
        SummaryReport(
            ReportRange(LocalDate.parse(response.startDate), LocalDate.parse(response.endDate)),
            resolution,
            ReportTotals(response.totals.seconds, response.totals.billableSeconds, response.totals.entryCount, amounts, totalsExtras),
            points, projects, reportExtras, extras.truncated,
        ),
    )
} catch (e: IllegalArgumentException) {
    ApiResult.Failure(ApiError.InvalidResponse(e))
} catch (e: DateTimeException) {
    ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("invalid summary date", e)))
}

// kotlinx.serialization accepts quoted numerals for numeric properties. The report contract
// does not: a known field changing JSON type must surface as an incompatible response.
private fun JsonObject.requireNumbers(vararg names: String) {
    for (name in names) {
        val value = get(name) ?: continue
        require(value == JsonNull || value is JsonPrimitive && !value.isString) { "invalid summary number type" }
    }
}

private fun JsonObject.requireStrings(vararg names: String) {
    for (name in names) {
        val value = get(name) ?: continue
        require(value == JsonNull || value is JsonPrimitive && value.isString) { "invalid summary string type" }
    }
}

@Serializable
private data class AppSummaryResponse(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val resolution: String,
    val totals: AppTotals,
    val series: List<AppPoint>,
    val groups: List<AppGroup>,
)

@Serializable
private data class AppTotals(
    val seconds: Long,
    @SerialName("billable_seconds") val billableSeconds: Long,
    @SerialName("entry_count") val entryCount: Int,
    val amounts: List<AppAmount>? = null,
)

@Serializable
private data class AppAmount(val currency: String, val cents: Long? = null)

@Serializable
private data class AppPoint(
    val start: String,
    val seconds: Long,
    @SerialName("billable_seconds") val billableSeconds: Long,
)

@Serializable
private data class AppGroup(
    val id: Long?,
    val title: String,
    val color: String?,
    val seconds: Long,
    @SerialName("billable_seconds") val billableSeconds: Long,
    @SerialName("amount_cents") val amountCents: Long? = null,
)
