package com.github.e0ipso.tokkl.history

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.entries.OlderDays
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.LocalDate
import java.time.ZoneId

/** What the history shows. */
@Immutable
sealed interface HistoryState {

    /** The cache or the account settings are not read yet. */
    data object Loading : HistoryState

    /** The phone cannot resolve the account's timezone, [timeZone], so it cannot tell the days apart. */
    data class NoAccountTime(val timeZone: String) : HistoryState

    /**
     * The [days] with entries, newest first, as of [today] in the account's timezone [zone], and
     * what comes after them.
     */
    data class Shown(
        val days: List<HistoryDay>,
        val today: LocalDate,
        val zone: ZoneId,
        val settings: AccountSettings,
        val older: OlderDays,
    ) : HistoryState
}
