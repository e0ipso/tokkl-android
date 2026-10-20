package com.github.e0ipso.tokkl.history

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What the history shows. */
sealed interface HistoryState {

    /** The cache or the account settings are not read yet. */
    data object Loading : HistoryState

    /** The phone cannot resolve the account's timezone, [timeZone], so it cannot tell the days apart. */
    data class NoAccountTime(val timeZone: String) : HistoryState

    /**
     * The [days] with entries, newest first, as of [today] in the account's timezone [zone], and
     * what comes after them.
     */
    data class Shown(
        val days: List<HistoryDay>,
        val today: LocalDate,
        val zone: ZoneId,
        val settings: AccountSettings,
        val older: OlderDays,
    ) : HistoryState
}

/** The end of the history: the days before the ones shown. */
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

/**
 * The history on the Timer page (docs/PRD.md H1): the cached two weeks from the phone, which
 * show offline, and older days that [loadOlder] pages from the configured server, [PAGE_DAYS] at
 * a time, when the list's end shows. Nothing loads on a timer. The older days live in memory
 * while the model does, and nothing of them is written to the phone.
 *
 * On Tokkl, `GET /api/app/bounds` tells where the account's history starts, so the list knows
 * when no older day has entries. A server without it, such as Toggl Track, gets pages until one
 * comes back empty, and then more only on a tap.
 *
 * The cache's window moves on with the date, and a refresh then drops the days that fell out of
 * it. The older days end where the window began when they first loaded, so the days the cache
 * drops after that stay in the list as the cache last held them, with no request, and the list
 * keeps every day from today to the oldest day loaded.
 *
 * Call [loadOlder] on the main thread.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 */
