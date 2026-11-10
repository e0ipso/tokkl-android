package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.entryfields.noTimeEntry
import com.github.e0ipso.tokkl.entryfields.resolveRange
import com.github.e0ipso.tokkl.entryfields.snappedSeconds
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.MAX_ALL_DAY_SECONDS
import com.github.e0ipso.tokkl.time.NoTimeDuration
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.isTimeResolution
import com.github.e0ipso.tokkl.time.noTimeDuration
import com.github.e0ipso.tokkl.time.parseDuration
import com.github.e0ipso.tokkl.time.parseTimeOfDay
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant

/** The longest entry Tokkl and Toggl store: 999 hours, MAX_DURATION_SECONDS in ../tokkl/packages/common/duration.ts. */
private const val MAX_ENTRY_SECONDS = 999L * 3600

/**
 * The span to save, or the mistakes that keep [form] from saving. The start must be read. A
 * duration that differs from the derived one sets the stop, and otherwise the end does, read
 * after the start. A field left as it opened keeps the entry's instant.
 */
internal fun check(form: EntryDraft, settings: AccountSettings, time: AccountTime, clock: Clock): CheckedTimes {
    if (form.noTimes) return checkNoTimes(form, settings, time)
    val unreadable = if (form.target == DraftTarget.New) TimeMistake.MANUAL_TIMES else TimeMistake.TIMES
    val start = form.startInstant(settings, time, clock) ?: return CheckedTimes.Invalid(TimeField.START, unreadable)
    if (form.target is DraftTarget.Running) return CheckedTimes.Valid(EntryTiming.Running(start))
    val durationChanged = form.durationChanged()
    val spanField = if (durationChanged) TimeField.DURATION else TimeField.END
    val stop = stopOf(form, start, durationChanged, settings, time, clock)
        ?: return CheckedTimes.Invalid(spanField, if (durationChanged) TimeMistake.DURATION else unreadable)
    return checkSpan(form, start, stop, spanField, time)
}

/** The stop [form] sets: from its duration when [durationChanged], else from its end, or null when unreadable. */
private fun stopOf(
    form: EntryDraft,
    start: Instant,
    durationChanged: Boolean,
    settings: AccountSettings,
    time: AccountTime,
    clock: Clock,
): Instant? = when {
    durationChanged -> (form.durationSeconds ?: parseDuration(form.duration))?.let { start.plusSecondsOrNull(it) }
    form.target is DraftTarget.Edit && !form.endChanged() -> form.target.stop
    else -> resolveRange(form.date, form.start, form.end, time, settings.twelveHour, nowMinutes(time, clock))?.stop
}

/** Whether the server would keep the span from [start] to [stop], with the mistake under [spanField] if not. */
private fun checkSpan(
    form: EntryDraft,
    start: Instant,
    stop: Instant,
    spanField: TimeField,
    time: AccountTime,
): CheckedTimes {
    if (stop.epochSecond < start.epochSecond) return CheckedTimes.Invalid(spanField, TimeMistake.END_BEFORE_START)
    val predicted = time.snapUnsent(start, stop)
    // Tokkl permits an unchanged-time edit of a stored legacy short/zero-length entry.
    val target = form.target as? DraftTarget.Edit
    val unchanged = target != null && target.entryId.value > 0 && start == target.start && stop == target.stop
    if (predicted == null && !unchanged) return CheckedTimes.Invalid(spanField, TimeMistake.TOO_SHORT)
    val seconds =
        predicted?.let { it.stop.epochSecond - it.start.epochSecond } ?: (stop.epochSecond - start.epochSecond)
    return if (seconds > MAX_ENTRY_SECONDS) {
        CheckedTimes.Invalid(spanField, TimeMistake.TOO_LONG)
    } else {
        CheckedTimes.Valid(EntryTiming.Stopped(start, stop))
    }
}

/**
 * The no-time entry [form] saves: midnight on its date in the account's timezone, and the
 * duration the server will store, as the web app's noTimeDuration.ts reads it. Only the
 * duration can be wrong.
 */
private fun checkNoTimes(form: EntryDraft, settings: AccountSettings, time: AccountTime): CheckedTimes {
    // A no-time entry saved with its day and duration as they opened keeps its stored span,
    // as the web app's noTimeEdit.ts does, so renaming one off the grid never rounds it.
    val target = form.target as? DraftTarget.Edit
    if (target != null && target.keepsNoTime(form)) {
        return CheckedTimes.Valid(
            EntryTiming.AllDay(form.date, target.start, Duration.between(target.start, target.stop)),
        )
    }
    val read = noTimeDuration(form.duration, settings.timeResolutionMinutes)
    val seconds = read.seconds
    if (read.error != null || seconds == null) {
        return CheckedTimes.Invalid(TimeField.DURATION, read.mistake(settings.timeResolutionMinutes))
    }
    return CheckedTimes.Valid(time.noTimeEntry(form.date, seconds))
}

