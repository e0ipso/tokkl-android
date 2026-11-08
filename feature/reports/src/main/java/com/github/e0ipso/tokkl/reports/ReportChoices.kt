package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Reports keeps between its visits: the tab and the filters. It lives in the signed-in
 * session's store, so the next visit opens on them, while the range and the report live in the
 * page's own [ReportsViewModel] and go with it. A widget's day tap asks here for today's summary,
 * before or while the page shows.
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
}
