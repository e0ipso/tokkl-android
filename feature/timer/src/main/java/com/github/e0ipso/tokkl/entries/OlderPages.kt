package com.github.e0ipso.tokkl.entries

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.fold
import com.github.e0ipso.tokkl.cache.toTimeEntry
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The end of the history: the days before the ones shown. */
@Immutable
sealed interface OlderDays {

    /**
     * Older days may have entries. With [auto], they load once the end of the list shows. After a
     * page without entries from a server that does not say where its history starts, as Toggl
     * Track, only a tap loads more, so scrolling never walks back through empty weeks on its own.
     */
    data class More(val auto: Boolean) : OlderDays

    data object Loading : OlderDays

    /** The server has no entries before the days shown. */
    data object End : OlderDays

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : OlderDays

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : OlderDays

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : OlderDays

    /** Any other answer. [status] is the HTTP status, or null when the body was not a list of entries. */
    data class Failed(val status: Int?) : OlderDays
}

/** One request for older days: the days it loaded, or the status the days end with instead. */
internal sealed interface OlderPage {
    data class Loaded(val from: LocalDate, val entries: List<TimeEntry>, val status: OlderDays) : OlderPage

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

/** The history's days before the cache, from the signed-in account's server. */
class OlderPages(private val client: ApiClientProvider) {

    companion object {
        /** How many days one request for older entries covers, as the web app's "Show 14 more days". */
        const val PAGE_DAYS = 14L
    }

    /** The page of days before [before], and where the history starts, [known] or read now. */
    internal suspend fun page(
        time: AccountTime,
        before: LocalDate,
        known: HistoryBounds?,
    ): Pair<OlderPage, HistoryBounds?> {
        val api = client() ?: return OlderPage.NotLoaded(OlderDays.AuthFailed) to known
        val bounds = known ?: when (val answer = readBounds(api)) {
            is BoundsAnswer.Read -> answer.bounds
            is BoundsAnswer.Failed -> return OlderPage.NotLoaded(answer.status) to null
        }
        if (bounds.nothingBefore(before)) return OlderPage.NotLoaded(OlderDays.End) to bounds
        val from = before.minusDays(PAGE_DAYS)
        val start = from.atStartOfDay(time.zone).toInstant()
        val end = before.atStartOfDay(time.zone).toInstant()
        val page = api.entries.list(start, end).fold(
            onSuccess = { answer ->
                val entries = answer.mapNotNull { it.toTimeEntry() }.filter { it.start >= start && it.start < end }
                val status = when {
                    bounds.nothingBefore(from) -> OlderDays.End
                    else -> OlderDays.More(auto = entries.isNotEmpty() || bounds is HistoryBounds.Known)
                }
                OlderPage.Loaded(from, entries, status)
            },
            onFailure = { OlderPage.NotLoaded(statusOf(it)) },
        )
        return page to bounds
    }
}

/** `GET /api/app/bounds`, Tokkl's first and last day with an entry in the account's timezone. */
private suspend fun readBounds(api: TokklApi): BoundsAnswer = when (val answer = api.entries.bounds()) {
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
    is ApiError.UnexpectedStatus -> if (error.status in PROXY_UNREACHABLE) {
        OlderDays.Unreachable
    } else {
        OlderDays.Failed(
            error.status,
        )
    }

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
