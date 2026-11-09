package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The calendar's weeks from the server: a week outside the cache, and for a cached week the
 * entries that start before the cache and run into it, when [load] is called. Coming back to the
 * foreground makes every loaded week load again the next time it shows, and [foregrounds] tells
 * the calendar on screen to load its week at once. The old entries show until the new ones come,
 * and stay offline.
 *
 * It is the one owner of [state], which changes only through [MutableStateFlow.update] in its
 * commands.
 *
 * @param serverWeeks the server's weeks. Runs on [io].
 * @param clock where today comes from, in the account's timezone.
 */
internal class WeekLoading(
    private val serverWeeks: ServerWeeks,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val mutableState = MutableStateFlow(WeeksState())

    /** What the server sent, by the span it covers, as far as it has loaded. */
    val state: StateFlow<WeeksState> = mutableState.asStateFlow()

    private val foregroundCount = MutableStateFlow(0)

    /**
     * How many times the app came back to the foreground or the network came back; the calendar
     * on screen loads its week again on each when the week is due.
     */
    val foregrounds: StateFlow<Int> = foregroundCount.asStateFlow()

    /**
     * Loads the server's part of the week from [weekStart], unless it is loading or has loaded
     * since the app last came to the foreground. A week that failed loads again the next time it
     * shows. A week that loaded before keeps its entries on screen while they load again, and
     * keeps them, to load again the next time, when the server is out of reach. A failure while
     * an event that would load the week came in tries once more while [selected] still shows it.
     * [fresh] gets each week that loaded anew, after it shows.
     */
    fun load(weekStart: LocalDate, selected: StateFlow<LocalDate?>, fresh: (ServerWeek) -> Unit) {
        val time = accountSettings.value?.time(clock) ?: return
        val span = spanOf(time, weekStart, time.today())
        val asked = mutableState.getAndUpdate { it.loading(span) }
        val known = asked.weeks[span]
        if (known?.isCurrent() == true) return
        val before = known?.takeIf { it.status == WeekSource.Loaded }
        val foregroundAtStart = foregroundCount.value
        scope.launch {
            val week = withContext(io) { serverWeeks.week(span) }
            val kept = before?.takeIf { week.status == WeekSource.Unreachable }?.copy(stale = true)
            var current = false
            mutableState.update { weeks ->
                current = weeks.generation == asked.generation
                if (current) weeks.copy(weeks = weeks.weeks + (span to (kept ?: week))) else weeks
            }
            if (!current) return@launch
            if (kept == null && week.status == WeekSource.Loaded) fresh(week)
            // A reconnect or foreground event can arrive while this request is still pending.
            // Its load does nothing then. Retry the failed attempt once for that event, while
            // this span is still shown, without starting a polling loop.
            val due = foregroundCount.value != foregroundAtStart && !mutableState.value.backgrounded
            if (week.status == WeekSource.Unreachable && due) loadAgainIfShown(span, selected, fresh)
        }
    }

    /** Loads the week of [span] again while [selected] still shows it. */
    private fun loadAgainIfShown(span: WeekSpan, selected: StateFlow<LocalDate?>, fresh: (ServerWeek) -> Unit) {
        val time = accountSettings.value?.time(clock) ?: return
        val currentWeek = time.startOfWeek(selected.value ?: time.today())
        if (spanOf(time, currentWeek, time.today()) == span) load(currentWeek, selected, fresh)
    }

    /** The app went to the background. A configuration change is no trip to the background. */
    fun wentToBackground() = mutableState.update { it.copy(backgrounded = true) }

    /**
     * The app came back to the foreground. After [wentToBackground], every loaded week loads
     * again the next time it shows, and [foregrounds] counts up so the calendar on screen loads
     * its week at once.
     */
    fun cameToForeground() {
        val before = mutableState.getAndUpdate { if (it.backgrounded) it.returned() else it }
        if (before.backgrounded) foregroundCount.update { it + 1 }
    }

    /** The network came back: [foregrounds] counts up, so the calendar on screen loads its week again. */
    fun reconnected() = foregroundCount.update { it + 1 }

    /** Drops the weeks, so an answer for the account before changes nothing. */
    fun forget() = mutableState.update { WeeksState(backgrounded = it.backgrounded, generation = it.generation + 1) }
}

/**
 * The server's [weeks], by the span each covers; whether the app is [backgrounded], gone to the
 * background and not back yet; and [generation], the accounts whose weeks were dropped.
 */
internal data class WeeksState(
    val weeks: Map<WeekSpan, ServerWeek> = emptyMap(),
    val backgrounded: Boolean = false,
    val generation: Int = 0,
) {
    /** These weeks with [span] loading, unless it is loading or loaded and not stale. */
    fun loading(span: WeekSpan): WeeksState {
        val known = weeks[span]
        if (known?.isCurrent() == true) return this
        val before = known?.takeIf { it.status == WeekSource.Loaded }
        val loading = before?.copy(stale = false) ?: ServerWeek(emptyList(), WeekSource.Loading)
        return copy(weeks = weeks + (span to loading))
    }

    /** Back in the foreground: every loaded week is due to load again. */
    fun returned(): WeeksState = copy(
        weeks = weeks.mapValues { (_, week) ->
            if (week.status == WeekSource.Loaded) week.copy(stale = true) else week
        },
        backgrounded = false,
    )

    /** The entry [id] as the server sent it, in any week. */
    fun entry(id: EntryId): TimeEntry? =
        weeks.values.firstNotNullOfOrNull { week -> week.entries.firstOrNull { it.id == id } }
}

/** True while the week loads, or once it loaded and nothing has made it stale. */
private fun ServerWeek.isCurrent(): Boolean = status == WeekSource.Loading || (status == WeekSource.Loaded && !stale)
