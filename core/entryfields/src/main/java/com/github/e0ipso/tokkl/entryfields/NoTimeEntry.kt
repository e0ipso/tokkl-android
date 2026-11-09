package com.github.e0ipso.tokkl.entryfields

import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Duration
import java.time.LocalDate

/**
 * The no-time entry on [day] lasting [seconds] as the server will store it: from local midnight of
 * the day in the account's timezone, for [AccountTime.snapDuration] of the seconds, as
 * [AccountTime.noTimeSpan] gives them. [stored] is the day the timing keeps: [day], unless the entry
 * came from the server without one.
 */
fun AccountTime.noTimeEntry(day: LocalDate, seconds: Long, stored: LocalDate? = day): EntryTiming.AllDay {
    val span = noTimeSpan(day, seconds)
    return EntryTiming.AllDay(stored, span.start, Duration.between(span.start, span.stop))
}
