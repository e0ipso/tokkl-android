package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The tab on screen and the filters every tab's report applies, chosen together. */
@Serializable
data class ReportChoice(val tab: ReportTab = ReportTab.SUMMARY, val filters: ReportFilters = ReportFilters())

/**
 * What Reports keeps between its visits: the tab and the filters, as one [ReportChoice]. It lives
 * in the signed-in session's store, so the next visit opens on them, while the range and the
 * report live in the page's own [ReportsViewModel] and go with it. A widget's day tap asks here for
 * today's summary, before or while the page shows. The page keeps the choice in its saved state
 * too, and a new process that restores the page brings it back here ([restore]). Only the
 * functions here change the choice, each in one step.
 */
class ReportChoices : ViewModel() {

    private val mutableChosen = MutableStateFlow(ReportChoice())

    /** The tab and the filters on screen. */
    val chosen: StateFlow<ReportChoice> = mutableChosen.asStateFlow()

    /** A widget's day tap asked for today's summary, until the page takes it ([takeToday]). */
    private val today = MutableStateFlow(false)

    /** Whether a widget's day tap waits for the page, as it changes. */
    internal val todayAsked: StateFlow<Boolean> = today.asStateFlow()

    /** Shows [tab], with the filters as they are. Returns whether the choice changed. */
    internal fun selectTab(tab: ReportTab): Boolean = change { it.copy(tab = tab) }

    /** Applies [filters] to every tab. Returns whether the choice changed. */
    internal fun applyFilters(filters: ReportFilters): Boolean = change { it.copy(filters = filters) }

    /** Asks for the widget's day report: the summary of today, with no filters. */
    fun openToday() {
        // Weekly expands a one-day range to a week. Widget totals always describe one day.
        mutableChosen.value = ReportChoice()
        today.value = true
    }

    /** Takes the widget's ask, so the page shows today's summary once. Returns whether one waited. */
    internal fun takeToday(): Boolean = today.compareAndSet(expect = true, update = false)

    /** The choice as the page keeps it in its saved state. */
    internal fun saved(): String = json.encodeToString(ReportChoice.serializer(), chosen.value)

    /**
     * Brings back the choice a page kept in its saved state, [text], after a new process restored
     * the page. Only while nothing has chosen in this process: a widget's day tap that waits, or a
     * choice made since, wins. Text an older version saved in a shape this one cannot read brings
     * nothing back.
     */
    internal fun restore(text: String) {
        if (today.value || chosen.value != ReportChoice()) return
        val saved = try {
            json.decodeFromString(ReportChoice.serializer(), text)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        mutableChosen.compareAndSet(ReportChoice(), saved)
    }

    private fun change(choose: (ReportChoice) -> ReportChoice): Boolean {
        val before = mutableChosen.getAndUpdate(choose)
        return choose(before) != before
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
