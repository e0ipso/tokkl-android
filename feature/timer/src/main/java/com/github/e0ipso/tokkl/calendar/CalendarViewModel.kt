package com.github.e0ipso.tokkl.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entries.CalendarWeeks
import com.github.e0ipso.tokkl.entries.WeekEntries
import com.github.e0ipso.tokkl.entryfields.TimeRange
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

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
 * memory while the view model does, and nothing of it is written to the phone. Nothing loads on a
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
 * [entries], the session's one owner of the entries, loads the server's weeks, keeps them and
 * merges them with the cache's; this model picks the week to show and lays out its blocks.
 *
 * Call [step], [showToday], [loadWeek], [create], [move] and [resize] on the main thread.
 *
 * @param computation where the week's blocks are laid out from the entries.
 * @param clock where today comes from, in the account's timezone.
 * @param allDayOffered whether the server stores no-time entries (`Capabilities.allDay == true`),
 *   which [CalendarState.Shown.allDayOffered] reports for the All day row.
 */
class CalendarViewModel(
    private val entries: CalendarWeeks,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    scope: CoroutineScope,
    computation: CoroutineContext,
    private val clock: Clock,
    allDayOffered: Flow<Boolean>,
) : ViewModel(scope) {

    /** The chosen day, or null to follow today. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /**
     * How many times the app came back to the foreground or the network came back; the calendar
     * on screen loads its week again on each when the week is due.
     */
    val foregrounds: StateFlow<Int> = entries.foregrounds

    val state: StateFlow<CalendarState> = combine(
        entries.week(selected),
        projects,
        tasks,
        allDayOffered,
    ) { week, projectRows, taskRows, offered ->
        when (week) {
            WeekEntries.Loading -> CalendarState.Loading

            is WeekEntries.NoAccountTime -> CalendarState.NoAccountTime(week.timeZone)

            is WeekEntries.Shown -> CalendarState.Shown(
                selected = week.shown,
                weekStart = week.weekStart,
                today = week.today,
                zone = week.time.zone,
                settings = week.settings,
                entries = weekEntries(week.current, week.server, projectRows, taskRows, week.time, week.weekStart),
                week = week.source,
                allDayOffered = offered,
            )
        }
    }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CalendarState.Loading)

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
     * Loads the server's part of the week from [weekStart], as [CalendarWeeks.loadWeek] does.
     * Showing a week is the event that loads it, and so are Try again after a failure and coming
     * back to the foreground.
     */
    fun loadWeek(weekStart: LocalDate) = entries.loadWeek(weekStart, selected)

    /**
     * The entry [id] as the server sent it for a week the phone does not keep, or null. The entry
     * form opens on this copy of an entry the cache lacks.
     */
    fun serverEntry(id: EntryId): TimeEntry? = entries.weekEntry(id)

    /**
     * Queues the entry that a drag from clock minute [fromMinute] to [toMinute] on [day] makes, in
     * the account's default workspace and with no description, and calls [onCreated] with its id
     * so the entry form can open on it, as the web app's editor opens after a drag. A drag that
     * never grew makes nothing.
     */
    fun create(day: LocalDate, fromMinute: Int, toMinute: Int, onCreated: (EntryId) -> Unit = {}) {
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val workspaceId = settings.defaultWorkspaceId ?: return
        val range = createdRange(day, fromMinute, toMinute, dragStep(settings.dragResolution), time) ?: return
        if (range.stop < range.start || time.snapUnsent(range.start, range.stop) == null) return
        val timing = EntryTiming.Stopped(range.start, range.stop)
        viewModelScope.launch { onCreated(changes.create(workspaceId, EntryFields(), timing)) }
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
        // A no-time entry has no time to move or resize, and a running one no end.
        val stop = (entry.timing as? EntryTiming.Stopped)?.stop ?: return
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val span = range(stop, time, dragStep(settings.dragResolution))
        if (span.stop < span.start || time.snapUnsent(span.start, span.stop) == null) return
        viewModelScope.launch { entries.moveWeekEntry(entry.id, span.start, span.stop) }
    }

    companion object {
        /** How long the state keeps following the cache after the last screen stops reading it. */
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
