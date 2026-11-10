package com.github.e0ipso.tokkl.entries

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.fold
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.toTimeEntry
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Where the shown week's entries come from. */
@Immutable
sealed interface WeekSource {

    /**
     * The phone keeps the week: today's week and the week before it. The entries that start
     * before the cache and run into the week join them once the server sends them.
     */
    data object Cached : WeekSource

    /** The week is not on the phone, and its entries load from the server. */
    data object Loading : WeekSource

    data object Loaded : WeekSource

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : WeekSource

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : WeekSource

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : WeekSource

    /** Any other answer. [status] is the HTTP status, or null when the body was not a list of entries. */
    data class Failed(val status: Int?) : WeekSource

    /** The week's entries are known, from the phone or the server. */
    val shows: Boolean get() = this == Cached || this == Loaded
}

/** The entries that start before [to] and run past [from], or still run. */
internal data class WeekSpan(val from: Instant, val to: Instant)

/**
 * A span from the server: its [entries] once loaded, how its load went, and whether it is
 * [stale], loaded before the app last came back to the foreground and due to load again.
 */
internal data class ServerWeek(val entries: List<TimeEntry>, val status: WeekSource, val stale: Boolean = false)

/**
 * The server's part of the week from [weekStart]. For a week outside the cache, that is the
 * whole week. The cache holds every entry that starts from its first day on, so for a cached
 * week it is the entries that start before the cache and run into it, the same for both
 * cached weeks.
 */
internal fun spanOf(time: AccountTime, weekStart: LocalDate, today: LocalDate): WeekSpan {
    if (isCached(weekStart, today)) {
        val cacheStart = today.minusDays(HistoryWindow.DAYS - 1).atStartOfDay(time.zone).toInstant()
        return WeekSpan(cacheStart, cacheStart)
    }
    return WeekSpan(
        weekStart.atStartOfDay(time.zone).toInstant(),
        weekStart.plusWeeks(1).atStartOfDay(time.zone).toInstant(),
    )
}

/** The week calendar's entries that the cache does not hold, from the signed-in account's server. */
class ServerWeeks(private val client: ApiClientProvider) {

    /**
     * The entries in [span]. The server finds entries by their start, so this asks from
     * [LONGEST_ENTRY] before it.
     */
    internal suspend fun week(span: WeekSpan): ServerWeek {
        val api = client() ?: return ServerWeek(emptyList(), WeekSource.AuthFailed)
        return api.entries.list(span.from - LONGEST_ENTRY, span.to).fold(
            onSuccess = { entries ->
                val read = entries.mapNotNull { it.toTimeEntry() }
                ServerWeek(read.filter { entry -> entry.stop.let { it == null || it > span.from } }, WeekSource.Loaded)
            },
            onFailure = { ServerWeek(emptyList(), statusOf(it)) },
        )
    }
}

private fun statusOf(error: ApiError): WeekSource = when (error) {
    is ApiError.Network -> WeekSource.Unreachable

    // A reverse proxy answers these while the server behind it is down.
    is ApiError.UnexpectedStatus -> if (error.status in
        PROXY_UNREACHABLE
    ) {
        WeekSource.Unreachable
    } else {
        WeekSource.Failed(
            error.status,
        )
    }

    ApiError.AuthFailed -> WeekSource.AuthFailed

    is ApiError.TrustRefused -> WeekSource.TrustRefused

    is ApiError.RateLimited -> WeekSource.Failed(error.status)

    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> WeekSource.Failed(null)
}

/** Bad gateway, service unavailable and gateway timeout. */
private val PROXY_UNREACHABLE = setOf(502, 503, 504)

/**
 * The longest stopped entry Tokkl stores, 999 hours, so a stopped entry that starts earlier ends
 * before the week.
 */
internal val LONGEST_ENTRY: Duration = Duration.ofHours(999)

/**
 * The phone keeps today and the 13 days before it, so it holds every day of the week from
 * [weekStart] up to [today] when the week starts in that window: today's week and the week
 * before it.
 */
internal fun isCached(weekStart: LocalDate, today: LocalDate): Boolean =
    weekStart <= today && weekStart >= today.minusDays(HistoryWindow.DAYS - 1)
