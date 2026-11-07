package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One request for older days: the days it loaded, or the status the days end with instead. */
internal sealed interface OlderPage {
    data class Loaded(val from: LocalDate, val entries: List<CachedTimeEntry>, val status: OlderDays) : OlderPage

    /** Nothing new: the days stay as they were, with [status] at the end. */
    data class NotLoaded(val status: OlderDays) : OlderPage
}

/** Where the account's history starts, as far as the server has said. */
internal sealed interface HistoryBounds {
    /** The account's first entry starts on [first], or the account has none when null. */
    data class Known(val first: LocalDate?) : HistoryBounds

    data object Unknown : HistoryBounds

    /** True when the server said that no entry starts before [day]. */
    fun nothingBefore(day: LocalDate): Boolean = this is Known && (first == null || first >= day)
}

private sealed interface BoundsAnswer {
    data class Read(val bounds: HistoryBounds) : BoundsAnswer

    data class Failed(val status: OlderDays) : BoundsAnswer
}

/**
 * The page of days before [before] from [client]'s server, and where the history starts, [known]
 * or read now. Blocks on the network.
 */
internal fun fetchOlderPage(client: ApiClientProvider, time: AccountTime, before: LocalDate, known: HistoryBounds?): Pair<OlderPage, HistoryBounds?> {
    val api = client() ?: return OlderPage.NotLoaded(OlderDays.AuthFailed) to known
    val bounds = known ?: when (val answer = readBounds(api)) {
        is BoundsAnswer.Read -> answer.bounds
        is BoundsAnswer.Failed -> return OlderPage.NotLoaded(answer.status) to null
    }
    if (bounds.nothingBefore(before)) return OlderPage.NotLoaded(OlderDays.End) to bounds
    val from = before.minusDays(HistoryViewModel.PAGE_DAYS)
    val start = from.atStartOfDay(time.zone).toInstant()
    val end = before.atStartOfDay(time.zone).toInstant()
    val url = api.server.api("me", "time_entries").newBuilder()
        .addQueryParameter("start_date", start.toString())
        .addQueryParameter("end_date", end.toString())
        .build()
    val page = when (val answer = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
        is ApiResult.Failure -> OlderPage.NotLoaded(statusOf(answer.error))
        is ApiResult.Success -> {
            val entries = answer.value.mapNotNull { it.toCached() }.filter { it.start >= start && it.start < end }
            val status = when {
                bounds.nothingBefore(from) -> OlderDays.End
                else -> OlderDays.More(auto = entries.isNotEmpty() || bounds is HistoryBounds.Known)
            }
            OlderPage.Loaded(from, entries, status)
        }
    }
    return page to bounds
}

/** `GET /api/app/bounds`, Tokkl's first and last day with an entry in the account's timezone. */
private fun readBounds(api: TogglApiClient): BoundsAnswer =
    when (val answer = api.get(api.server.app("bounds"), JsonObject.serializer())) {
        is ApiResult.Success -> BoundsAnswer.Read(boundsOf(answer.value))
        is ApiResult.Failure -> when (val status = statusOf(answer.error)) {
            OlderDays.Unreachable, OlderDays.AuthFailed, OlderDays.TrustRefused -> BoundsAnswer.Failed(status)
            // A 404 from a server without it, or any other answer: page without knowing.
            else -> BoundsAnswer.Read(HistoryBounds.Unknown)
        }
    }

private fun statusOf(error: ApiError): OlderDays = when (error) {
    is ApiError.Network -> OlderDays.Unreachable
    // A reverse proxy answers these while the server behind it is down.
    is ApiError.UnexpectedStatus -> if (error.status in PROXY_UNREACHABLE) OlderDays.Unreachable else OlderDays.Failed(error.status)
    ApiError.AuthFailed -> OlderDays.AuthFailed
    is ApiError.TrustRefused -> OlderDays.TrustRefused
    is ApiError.RateLimited -> OlderDays.Failed(error.status)
    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> OlderDays.Failed(null)
}

/**
 * The bounds' `first_date`. A body without a readable one says nothing about where the
 * history starts, while a null says the account has no entries.
 */
private fun boundsOf(body: JsonObject): HistoryBounds {
    val first = body["first_date"] as? JsonPrimitive ?: return HistoryBounds.Unknown
    if (first is JsonNull) return HistoryBounds.Known(null)
    if (!first.isString) return HistoryBounds.Unknown
    return try {
        HistoryBounds.Known(LocalDate.parse(first.content))
    } catch (e: DateTimeParseException) {
        HistoryBounds.Unknown
    }
}

/** Bad gateway, service unavailable and gateway timeout. */
private val PROXY_UNREACHABLE = setOf(502, 503, 504)
