package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/** What the calendar shows. */
sealed interface CalendarState {

    /** The cache or the account settings are not read yet. */
    data object Loading : CalendarState

    /** The phone cannot resolve the account's timezone, [timeZone], so it cannot place entries. */
    data class NoAccountTime(val timeZone: String) : CalendarState

    /**
     * The week that holds [selected], from [weekStart], the account's first day of the week, as of
     * [today] in the account's timezone [zone]. [entries] are those that touch the week, from the
     * phone or the server as [week] says, with the running one, which ends at the current time.
     */
    data class Shown(
        val selected: LocalDate,
        val weekStart: LocalDate,
        val today: LocalDate,
        val zone: ZoneId,
        val settings: AccountSettings,
        val entries: List<CalendarEntry>,
        val week: WeekSource,
    ) : CalendarState {
        /** The seven days of the week, from [weekStart]. */
        val weekDays: List<LocalDate> get() = (0L until 7L).map(weekStart::plusDays)
    }
}

/** Where the shown week's entries come from. */
sealed interface WeekSource {

    /**
     * The phone keeps the week: today's week and the week before it. The entries that start
     * before the cache and run into the week join them once the server sends them.
     */
    data object Cached : WeekSource

    /** The week is not on the phone, and its entries load from the server. */
    data object Loading : WeekSource

