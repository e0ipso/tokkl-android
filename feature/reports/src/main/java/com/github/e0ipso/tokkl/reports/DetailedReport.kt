package com.github.e0ipso.tokkl.reports

import java.time.ZonedDateTime

/**
 * One time entry of the detailed report, as the server sends it after rounding. [start] and
 * [stop] are in the account's timezone; [stop] is null when the server sent none. [projectName]
 * is null for an entry without a project.
 */
data class DetailedEntry(
    val id: Long?,
    val description: String,
    val projectName: String?,
    val projectColor: String?,
    val taskName: String?,
    val start: ZonedDateTime,
    val stop: ZonedDateTime?,
    val seconds: Long,
)

/**
 * One page of the detailed search. [nextRow] is the row the server said the next page starts on,
 * from `X-Next-Row-Number`, or null after the last page.
 */
data class DetailedPage(val entries: List<DetailedEntry>, val nextRow: Int?)

/**
 * What the detailed tab shows: the pages loaded so far, newest entry first, as the web app's
 * detailed report sorts them by default. [nextRow] is where the next page starts, null once the
 * server sent the last one, and [loadingMore] is true while that page is on its way.
 */
data class DetailedReport(val entries: List<DetailedEntry>, val nextRow: Int?, val loadingMore: Boolean = false) : Report
