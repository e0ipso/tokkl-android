package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
/** The entries that start before [to] and run past [from], or still run. */
internal data class WeekSpan(val from: Instant, val to: Instant)

/**
 * A span from the server: its [entries] once loaded, how its load went, and whether it is
 * [stale], loaded before the app last came back to the foreground and due to load again.
 */
internal data class ServerWeek(val entries: List<CachedTimeEntry>, val status: WeekSource, val stale: Boolean = false)

/**
 * The server's part of the week from [weekStart]. For a week outside the cache, that is the
 * whole week. The cache holds every entry that starts from its first day on, so for a cached
 * week it is the entries that start before the cache and run into it, the same for both
 * cached weeks.
 */
internal fun spanOf(time: AccountTime, weekStart: LocalDate, today: LocalDate): WeekSpan {
    if (CalendarViewModel.isCached(weekStart, today)) {
        val cacheStart = today.minusDays(HistoryWindow.DAYS - 1).atStartOfDay(time.zone).toInstant()
        return WeekSpan(cacheStart, cacheStart)
    }
    return WeekSpan(weekStart.atStartOfDay(time.zone).toInstant(), weekStart.plusDays(7).atStartOfDay(time.zone).toInstant())
}

/**
 * The entries in [span], from [client]'s server. The server finds entries by their start, so this
 * asks from [CalendarViewModel.LONGEST_ENTRY] before it. Blocks on the network.
 */
internal fun fetchWeek(client: ApiClientProvider, span: WeekSpan): ServerWeek {
    val api = client() ?: return ServerWeek(emptyList(), WeekSource.AuthFailed)
    val url = api.server.api("me", "time_entries").newBuilder()
        .addQueryParameter("start_date", (span.from - CalendarViewModel.LONGEST_ENTRY).toString())
        .addQueryParameter("end_date", span.to.toString())
        .build()
    return when (val answer = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
        is ApiResult.Failure -> ServerWeek(emptyList(), statusOf(answer.error))
        is ApiResult.Success -> ServerWeek(
            answer.value.mapNotNull { it.toCached() }.filter { entry -> entry.stop.let { it == null || it > span.from } },
            WeekSource.Loaded,
        )
    }
}

private fun statusOf(error: ApiError): WeekSource = when (error) {
    is ApiError.Network -> WeekSource.Unreachable
    // A reverse proxy answers these while the server behind it is down.
    is ApiError.UnexpectedStatus -> if (error.status in PROXY_UNREACHABLE) WeekSource.Unreachable else WeekSource.Failed(error.status)
    ApiError.AuthFailed -> WeekSource.AuthFailed
    is ApiError.TrustRefused -> WeekSource.TrustRefused
    is ApiError.RateLimited -> WeekSource.Failed(error.status)
    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> WeekSource.Failed(null)
}

/** Bad gateway, service unavailable and gateway timeout. */
private val PROXY_UNREACHABLE = setOf(502, 503, 504)
