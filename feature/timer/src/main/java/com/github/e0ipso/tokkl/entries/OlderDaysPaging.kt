package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The history's older days, which [OlderPages] reads from the server [OlderPages.PAGE_DAYS] at a
 * time when [loadOlder] is called. On Tokkl, `GET /api/app/bounds` tells where the account's
 * history starts, so the list knows when no older day has entries; a server without it, such as
 * Toggl Track, gets pages until one comes back empty, and then more only on a tap.
 *
 * It is the one owner of [state], which changes only through [MutableStateFlow.update] in its
 * commands, so a call on the main thread and an answer on [scope] never lose each other's change.
 *
 * @param olderPages the server's days before the cache. Runs on [io].
 */
internal class OlderDaysPaging(
    private val olderPages: OlderPages,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val mutableState = MutableStateFlow(OlderDaysState())

    val state: StateFlow<OlderDaysState> = mutableState.asStateFlow()

    /**
     * Loads the [OlderPages.PAGE_DAYS] days before the oldest day loaded, or before the cached two
     * weeks at first. Does nothing while a page loads, once the server has no older entries, or
     * without an account timezone. After a failure it tries the same days again. A failure for
     * want of an answer while the network came back tries once more for that newer event.
     */
    fun loadOlder() {
        val time = accountSettings.value?.time(clock) ?: return
        val asked = mutableState.getAndUpdate { if (it.canLoad) it.copy(status = OlderDays.Loading) else it }
        if (!asked.canLoad) return
        val before = asked.from ?: firstCachedDay(time)
        scope.launch {
            val (page, read) = withContext(io) { olderPages.page(time, before, asked.bounds) }
            val after = mutableState.updateAndGet { current ->
                if (current.generation == asked.generation) current.after(page, read, before) else current
            }
            // The request may still have used the old route when an online update arrived.
            // Retry that failure once for the newer event; a successful page never pages on.
            val retry = after.generation == asked.generation && page is OlderPage.NotLoaded &&
                page.status == OlderDays.Unreachable && after.reconnects != asked.reconnects
            if (retry) loadOlder()
        }
    }

    /** The network came back: older days that failed for want of an answer load again. */
    fun reconnected() {
        val now = mutableState.updateAndGet { it.copy(reconnects = it.reconnects + 1) }
        if (now.status == OlderDays.Unreachable) loadOlder()
    }

    /** Drops the older days, so an answer for the account before changes nothing. */
    fun forget() = mutableState.update { OlderDaysState(generation = it.generation + 1) }
}

/**
 * The older days loaded so far: their [entries], from the day [from] on, and [status], what comes
 * next. They end the day before [until], the cache's first day when the first page loaded or the
 * server said no entry comes before it, and null until then. [bounds] is where the account's
 * history starts, once the server has said or shown that it cannot. [reconnects] counts the online
 * updates, and [generation] the accounts whose older days were dropped.
 */
internal data class OlderDaysState(
    val entries: List<TimeEntry> = emptyList(),
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val status: OlderDays = OlderDays.More(auto = true),
    val bounds: HistoryBounds? = null,
    val reconnects: Long = 0,
    val generation: Int = 0,
) {
    val canLoad: Boolean get() = status != OlderDays.Loading && status != OlderDays.End

    /** These days after [page], the days before [before], with the bounds [read] on the way. */
    fun after(page: OlderPage, read: HistoryBounds?, before: LocalDate): OlderDaysState = when (page) {
        is OlderPage.Loaded -> copy(
            entries = entries + page.entries,
            from = page.from,
            until = until ?: before,
            status = page.status,
            bounds = read,
        )

        is OlderPage.NotLoaded -> copy(
            // The server's word that no entry comes before the cache fixes where the older days end too.
            until = until ?: before.takeIf { page.status == OlderDays.End },
            status = page.status,
            bounds = read,
        )
    }
}

/** The first day of the cached two weeks, today and the 13 days before it. */
internal fun firstCachedDay(time: AccountTime): LocalDate = time.today().minusDays(HistoryWindow.DAYS - 1)
