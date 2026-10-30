package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.cache.countsFrom
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.favorites.fields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.timer.countedTimerEntries
import com.github.e0ipso.tokkl.timer.timerEntrySeconds
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What the home screen widget shows (docs/PRD.md N3). It comes from the phone alone: the cache's
 * running entry and the server's timer state the phone last received, so the widget shows a Start
 * or Stop at once, also offline.
 */
sealed interface WidgetState {

    /** No account on the phone, or none with a workspace to start in: the widget opens the app. */
    data object NoAccount : WidgetState

    /**
     * @param running the running entry, null when no timer runs.
     * @param favorites in the user's order, empty when the server keeps no favorites.
     * @param paused the paused timer (docs/PRD.md T1), null unless the server's pause chain is paused.
     * @param day the cached account-local day, null when the account's zone cannot be resolved.
     */
    data class Ready(
        val running: WidgetRunning?,
        val favorites: List<WidgetFavorite>,
        val paused: WidgetPaused? = null,
        val day: WidgetDayReport? = null,
    ) : WidgetState
}

/**
 * The running entry as the widget shows it.
 *
 * @param entryId the id the cache holds the entry under, which Stop stops.
 * @param description as typed; blank when the entry has none.
 * @param project null when the entry has no project or the cache does not know it.
 * @param start the instant the elapsed time counts from: the entry's start, moved by the server's
 *   clock offset for a resumed clone, which the server started.
 * @param accumulatedSeconds what a pause chain stored before this entry, which the elapsed time
 *   adds to the time since [start].
 * @param target what Pause and Stop act on.
 * @param pausable whether Pause shows, which needs the server's timer API and no Pomodoro session.
 */
data class WidgetRunning(
    val entryId: Long,
    val description: String,
    val project: String?,
    val task: String?,
    val start: Instant,
    val accumulatedSeconds: Long,
    val target: TimerTarget,
    val pausable: Boolean,
    val projectColor: String? = null,
)

/** The paused timer: what Resume starts, the paused total [seconds], and what Resume and Stop act on. */
data class WidgetPaused(
    val description: String,
    val project: String?,
    val task: String?,
    val seconds: Long,
    val target: TimerTarget,
    val projectColor: String? = null,
)

/** One favorite: what a tap starts, and the names of its project and task. */
data class WidgetFavorite(val fields: EntryFields, val project: String?, val task: String?)

/**
 * The account-local start day of cached entries. Only the stopped seconds are stored here;
 * the running start lets a launcher Chronometer tick without changing the redraw key. Pass
 * [stoppedSeconds] as its accumulated time, never the pause chain's cumulative display total.
 */
data class WidgetDayReport(
    val date: LocalDate,
    val stoppedSeconds: Long,
    val stoppedBillableSeconds: Long,
    val entryCount: Int,
    val projects: List<WidgetDayProject>,
    val running: WidgetDayRunning?,
    val durationFormat: DurationFormat,
) {
    fun totalSeconds(now: Instant): Long = stoppedSeconds + (running?.seconds(now) ?: 0)

    fun billableSeconds(now: Instant): Long =
        stoppedBillableSeconds + (running?.takeIf { it.billable }?.seconds(now) ?: 0)

    fun formattedTotal(now: Instant): String = formatDuration(totalSeconds(now), durationFormat)

    fun formattedBillable(now: Instant): String = formatDuration(billableSeconds(now), durationFormat)
}

/** Only this segment's elapsed time, excluding the pause chain already counted in stopped rows. */
data class WidgetDayRunning(val entryId: Long, val start: Instant, val billable: Boolean) {
    fun seconds(now: Instant): Long = timerEntrySeconds(start, null, now)
}

/** Project rows are sorted by stopped seconds. A null [projectId] is the unassigned group. */
data class WidgetDayProject(
    val projectId: Long?,
    val project: String?,
    val projectColor: String?,
    val stoppedSeconds: Long,
    val formattedStopped: String,
    val running: Boolean,
)

/**
 * The widget for an account whose default workspace is [workspaceId], null when the phone has no
 * account, with what the timer [controls] show and the cached [favorites]. [favoritesAvailable]
 * is the favorites capability: without it, as on Toggl Track, the widget shows none.
 * [entries], [settings], [now] and [serverIdOf] supply the day report using the Timer page's
 * start-day rule. The clock snapshot selects the day; it is not part of the redraw key.
 */
