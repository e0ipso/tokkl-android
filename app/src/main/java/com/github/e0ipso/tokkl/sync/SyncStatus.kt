package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the status element above the bottom bar says (docs/PRD.md O5).
 *
 * @property unsent how many changes the queue holds that the server has not received.
 * @property lastAnswered when the server last gave a 2xx answer, or null when it has not since
 *   sign-in.
 * @property needsAttention how many changes the server refused, which the "needs attention" list
 *   of TKA-0073 holds. The queue keeps none apart yet, so it is 0 until that list lands.
 * @property settings the account's settings, for the timezone and the clock format, or null
 *   before they load.
 */
data class SyncStatus(
    val unsent: Int,
    val lastAnswered: Instant?,
    val needsAttention: Int = 0,
    val settings: AccountSettings? = null,
) {
    /** Something waits to reach the server, so the status shows. */
    val pending: Boolean get() = unsent > 0 || needsAttention > 0
}

/**
 * The status to show, or null while nothing waits to reach the server. A change sent at once,
 * as online, never shows it: the status shows once changes have waited [grace] without a break,
 * and from then on it follows every change to the count and the time at once. It goes the moment
 * the queue empties.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun shownSyncStatus(
    unsent: Flow<Int>,
    lastAnswered: Flow<Instant?>,
    settings: Flow<AccountSettings?>,
    needsAttention: Flow<Int> = flowOf(0),
    grace: Duration = SHOW_AFTER,
): Flow<SyncStatus?> {
    val status = combine(unsent, lastAnswered, needsAttention, settings) { count, answered, attention, account ->
        SyncStatus(count, answered, attention, account)
    }
    val waitedLongEnough = status
        .map { it.pending }
        .distinctUntilChanged()
        .transformLatest { pending ->
            if (pending) delay(grace)
            emit(pending)
        }
    return combine(waitedLongEnough, status) { shown, current -> current.takeIf { shown && it.pending } }
        .distinctUntilChanged()
}

/** How long changes wait before the status shows, longer than a send takes online. */
val SHOW_AFTER: Duration = 3.seconds

/** When the server last answered, as the status element says it. */
sealed interface LastAnsweredLabel {

    /** No 2xx answer since sign-in. */
    data object Never : LastAnsweredLabel

    /** The phone cannot resolve the account's timezone, so it shows no time. */
    data object NoAccountTime : LastAnsweredLabel

    /** At [time], the clock time in the account's zone and format, [daysAgo] days before today there. */
    data class At(val time: String, val day: LocalDate, val daysAgo: Long) : LastAnsweredLabel
}

/** [answered] in the account's timezone and clock format, as of [clock]'s today in that zone. */
fun lastAnsweredLabel(answered: Instant?, settings: AccountSettings?, clock: Clock): LastAnsweredLabel {
    if (answered == null) return LastAnsweredLabel.Never
    val time = settings?.time(clock) ?: return LastAnsweredLabel.NoAccountTime
    val day = time.local(answered).toLocalDate()
    return LastAnsweredLabel.At(
        time = settings.formatTime(answered, time.zone),
        day = day,
        daysAgo = ChronoUnit.DAYS.between(day, time.today()),
    )
}
