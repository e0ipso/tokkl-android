package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.entry.TimeRange
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.history.reconcileEntryRows
import com.github.e0ipso.tokkl.history.resolveEntryOverrides
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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

/** The edge of a block that a resize drags. */
enum class ResizeEdge { START, END }

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
 * Anything may change on the server while the app is in the background, so coming back to the
 * foreground makes every loaded week load again the next time it shows, and [foregrounds] tells
 * the calendar on screen to load its week at once. The old entries show until the new ones come,
 * and stay when the phone is offline. When the network comes back, [reconnected] loads the week on
 * screen again if it failed or kept old entries.
 *
 * The calendar's drags create, move and resize entries (K2) through [changes], the change queue,
 * so they show at once and work offline on the weeks the phone keeps. An entry from a week the
 * server sent goes to the queue whole, whether the phone keeps it or not. The cache keeps one from
 * before its window only until the send and the refresh after it, and the week then shows the copy
 * the cache last held, until the week loads again.
 *
 * Call [step], [showToday], [loadWeek], [wentToBackground], [cameToForeground], [reconnected],
 * [create], [move] and [resize] on the main thread.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 */
class CalendarModel(
    private val entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** The chosen day, or null to follow today. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** What the server sent, by the span it covers, as far as it has loaded. */
    private val loaded = MutableStateFlow<Map<Span, ServerWeek>>(emptyMap())

    /**
     * The newest copy the cache has shown of each entry from [loaded] that it held before its
     * window, as after a drag on an older week, which the cache keeps only until the send and the
     * refresh after it. [Edit.held] says whether the cache still held it when [state] last read
     * the cache. A load that starts once the cache no longer holds an entry replaces its copy.
     */
    private val edited = MutableStateFlow<Map<Long, Edit>>(emptyMap())

    private val reconciliation = MutableStateFlow<Map<Long, CachedTimeEntry?>>(emptyMap())

    fun reconcile(overrides: Map<Long, CachedTimeEntry?>) {
        val before = reconciliation.value
        val restored = overrides.filter { (id, entry) -> entry != null && before[id] != entry }.values.filterNotNull()
        // A response started before Undo must not replace its restored snapshot. Later loads
        // replace these entries through the same held/sent guard as older calendar edits.
        edited.update { edits -> edits + restored.associate { it.id to Edit(it, held = false) } }
        reconciliation.value = overrides
    }

    private val choice = combine(selected, loaded, reconciliation) { day, weeks, overrides -> Triple(day, weeks, overrides) }

    /** The app went to the background, and has not come back yet. */
    private var backgrounded = false

    private val foregroundCount = MutableStateFlow(0)

    /**
     * How many times the app came back to the foreground or the network came back; the calendar
     * on screen loads its week again on each when the week is due.
     */
    val foregrounds: StateFlow<Int> = foregroundCount

    val state: StateFlow<CalendarState> = combine(entries, projects, tasks, accountSettings, choice) { cached, projectRows, taskRows, settings, (day, weeks, overrides) ->
        val time = settings?.time(clock)
        when {
            settings == null -> CalendarState.Loading
            time == null -> CalendarState.NoAccountTime(settings.timeZone)
            else -> {
                val today = time.today()
                val shown = day ?: today
                val weekStart = time.startOfWeek(shown)
                val server = weeks[spanOf(time, weekStart, today)]
                val resolved = resolveEntryOverrides(overrides, changes::currentId)
                val edits = keepEdits(cached, weeks, time, resolved.values.filterNotNull().mapTo(HashSet()) { it.id })
                val restored = resolved.mapValues { (id, entry) ->
                    entry?.let { edits[id]?.entry ?: server?.entries?.firstOrNull { it.id == id } ?: it }
                }
                val (current, serverRows) = reconcileEntryRows(cached, server?.entries.orEmpty().map { edits[it.id]?.entry ?: it }, restored)
                CalendarState.Shown(
                    selected = shown,
                    weekStart = weekStart,
                    today = today,
                    zone = time.zone,
                    settings = settings,
                    entries = weekEntries(current, serverRows, projectRows, taskRows, time, weekStart),
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

    /** Shows [day], as a tap on it in the week's strip does. */
    fun select(day: LocalDate) {
        selected.value = day
    }

    /** Shows today, and follows it from then on. */
    fun showToday() {
        selected.value = null
    }

    /**
     * Loads the server's part of the week from [weekStart], unless it is loading or has loaded
     * since the app last came to the foreground: the whole week outside the cache, or the entries
     * from before the cache for a cached week. Showing a week is the event that loads it, and so
     * are Try again after a failure and coming back to the foreground. A week that failed loads
     * again the next time it shows. A week that loaded before keeps its entries on screen while
     * they load again, and keeps them, to load again the next time, when the server is out of
     * reach.
     */
    fun loadWeek(weekStart: LocalDate) {
        val time = accountSettings.value?.time(clock) ?: return
        val span = spanOf(time, weekStart, time.today())
        val known = loaded.value[span]
        if (known != null && (known.status == WeekSource.Loading || known.status == WeekSource.Loaded && !known.stale)) return
        val before = known?.takeIf { it.status == WeekSource.Loaded }
        loaded.update { it + (span to (before?.copy(stale = false) ?: ServerWeek(emptyList(), WeekSource.Loading))) }
        // The server has every edit that the cache no longer holds, so this load's copies replace them.
        val sent = edited.value.filterValues { !it.held }
        scope.launch {
            val week = withContext(io) { fetch(span) }
            val kept = before?.takeIf { week.status == WeekSource.Unreachable }?.copy(stale = true)
            if (kept == null && week.status == WeekSource.Loaded) {
                val ids = week.entries.mapTo(HashSet()) { it.id }
                edited.update { edits -> edits.filterNot { (id, edit) -> id in ids && sent[id] == edit } }
            }
            loaded.update { it + (span to (kept ?: week)) }
        }
    }

    /**
     * The app went to the background. Call it when the activity stops, but not for a
     * configuration change, which is no trip to the background.
     */
    fun wentToBackground() {
        backgrounded = true
    }

    /**
     * The network came back while the app is in the foreground. [foregrounds] counts up, so the
     * calendar on screen loads its week again when it failed or when it kept old entries offline.
     * A week that loaded since the last foreground asks nothing.
     */
    fun reconnected() {
        foregroundCount.update { it + 1 }
    }

    /**
     * The app came back to the foreground. After [wentToBackground], every loaded week loads
     * again the next time it shows, and [foregrounds] counts up so the calendar on screen loads
     * its week at once.
     */
    fun cameToForeground() {
        if (!backgrounded) return
        backgrounded = false
        loaded.update { weeks -> weeks.mapValues { (_, week) -> if (week.status == WeekSource.Loaded) week.copy(stale = true) else week } }
        foregroundCount.update { it + 1 }
    }

    /**
     * The entry [id] as the server sent it for a week the phone does not keep, or null. The entry
     * form opens on this copy of an entry the cache lacks.
     */
    fun serverEntry(id: Long): CachedTimeEntry? =
        loaded.value.values.firstNotNullOfOrNull { week -> week.entries.firstOrNull { it.id == id } }
            ?.let { edited.value[id]?.entry ?: it }

    /**
     * Updates [edited] from [cached] and returns it. An entry of [weeks] that the cache holds from
     * before its window was changed on the phone, and its copy goes in, with the times the server
     * stores for it. One that the cache holds inside its window shows from the cache, and one that
     * the cache dropped keeps its copy, no longer held.
     */
    private fun keepEdits(cached: List<EntryWithSendState>, weeks: Map<Span, ServerWeek>, time: AccountTime, restored: Set<Long>): Map<Long, Edit> {
        val ids = weeks.values.flatMapTo(HashSet()) { week -> week.entries.map { it.id } } + restored
        val windowStart = HistoryWindow.endingToday(time).start
        val held = cached.filter { it.entry.id in ids }.associateBy { it.entry.id }
        return edited.updateAndGet { edits ->
            val dropped = edits.filterKeys { it !in held }.mapValues { (_, edit) -> edit.copy(held = false) }
            val kept = held.values.filter { it.entry.start < windowStart }.associate { it.entry.id to Edit(it.entry.stored(time), held = true) }
            dropped + kept
        }
    }

    /**
     * Queues the entry that a drag from clock minute [fromMinute] to [toMinute] on [day] makes, in
     * the account's default workspace and with no description, and calls [onCreated] with its id
     * so the entry form can open on it, as the web app's editor opens after a drag. A drag that
     * never grew makes nothing.
     */
    fun create(day: LocalDate, fromMinute: Int, toMinute: Int, onCreated: (Long) -> Unit = {}) {
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val workspaceId = settings.defaultWorkspaceId ?: return
        val range = createdRange(day, fromMinute, toMinute, dragStep(settings.dragResolution), time) ?: return
        if (range.stop < range.start || time.snapUnsent(range.start, range.stop) == null) return
        scope.launch { onCreated(changes.create(workspaceId, EntryFields(), range.start, range.stop)) }
    }

    /**
     * Queues the stopped [entry] moved [deltaMinutes] on the account's clock, from its block on
     * [day]. In [dayMode] the calendar shows [day] alone, and the entry stays on it.
     */
    fun move(entry: CalendarEntry, day: LocalDate, deltaMinutes: Int, dayMode: Boolean) =
        change(entry) { stop, time, _ -> movedRange(entry.start, stop, deltaMinutes, day, dayMode, time) }

    /** Queues the stopped [entry] with its [edge] moved [deltaMinutes] on the account's clock. */
    fun resize(entry: CalendarEntry, edge: ResizeEdge, deltaMinutes: Int) = change(entry) { stop, time, step ->
        when (edge) {
            ResizeEdge.START -> startResizedRange(entry.start, stop, deltaMinutes, step, time)
            ResizeEdge.END -> endResizedRange(entry.start, stop, deltaMinutes, step, time)
        }
    }

    /**
     * Queues [entry] with the span [range] gives for it, with every other field as the phone or
     * the server has it. The span starts from the times the block shows, which for an unsent entry
     * are the ones the server will store.
     */
    private fun change(entry: CalendarEntry, range: (stop: Instant, time: AccountTime, step: Int) -> TimeRange) {
        val stop = entry.stop ?: return
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val span = range(stop, time, dragStep(settings.dragResolution))
        if (span.stop < span.start || time.snapUnsent(span.start, span.stop) == null) return
        scope.launch {
            val cached = entries.first().firstOrNull { it.entry.id == entry.id }?.entry
            when {
                cached != null -> changes.update(cached.id, cached.fields(), span.start, span.stop)
                else -> serverEntry(entry.id)?.let { changes.updateOlder(it.id, it.workspaceId, it.fields(), span.start, span.stop) }
            }
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

    /**
     * A span from the server: its [entries] once loaded, how its load went, and whether it is
     * [stale], loaded before the app last came back to the foreground and due to load again.
     */
    private data class ServerWeek(val entries: List<CachedTimeEntry>, val status: WeekSource, val stale: Boolean = false)

    /** The copy of an entry changed on the phone, and whether the cache [held] it when last read. */
    private data class Edit(val entry: CachedTimeEntry, val held: Boolean)

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

/** The entry with the times the server stores once it gets it: a stopped entry's span snapped. */
private fun CachedTimeEntry.stored(time: AccountTime): CachedTimeEntry {
    val stop = stop?.takeIf { !it.isBefore(start) } ?: return this
    val span = time.snapUnsent(start, stop) ?: return this
    return copy(start = span.start, stop = span.stop)
}

private fun CachedTimeEntry.shown(
    unsent: Boolean,
    time: AccountTime,
    projects: Map<Long, CachedProject>,
    tasks: Map<Long, CachedTask>,
): CalendarEntry {
    // The server keeps a running entry's start as sent, so only a stopped one is snapped.
    // A rejected pending span keeps its actual times and unsent state for correction.
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