fun widgetState(
    workspaceId: Long?,
    controls: TimerControls,
    favorites: List<CachedFavorite>,
    favoritesAvailable: Boolean,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    entries: List<CachedTimeEntry> = emptyList(),
    settings: AccountSettings? = null,
    now: Instant = Clock.systemUTC().instant(),
    serverIdOf: Map<Long, Long> = emptyMap(),
): WidgetState {
    if (workspaceId == null) return WidgetState.NoAccount
    val shown = (controls as? TimerControls.Running)?.let { running ->
        val entry = running.entry
        val labels = entryLabels(entry.fields(), projects, tasks)
        WidgetRunning(
            entryId = entry.id,
            description = entry.description,
            project = labels.project,
            task = labels.task,
            start = running.countsFrom,
            accumulatedSeconds = running.accumulatedSeconds,
            target = running.target,
            pausable = running.pausable,
            projectColor = projects.firstOrNull { it.id == entry.projectId }?.color,
        )
    }
    val paused = (controls as? TimerControls.Paused)?.let { chain ->
        val labels = entryLabels(chain.fields, projects, tasks)
        WidgetPaused(
            chain.fields.description, labels.project, labels.task, chain.seconds, chain.target,
            projectColor = projects.firstOrNull { it.id == chain.fields.projectId }?.color,
        )
    }
    val starts = if (!favoritesAvailable) {
        emptyList()
    } else {
        favorites.sortedBy { it.position }.map { favorite ->
            val fields = favorite.fields()
            val labels = entryLabels(fields, projects, tasks)
            WidgetFavorite(fields, labels.project, labels.task)
        }
    }
    val day = settings?.time()?.let { time ->
        val today = time.local(now).toLocalDate()
        val rows = countedTimerEntries(entries, time, now, serverIdOf).filter { it.day == today }
        val stopped = rows.filter { it.entry.stop != null }
        // Use the effective cached row, including a local replacement of the ID the controls
        // already show. Its elapsed time belongs to the day even across that remap emission.
        val active = rows.firstOrNull { it.entry.stop == null }?.entry
        val byId = projects.associateBy { it.id }
        val grouped = rows.groupBy { it.entry.projectId }
        val reportProjects = grouped.map { (projectId, segments) ->
            val project = byId[projectId]
            val seconds = segments.filter { it.entry.stop != null }.sumOf { it.seconds }
            WidgetDayProject(
                projectId = projectId,
                project = project?.name,
                projectColor = project?.color,
                stoppedSeconds = seconds,
                formattedStopped = settings.formatDurationWithoutSeconds(seconds),
                running = segments.any { it.entry.id == active?.id },
            )
        }.sortedWith(compareByDescending<WidgetDayProject> { it.stoppedSeconds }.thenBy { it.projectId })
        WidgetDayReport(
            date = today,
            stoppedSeconds = stopped.sumOf { it.seconds },
            stoppedBillableSeconds = stopped.filter { it.entry.billable }.sumOf { it.seconds },
            entryCount = rows.size,
            projects = reportProjects,
            running = active?.let { WidgetDayRunning(it.id, it.start, it.billable) },
            durationFormat = settings.durationFormat,
        )
    }
    return WidgetState.Ready(shown, starts, paused, day)
}

/**
 * How many of [count] favorites fit in a widget [heightDp] tall, under the timer row. Each takes
 * a row of [WidgetLayout.FAVORITE_ROW_DP], the size of a touch target.
 */
fun favoritesThatFit(heightDp: Float, count: Int, fontScale: Float = 1f): Int {
    val room = heightDp - 2 * WidgetLayout.PADDING_DP - WidgetLayout.timerRowHeight(fontScale)
    if (room <= 0f) return 0
    return (room / WidgetLayout.favoriteRowHeight(fontScale)).toInt().coerceIn(0, count)
}

/**
 * The base of a Chronometer that counts from [start], after [accumulatedSeconds] a pause chain
 * stored before it: the `SystemClock.elapsedRealtime()` at which it read zero, given that [now]
 * on the wall clock is [elapsedRealtime] on that clock. A start after [now], from a clock that
 * runs late, counts from the accumulated time.
 */
fun chronometerBase(start: Instant, now: Instant, elapsedRealtime: Long, accumulatedSeconds: Long = 0): Long =
    elapsedRealtime - Duration.between(start, now).toMillis().coerceAtLeast(0) - Duration.ofSeconds(accumulatedSeconds).toMillis()

/**
 * Changes with everything the widget shows, so the widget updates only when it would look
 * different. A SHA-256 hash, so the store that keeps it holds no description or name.
 */
fun WidgetState.key(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toString().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/** The widget's sizes, in dp, which [favoritesThatFit] and the layout share. */
object WidgetLayout {
    // Approved 298×222 and 375×338 cards, with room for launcher padding.
    const val MEDIUM_WIDTH_DP = 260f
    const val MEDIUM_HEIGHT_DP = 200f
    const val LARGE_WIDTH_DP = 340f
    const val LARGE_HEIGHT_DP = 310f
    const val MAX_SEGMENTS = 5

    fun clockRowHeight(large: Boolean, scale: Float): Float =
        if (large) 46f + 18f * (scale.coerceAtLeast(1f) - 1f) else 28f + 20f * (scale.coerceAtLeast(1f) - 1f)
    fun reportHeadingHeight(scale: Float): Float = 22f + 8f * (scale.coerceAtLeast(1f) - 1f)
    fun statsHeight(scale: Float): Float = 44f + 20f * (scale.coerceAtLeast(1f) - 1f)
    fun todayRowHeight(scale: Float): Float = 26f + 14f * (scale.coerceAtLeast(1f) - 1f)
    const val PADDING_DP = 12f
    const val TIMER_ROW_DP = 80f
    const val FAVORITE_ROW_DP = 48f

    fun buttonHeight(fontScale: Float): Float = 48f + 16f * (fontScale.coerceAtLeast(1f) - 1f)

    fun timerRowHeight(fontScale: Float): Float = buttonHeight(fontScale) + (TIMER_ROW_DP - 48f) * fontScale.coerceAtLeast(1f)

    fun favoriteRowHeight(fontScale: Float): Float = FAVORITE_ROW_DP * fontScale.coerceAtLeast(1f)
}
