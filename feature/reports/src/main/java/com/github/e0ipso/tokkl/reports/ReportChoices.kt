package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What Reports keeps between its visits: the tab and the filters. It lives in the signed-in
 * session's store, so the next visit opens on them, while the range and the report live in the
 * page's own [ReportsViewModel] and go with it. A widget's day tap asks here for today's summary,
 * before or while the page shows. The page keeps the tab and the filters in its saved state too,
 * and a new process that restores the page brings them back here ([restore]).
 */
class ReportChoices : ViewModel() {

    /** The tab on screen. */
    val tab = MutableStateFlow(ReportTab.SUMMARY)

    /** The filters every tab's report applies. */
    val filters = MutableStateFlow(ReportFilters())

    /** A widget's day tap asked for today's summary, until the page takes it ([takeToday]). */
    private val today = MutableStateFlow(false)

    /** Whether a widget's day tap waits for the page, as it changes. */
    internal val todayAsked: StateFlow<Boolean> = today.asStateFlow()

    /** Asks for the widget's day report: the summary of today, with no filters. */
    fun openToday() {
        // Weekly expands a one-day range to a week. Widget totals always describe one day.
        tab.value = ReportTab.SUMMARY
        filters.value = ReportFilters()
        today.value = true
    }

    /** Takes the widget's ask, so the page shows today's summary once. Returns whether one waited. */
    internal fun takeToday(): Boolean = today.compareAndSet(expect = true, update = false)

    /** The tab and the filters as the page keeps them in its saved state. */
    internal fun saved(): String =
        json.encodeToString(SavedChoices.serializer(), SavedChoices(tab.value, filters.value))

    /**
     * Brings back the tab and the filters a page kept in its saved state, [text], after a new
     * process restored the page. Only while nothing has chosen them in this process: a widget's
     * day tap that waits, or a choice made since, wins. Text an older version saved in a shape
     * this one cannot read brings nothing back.
     */
    internal fun restore(text: String) {
        if (today.value || tab.value != ReportTab.SUMMARY || filters.value != ReportFilters()) return
        val saved = try {
            json.decodeFromString(SavedChoices.serializer(), text)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        tab.value = saved.tab
        filters.value = saved.filters
    }

    @Serializable
    private data class SavedChoices(val tab: ReportTab, val filters: ReportFilters)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
