package com.github.e0ipso.tokkl.history

import androidx.compose.runtime.Immutable
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

/** The end of the history: the days before the ones shown. */
@Immutable
sealed interface OlderDays {

    /**
     * Older days may have entries. With [auto], they load once the end of the list shows. After a
     * page without entries from a server that does not say where its history starts, as Toggl
     * Track, only a tap loads more, so scrolling never walks back through empty weeks on its own.
     */
    data class More(val auto: Boolean) : OlderDays

    data object Loading : OlderDays

    /** The server has no entries before the days shown. */
    data object End : OlderDays

    /** No answer: the phone is offline, or the server or the proxy in front of it is down. */
    data object Unreachable : OlderDays

    /** The server refused the stored API token, or the phone could not read it. */
    data object AuthFailed : OlderDays

    /** The app sends nothing to the server until the user answers its trust prompt. */
    data object TrustRefused : OlderDays

    /** Any other answer. [status] is the HTTP status, or null when the body was not a list of entries. */
    data class Failed(val status: Int?) : OlderDays
}
