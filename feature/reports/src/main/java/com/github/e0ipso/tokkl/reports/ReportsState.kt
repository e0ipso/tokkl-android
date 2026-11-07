package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.HttpStatus

/** A range and the preset it came from, null for a custom range or one stepped to. */
data class RangeSelection(val range: ReportRange, val preset: RangePreset?)

/** The reports, one tab each, in the web app's order. */
enum class ReportTab {
    SUMMARY,
    DETAILED,
    WEEKLY,
}

/** What one tab shows once the server answers: a [SummaryReport], a [DetailedReport] or a [WeeklyReport]. */
sealed interface Report

/** What the reports screen shows. No state carries numbers from another range or an earlier answer. */
sealed interface ReportsState {

    /** The screen is closed, and nothing is loaded or kept. */
    data object Closed : ReportsState

    /**
     * The phone cannot turn the account's timezone into dates, so it cannot pick a range.
     * [timeZone] is the account's zone name, or null when no account settings are stored.
     */
    data class NoAccountTime(val timeZone: String?) : ReportsState

    data class Loading(val selection: RangeSelection) : ReportsState

    data class Loaded(val selection: RangeSelection, val report: Report) : ReportsState

    data class Failed(val selection: RangeSelection, val failure: ReportFailure) : ReportsState
}

/** The range this state is about, or null when there is none. */
internal fun ReportsState.selection(): RangeSelection? = when (this) {
    is ReportsState.Loading -> selection
    is ReportsState.Loaded -> selection
    is ReportsState.Failed -> selection
    ReportsState.Closed, is ReportsState.NoAccountTime -> null
}

/** Why there is no report. */
sealed interface ReportFailure {

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : ReportFailure

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : ReportFailure

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : ReportFailure

    /** Any other answer. [status] is the HTTP status, or null when the body was not a report. */
    data class Unexpected(val status: Int?) : ReportFailure
}

/** How [error] shows on the reports screen. */
internal fun reportFailureOf(error: ApiError): ReportFailure = when (error) {
    is ApiError.Network -> ReportFailure.Unreachable

    is ApiError.UnexpectedStatus -> if (error.status in PROXY_ANSWERS_SERVER_DOWN) {
        ReportFailure.Unreachable
    } else {
        ReportFailure.Unexpected(error.status)
    }

    ApiError.AuthFailed -> ReportFailure.AuthFailed

    is ApiError.TrustRefused -> ReportFailure.TrustRefused

    is ApiError.RateLimited -> ReportFailure.Unexpected(error.status)

    is ApiError.HostNotAllowed, is ApiError.InvalidResponse -> ReportFailure.Unexpected(null)
}

/** What a reverse proxy answers while the Tokkl server behind it is down. */
private val PROXY_ANSWERS_SERVER_DOWN =
    setOf(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