/** The start [EntryDraft.start] means: the entry's own while the date and the start are as they opened. */
private fun EntryDraft.startInstant(settings: AccountSettings, time: AccountTime, clock: Clock): Instant? {
    val minutes = parseTimeOfDay(
        start,
        twelveHour = settings.twelveHour,
        reference = nowMinutes(time, clock),
    ) ?: return null
    return when {
        target is DraftTarget.Edit && !startChanged() -> target.start
        target is DraftTarget.Running && !startChanged() -> target.start
        else -> time.instantAt(date, minutes)
    }
}

private fun nowMinutes(time: AccountTime, clock: Clock): Int = time.minutesOfDay(clock.instant())

/** The duration of the span the start and the end give, when both can be read and one changed. */
internal fun EntryDraft.followTimes(settings: AccountSettings, time: AccountTime, clock: Clock): EntryDraft {
    val range = resolveRange(date, start, end, time, settings.twelveHour, nowMinutes(time, clock))
    if (range == null || !(startChanged() || endChanged())) return this
    val text = formatDuration(snappedSeconds(range.start, range.stop, time))
    return copy(duration = text, syncedDuration = text, durationSeconds = null)
}

/** The end the start and a changed duration give, with the duration the server will store. */
internal fun EntryDraft.followDuration(settings: AccountSettings, time: AccountTime, clock: Clock): EntryDraft {
    if (!durationChanged()) return this
    val seconds = durationSeconds ?: parseDuration(duration) ?: return this
    val start = startInstant(settings, time, clock) ?: return this
    val stop = start.plusSecondsOrNull(seconds) ?: return this
    return copy(
        end = settings.formatTime(stop, time.zone),
        duration = formatDuration(snappedSeconds(start, stop, time)),
        durationSeconds = seconds,
    )
}

/** The no-time duration as the server will store it, when the server takes it. */
internal fun EntryDraft.withStoredNoTimeDuration(settings: AccountSettings): EntryDraft {
    val read = noTimeDuration(duration, settings.timeResolutionMinutes)
    val seconds = read.seconds?.takeIf { read.error == null } ?: return this
    return copy(duration = formatDuration(seconds))
}

/**
 * Times for a no-time entry: midnight on its date, and an end its duration later. Where that
 * end reads as another span, as midnight again after 24 hours, the duration sets the stop,
 * as after [EntryFormViewModel.durationDone].
 */
internal fun EntryDraft.timesFromMidnight(settings: AccountSettings, time: AccountTime, clock: Clock): EntryDraft {
    val start = time.instantAt(date, 0)
    val startText = settings.formatTime(start, time.zone)
    val seconds = parseDuration(duration)
    val stop = seconds?.let { start.plusSecondsOrNull(it) } ?: return copy(noTimes = false, start = startText, end = "")
    val endText = settings.formatTime(stop, time.zone)
    val text = formatDuration(snappedSeconds(start, stop, time))
    val endSaysIt = resolveRange(
        date,
        startText,
        endText,
        time,
        settings.twelveHour,
        nowMinutes(time, clock),
    )?.stop == stop
    return copy(
        noTimes = false,
        start = startText,
        end = endText,
        duration = text,
        syncedDuration = text,
        durationSeconds = seconds.takeUnless { endSaysIt },
    )
}

private fun Instant.plusSecondsOrNull(seconds: Long): Instant? = try {
    plusSeconds(seconds)
} catch (_: ArithmeticException) {
    null
} catch (_: DateTimeException) {
    null
}

/**
 * The mistake for a duration [noTimeDuration] refused, by its rules: one it cannot read, one over a
 * day, or one that rounds to zero at the time resolution [minutes]. Without a resolution a zero
 * gets the message for a duration it cannot read, as in the web app.
 */
private fun NoTimeDuration.mistake(minutes: Double?): TimeMistake {
    val resolution = minutes?.takeIf(::isTimeResolution)
    val seconds = seconds ?: return TimeMistake.NO_TIME_DURATION
    return when {
        seconds > MAX_ALL_DAY_SECONDS -> TimeMistake.NO_TIME_TOO_LONG
        resolution != null -> TimeMistake.NoTimeRoundsToZero(resolution.toInt())
        else -> TimeMistake.NO_TIME_DURATION
    }
}

private fun DraftTarget.Edit.keepsNoTime(form: EntryDraft): Boolean =
    noTime && form.date == date && !form.durationChanged()
