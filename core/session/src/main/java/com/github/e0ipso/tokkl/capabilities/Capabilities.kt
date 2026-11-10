package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.model.Capabilities

/** The app CSV routes, separate from Toggl's v3 export routes and from on-screen pagination. */
enum class ReportCsvKind(val path: String) {
    SUMMARY("summary"),
    DETAILED("detailed"),
}

/**
 * The CSV routes answer POST only. A 404 or 410 from that POST is the endpoint missing.
 * Connectivity, authentication, trust and rate-limit failures are not.
 */
fun ApiError.csvEndpointAbsent(): Boolean = this is ApiError.UnexpectedStatus && status in setOf(
    HttpStatus.NOT_FOUND,
    HttpStatus.GONE,
)

fun Capabilities.supports(kind: ReportCsvKind): Boolean = when (kind) {
    ReportCsvKind.SUMMARY -> summaryCsv == true
    ReportCsvKind.DETAILED -> detailedCsv == true
}

/**
 * The flags from [me] and the [trash] probe. A probe that got no clear answer keeps the
 * trash and restore flags as they were. CSV routes are POST only, so refresh does not send
 * OPTIONS: that 404 is not absence. Tokkl offers Share until a POST proves one kind is gone,
 * and a recorded false stays false across later refreshes. The same holds for [timerState]
 * and its probe. [allDay] follows the CSV rule: offered on Tokkl until a time entry list
 * proves it gone.
 */
internal fun Capabilities.updatedWith(me: Me, trash: TrashProbe, timer: TimerStateProbe): Capabilities {
    val hasTrash = when (trash) {
        TrashProbe.PRESENT -> true
        TrashProbe.ABSENT -> false
        TrashProbe.UNKNOWN -> this.trash
    }
    val tokkl = me.role != null
    return Capabilities(
        trash = hasTrash,
        restore = if (trash == TrashProbe.UNKNOWN) restore else hasTrash,
        favorites = tokkl,
        timeResolution = me.timeResolutionMinutes != null,
        negatedFilters = tokkl,
        summaryCsv = offeredUntilGone(tokkl, summaryCsv),
        detailedCsv = offeredUntilGone(tokkl, detailedCsv),
        timerState = when (timer) {
            is TimerStateProbe.Present -> true
            TimerStateProbe.Absent -> false
            TimerStateProbe.Unknown -> timerState
        },
        allDay = offeredUntilGone(tokkl, allDay),
    )
}

/**
 * A feature Tokkl offers until a request proves it gone. [previous] false is that proof, such as
 * a CSV POST's 404 or 410 or a time entry list without `all_day`, or Toggl Track, and stays
 * false. Null and true become true on Tokkl. Sign-out forgets the flags, so the next account is
 * judged again.
 */
private fun offeredUntilGone(tokkl: Boolean, previous: Boolean?): Boolean? = when {
    !tokkl -> false
    previous == false -> false
    else -> true
}

/** What `GET /api/app/trash` said about the trash. */
internal enum class TrashProbe {
    /** It answered with a list. */
    PRESENT,

    /** 404, or a 2xx that is not a list, so the server has no trash the app can read. */
    ABSENT,

    /** No clear answer: no network, a refused trust check, 403, 429 or another status. */
    UNKNOWN,
}

/** Sends `GET /api/app/trash` once. */
internal suspend fun probeTrash(api: TokklApi): TrashProbe = when (val result = api.trash.probe()) {
    is ApiResult.Success -> TrashProbe.PRESENT

    is ApiResult.Failure -> when (val error = result.error) {
        is ApiError.UnexpectedStatus -> if (error.status ==
            HttpStatus.NOT_FOUND
        ) {
            TrashProbe.ABSENT
        } else {
            TrashProbe.UNKNOWN
        }

        is ApiError.InvalidResponse -> TrashProbe.ABSENT

        else -> TrashProbe.UNKNOWN
    }
}

/** What `GET /api/app/timer` said about the server-owned timer. */
internal sealed interface TimerStateProbe {
    data class Present(val state: TimerState) : TimerStateProbe

    /** 404, or a 2xx that is not a timer state, so the server has no timer API the app can read. */
    data object Absent : TimerStateProbe

    /** No clear answer: no network, a refused trust check, 403, 429 or another status. */
    data object Unknown : TimerStateProbe
}

/** Sends `GET /api/app/timer` once. */
internal suspend fun probeTimerState(api: TokklApi): TimerStateProbe = when (val result = api.timer.state()) {
    is ApiResult.Success -> TimerStateProbe.Present(result.value)

    is ApiResult.Failure -> when (val error = result.error) {
        is ApiError.UnexpectedStatus -> if (error.status ==
            HttpStatus.NOT_FOUND
        ) {
            TimerStateProbe.Absent
        } else {
            TimerStateProbe.Unknown
        }

        is ApiError.InvalidResponse -> TimerStateProbe.Absent

        else -> TimerStateProbe.Unknown
    }
}