    /** The server sent the week's entries. */
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

/**
 * The calendar on the Timer page (docs/PRD.md K1): a week at a time, from the account's first day
 * of the week, in the account's timezone. Today's week and the week before it come from the
 * phone, which keeps the entries that start today and in the 13 days before it, so they show
 * offline. Any other week loads from the configured server when it is chosen.
 *
 * The server finds entries by their start, and an entry can start up to [LONGEST_ENTRY] before a
 * week and still run into it. So a week outside the cache asks for the entries that start from
 * that long before it, and keeps those that run into it. When a cached week shows, the entries
 * that start in that time before the cache and run into it load the same way, once for both
 * cached weeks; offline, the week shows what the phone keeps. What the server sends lives in
 * memory while the model does, and nothing of it is written to the phone. Nothing loads on a
 * timer.
 *
 * Call [step], [showToday] and [loadWeek] on the main thread.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 */
class CalendarModel(
    entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** The chosen day, or null to follow today. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** What the server sent, by the span it covers, as far as it has loaded. */
    private val loaded = MutableStateFlow<Map<Span, ServerWeek>>(emptyMap())

    private val choice = combine(selected, loaded) { day, weeks -> day to weeks }

    val state: StateFlow<CalendarState> = combine(entries, projects, tasks, accountSettings, choice) { cached, projectRows, taskRows, settings, (day, weeks) ->
        val time = settings?.time(clock)
        when {
            settings == null -> CalendarState.Loading
            time == null -> CalendarState.NoAccountTime(settings.timeZone)
            else -> {
                val today = time.today()
                val shown = day ?: today
                val weekStart = time.startOfWeek(shown)
                val server = weeks[spanOf(time, weekStart, today)]
                CalendarState.Shown(
                    selected = shown,
                    weekStart = weekStart,
                    today = today,
                    zone = time.zone,
                    settings = settings,
                    entries = weekEntries(cached, server?.entries.orEmpty(), projectRows, taskRows, time, weekStart),
                    week = if (isCached(weekStart, today)) WeekSource.Cached else server?.status ?: WeekSource.Loading,
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CalendarState.Loading)

    /** Shows the day [days] days from the chosen one: 7 or -7 for a week, 1 or -1 for a day. */
    fun step(days: Long) {
        val time = accountSettings.value?.time(clock) ?: return
        selected.value = (selected.value ?: time.today()).plusDays(days)
    }

    /** Shows today, and follows it from then on. */
    fun showToday() {
        selected.value = null
    }

    /**
     * Loads the server's part of the week from [weekStart], unless it has loaded or is loading:
     * the whole week outside the cache, or the entries from before the cache for a cached week.
     * Showing a week is the event that loads it, and so is Try again after a failure. A week that
     * failed loads again the next time it shows.
     */
    fun loadWeek(weekStart: LocalDate) {
        val time = accountSettings.value?.time(clock) ?: return
        val span = spanOf(time, weekStart, time.today())
        val status = loaded.value[span]?.status
        if (status == WeekSource.Loading || status == WeekSource.Loaded) return
        loaded.update { it + (span to ServerWeek(emptyList(), WeekSource.Loading)) }
        scope.launch {
            val week = withContext(io) { fetch(span) }
            loaded.update { it + (span to week) }
        }
    }

    /**
     * The server's part of the week from [weekStart]. For a week outside the cache, that is the
     * whole week. The cache holds every entry that starts from its first day on, so for a cached
     * week it is the entries that start before the cache and run into it, the same for both
     * cached weeks.
     */
    private fun spanOf(time: AccountTime, weekStart: LocalDate, today: LocalDate): Span {
        if (isCached(weekStart, today)) {
            val cacheStart = today.minusDays(HistoryWindow.DAYS - 1).atStartOfDay(time.zone).toInstant()
            return Span(cacheStart, cacheStart)
        }
        return Span(weekStart.atStartOfDay(time.zone).toInstant(), weekStart.plusDays(7).atStartOfDay(time.zone).toInstant())
    }

    /** The entries in [span]. The server finds entries by their start, so this asks from [LONGEST_ENTRY] before it. */
    private fun fetch(span: Span): ServerWeek {
        val api = client() ?: return ServerWeek(emptyList(), WeekSource.AuthFailed)
        val url = api.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", (span.from - LONGEST_ENTRY).toString())
            .addQueryParameter("end_date", span.to.toString())
            .build()
        return when (val answer = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
            is ApiResult.Failure -> ServerWeek(emptyList(), statusOf(answer.error))
            is ApiResult.Success -> ServerWeek(
                answer.value.mapNotNull { it.toCached() }.filter { it.stop == null || it.stop > span.from },
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

    /** The entries that start before [to] and run past [from], or still run. */
    private data class Span(val from: Instant, val to: Instant)

    /** A span from the server: its [entries] once loaded, and how its load went. */
    private data class ServerWeek(val entries: List<CachedTimeEntry>, val status: WeekSource)

    companion object {
        /** Bad gateway, service unavailable and gateway timeout. */
        private val PROXY_UNREACHABLE = setOf(502, 503, 504)

        /** How long the state keeps following the cache after the last screen stops reading it. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The longest stopped entry Tokkl stores, 999 hours, so a stopped entry that starts
         * earlier ends before the week.
         */
        internal val LONGEST_ENTRY: Duration = Duration.ofHours(999)

        /**
         * The phone keeps today and the 13 days before it, so it holds every day of the week from
         * [weekStart] up to [today] when the week starts in that window: today's week and the
         * week before it.
         */
        internal fun isCached(weekStart: LocalDate, today: LocalDate): Boolean =
            weekStart <= today && weekStart >= today.minusDays(HistoryWindow.DAYS - 1)
    }
}

/**
 * The entries that touch the week from [weekStart] in [time]'s zone: [cached] from the phone, with
 * queued changes applied, and [server], what the server sent for the week. An entry in both shows
 * as cached. A stopped entry with a change the server has not received shows the times the server
 * will store, as in the history.
 */
internal fun weekEntries(
    cached: List<EntryWithSendState>,
    server: List<CachedTimeEntry>,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    time: AccountTime,
    weekStart: LocalDate,
): List<CalendarEntry> {
    val from = weekStart.atStartOfDay(time.zone).toInstant()
    val to = weekStart.plusDays(7).atStartOfDay(time.zone).toInstant()
    val cachedIds = cached.mapTo(HashSet()) { it.entry.id }
    val rows = cached.map { it.entry to it.unsent } + server.filter { it.id !in cachedIds }.map { it to false }
    val projectsById = projects.associateBy { it.id }
    val tasksById = tasks.associateBy { it.id }
    return rows
        .map { (entry, unsent) -> entry.shown(unsent, time, projectsById, tasksById) }
        .filter { it.start < to && (it.stop == null || it.stop > from) }
}

private fun CachedTimeEntry.shown(
    unsent: Boolean,
    time: AccountTime,
    projects: Map<Long, CachedProject>,
    tasks: Map<Long, CachedTask>,
): CalendarEntry {
    // The server keeps a running entry's start as sent, so only a stopped one is snapped.
    val span = stop?.takeIf { unsent }?.let { time.snapUnsent(start, it) }
    val project = projectId?.let(projects::get)
    return CalendarEntry(
        id = id,
        description = description,
        project = project?.name,
        projectColor = project?.color,
        task = taskId?.let(tasks::get)?.name,
        start = span?.start ?: start,
        stop = span?.stop ?: stop,
        unsent = unsent,
    )
}