class HistoryModel(
    entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val older = MutableStateFlow(Older())

    /** Where the account's history starts, once the server has said or shown that it cannot. */
    private var bounds: Bounds? = null

    /**
     * The entries of the days the cache dropped between [Older.until] and its first day, and the
     * cached entries as [state] last saw them. Only [state]'s upstream reads and writes these, one
     * emission at a time.
     */
    private var dropped: List<CachedTimeEntry> = emptyList()
    private var lastCached: List<CachedTimeEntry> = emptyList()

    val state: StateFlow<HistoryState> = combine(entries, projects, tasks, accountSettings, older) { cached, projectRows, taskRows, settings, loaded ->
        val time = settings?.time(clock)
        when {
            settings == null -> HistoryState.Loading
            time == null -> HistoryState.NoAccountTime(settings.timeZone)
            else -> HistoryState.Shown(
                days = historyDays(
                    cached,
                    daysTheCacheDropped(cached, loaded.until, time) + loaded.entries,
                    projectRows,
                    taskRows,
                    time,
                    collapse = settings.collapseSimilar,
                ),
                today = time.today(),
                zone = time.zone,
                settings = settings,
                older = loaded.status,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryState.Loading)

    /**
     * Loads the [PAGE_DAYS] days before the oldest day loaded, or before the cached two weeks at
     * first. Does nothing while a page loads, once the server has no older entries, or without
     * an account timezone. After a failure it tries the same days again.
     */
    fun loadOlder() {
        val current = older.value
        if (current.status == OlderDays.Loading || current.status == OlderDays.End) return
        val time = accountSettings.value?.time(clock) ?: return
        val before = current.from ?: firstCachedDay(time)
        older.value = current.copy(status = OlderDays.Loading)
        val known = bounds
        scope.launch {
            val (page, read) = withContext(io) { fetch(time, before, known) }
            bounds = read
            val loaded = older.value
            older.value = when (page) {
                is Page.Loaded -> Older(loaded.entries + page.entries, page.from, loaded.until ?: before, page.status)
                is Page.NotLoaded -> loaded.copy(
                    // The server's word that no entry comes before the cache fixes where the older days end too.
                    until = loaded.until ?: before.takeIf { page.status == OlderDays.End },
                    status = page.status,
                )
            }
        }
    }

    /**
     * The entries of the days from [until] to the day before the cache's first day: days the
     * cache held when the older days first loaded and has since dropped, or will drop, as its
     * window moved on with the date. They come from the cache as [state] last saw it, the newest
     * version of each entry winning, and none when no older days have loaded.
     */
    private fun daysTheCacheDropped(cached: List<EntryWithSendState>, until: LocalDate?, time: AccountTime): List<CachedTimeEntry> {
        val now = cached.map { it.entry }
        val before = lastCached
        lastCached = now
        val firstCached = firstCachedDay(time)
        dropped = if (until == null || until >= firstCached) {
            emptyList()
        } else {
            (dropped + before + now)
                .filter { time.local(it.start).toLocalDate().let { day -> day >= until && day < firstCached } }
                .associateBy { it.id }
                .values
                .toList()
        }
        return dropped
    }

    /** The page of days before [before], and where the history starts, [known] or read now. */
    private fun fetch(time: AccountTime, before: LocalDate, known: Bounds?): Pair<Page, Bounds?> {
        val api = client() ?: return Page.NotLoaded(OlderDays.AuthFailed) to known
        val bounds = known ?: when (val answer = readBounds(api)) {
            is BoundsAnswer.Read -> answer.bounds
            is BoundsAnswer.Failed -> return Page.NotLoaded(answer.status) to null
        }
        if (bounds.nothingBefore(before)) return Page.NotLoaded(OlderDays.End) to bounds
        val from = before.minusDays(PAGE_DAYS)
        val start = from.atStartOfDay(time.zone).toInstant()
        val end = before.atStartOfDay(time.zone).toInstant()
        val url = api.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", start.toString())
            .addQueryParameter("end_date", end.toString())
            .build()
        val page = when (val answer = api.get(url, ListSerializer(ServerTimeEntry.serializer()))) {
            is ApiResult.Failure -> Page.NotLoaded(statusOf(answer.error))
            is ApiResult.Success -> {
                val entries = answer.value.mapNotNull { it.toCached() }.filter { it.start >= start && it.start < end }
                val status = when {
                    bounds.nothingBefore(from) -> OlderDays.End
                    else -> OlderDays.More(auto = entries.isNotEmpty() || bounds is Bounds.Known)
                }
                Page.Loaded(from, entries, status)
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
                else -> BoundsAnswer.Read(Bounds.Unknown)
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
     * The older days loaded so far: their [entries], from the day [from] on, and what comes next.
     * They end the day before [until], the cache's first day when the first page loaded or the
     * server said no entry comes before it, and null until then.
     */
    private data class Older(
        val entries: List<CachedTimeEntry> = emptyList(),
        val from: LocalDate? = null,
        val until: LocalDate? = null,
        val status: OlderDays = OlderDays.More(auto = true),
    )

    private sealed interface Page {
        data class Loaded(val from: LocalDate, val entries: List<CachedTimeEntry>, val status: OlderDays) : Page

        /** Nothing new: the days stay as they were, with [status] at the end. */
        data class NotLoaded(val status: OlderDays) : Page
    }

    private sealed interface Bounds {
        /** The account's first entry starts on [first], or the account has none when null. */
        data class Known(val first: LocalDate?) : Bounds

        data object Unknown : Bounds

        /** True when the server said that no entry starts before [day]. */
        fun nothingBefore(day: LocalDate): Boolean = this is Known && (first == null || first >= day)
    }

    private sealed interface BoundsAnswer {
        data class Read(val bounds: Bounds) : BoundsAnswer

        data class Failed(val status: OlderDays) : BoundsAnswer
    }

    companion object {
        /** How many days one request for older entries covers, as the web app's "Show 14 more days". */
        const val PAGE_DAYS = 14L

        /** Bad gateway, service unavailable and gateway timeout. */
        private val PROXY_UNREACHABLE = setOf(502, 503, 504)

        /** How long the state keeps following the cache after the last screen stops reading it. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The first day of the cached two weeks, today and the 13 days before it. */
        private fun firstCachedDay(time: AccountTime): LocalDate = time.today().minusDays(HistoryWindow.DAYS - 1)

        /**
         * The bounds' `first_date`. A body without a readable one says nothing about where the
         * history starts, while a null says the account has no entries.
         */
        private fun boundsOf(body: JsonObject): Bounds {
            val first = body["first_date"] as? JsonPrimitive ?: return Bounds.Unknown
            if (first is JsonNull) return Bounds.Known(null)
            if (!first.isString) return Bounds.Unknown
            return try {
                Bounds.Known(LocalDate.parse(first.content))
            } catch (e: DateTimeParseException) {
                Bounds.Unknown
            }
        }
    }
}
