package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.LocalEntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.countedTimerEntries
import com.github.e0ipso.tokkl.timerstate.countsFrom
import com.github.e0ipso.tokkl.timerstate.timerEntrySeconds
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
     * @param account the name the signed-in account's timer controls carry, which Start and the
     *   templates carry too.
     */
    data class Ready(
        val running: WidgetRunning?,
        val favorites: List<WidgetFavorite>,
        val paused: WidgetPaused? = null,
        val day: WidgetDayReport? = null,
        val account: String,
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
    val entryId: EntryId,
    val description: String,
    val project: String?,
    val task: String?,
    val start: Instant,
    val accumulatedSeconds: Long,
    val target: TimerTarget.Running,
    val pausable: Boolean,
    val projectColor: String? = null,
)

/** The paused timer: what Resume starts, the paused total [seconds], and what Resume and Stop act on. */
data class WidgetPaused(
    val description: String,
    val project: String?,
    val task: String?,
    val seconds: Long,
    val target: TimerTarget.Paused,
    val projectColor: String? = null,
)

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
data class WidgetDayRunning(val entryId: EntryId, val start: Instant, val billable: Boolean) {
    fun seconds(now: Instant): Long = timerEntrySeconds(start, null, now)
}

/** Project rows are sorted by stopped seconds. A null [projectId] is the unassigned group. */
data class WidgetDayProject(
    val projectId: ProjectId?,
    val project: String?,
    val projectColor: String?,
    val stoppedSeconds: Long,
    val formattedStopped: String,
    val running: Boolean,
)

/**
 * [workspaceId] is null when the phone has no account. [favoritesAvailable] is the favorites
 * capability: without it, as on Toggl Track, the widget shows none. [entries], [settings], [now]
 * and [serverIdOf] supply the day report using the Timer page's start-day rule. The clock snapshot
 * selects the day; it is not part of the redraw key.
 */
fun widgetState(
    workspaceId: WorkspaceId?,
    account: String,
    controls: TimerControls,
    favorites: List<Favorite>,
    favoritesAvailable: Boolean,
    projects: List<Project>,
    tasks: List<Task>,
    entries: List<TimeEntry> = emptyList(),
    settings: AccountSettings? = null,
    now: Instant = Clock.systemUTC().instant(),
    serverIdOf: Map<LocalEntryId, EntryId> = emptyMap(),
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
            chain.fields.description,
            labels.project,
            labels.task,
            chain.seconds,
            chain.target,
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
    val day = settings?.let { dayReport(it, entries, projects, now, serverIdOf) }
    return WidgetState.Ready(shown, starts, paused, day, account)
}

/** The day report of [settings]' account for the day [now] falls on, or null without its time zone. */
private fun dayReport(
    settings: AccountSettings,
    entries: List<TimeEntry>,
    projects: List<Project>,
    now: Instant,
    serverIdOf: Map<LocalEntryId, EntryId>,
): WidgetDayReport? = settings.time()?.let { time ->
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
    }.sortedWith(compareByDescending<WidgetDayProject> { it.stoppedSeconds }.thenBy { it.projectId?.value })
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

/**
 * The base of a Chronometer that counts from [start], after [accumulatedSeconds] a pause chain
 * stored before it: the `SystemClock.elapsedRealtime()` at which it read zero, given that [now]
 * on the wall clock is [elapsedRealtime] on that clock. A start after [now], from a clock that
 * runs late, counts from the accumulated time.
 */
fun chronometerBase(start: Instant, now: Instant, elapsedRealtime: Long, accumulatedSeconds: Long = 0): Long =
    elapsedRealtime - Duration.between(
        start,
        now,
    ).toMillis().coerceAtLeast(0) - Duration.ofSeconds(accumulatedSeconds).toMillis()

/**
 * Changes with everything the widget shows and every target its actions carry, so the widget
 * updates only when it would look different or act on something else. Each value goes into JSON
 * on its own, so a tag named "a, b" never reads as the tags "a" and "b". A SHA-256 hash, so the
 * store that keeps it holds no description or name.
 */
fun WidgetState.key(): String {
    val content = when (this) {
        WidgetState.NoAccount -> buildJsonObject { put("state", "no-account") }

        is WidgetState.Ready -> buildJsonObject {
            put("state", "ready")
            put("account", account)
            put("running", running?.keyContent() ?: JsonNull)
            put("favorites", JsonArray(favorites.map { it.keyContent() }))
            put("paused", paused?.keyContent() ?: JsonNull)
            put("day", day?.keyContent() ?: JsonNull)
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun WidgetRunning.keyContent() = buildJsonObject {
    put("entryId", entryId.value)
    put("description", description)
    put("project", project)
    put("task", task)
    put("start", start.toString())
    put("accumulatedSeconds", accumulatedSeconds)
    put("target", target.keyContent())
    put("pausable", pausable)
    put("projectColor", projectColor)
}

private fun WidgetPaused.keyContent() = buildJsonObject {
    put("description", description)
    put("project", project)
    put("task", task)
    put("seconds", seconds)
    put("target", target.keyContent())
    put("projectColor", projectColor)
}

private fun WidgetFavorite.keyContent() = buildJsonObject {
    put("description", fields.description)
    put("projectId", fields.projectId?.value)
    put("taskId", fields.taskId?.value)
    put("tags", JsonArray(fields.tags.map(::JsonPrimitive)))
    put("billable", fields.billable)
    put("project", project)
    put("task", task)
}

private fun WidgetDayReport.keyContent() = buildJsonObject {
    put("date", date.toString())
    put("stoppedSeconds", stoppedSeconds)
    put("stoppedBillableSeconds", stoppedBillableSeconds)
    put("entryCount", entryCount)
    put(
        "projects",
        JsonArray(
            projects.map { row ->
                buildJsonObject {
                    put("projectId", row.projectId?.value)
                    put("project", row.project)
                    put("projectColor", row.projectColor)
                    put("stoppedSeconds", row.stoppedSeconds)
                    put("formattedStopped", row.formattedStopped)
                    put("running", row.running)
                }
            },
        ),
    )
    put(
        "running",
        running?.let {
            buildJsonObject {
                put("entryId", it.entryId.value)
                put("start", it.start.toString())
                put("billable", it.billable)
            }
        } ?: JsonNull,
    )
    put("durationFormat", durationFormat.name)
}

private fun TimerTarget.keyContent() = buildJsonObject {
    put("account", account)
    when (val target = this@keyContent) {
        is TimerTarget.Running -> put("entryId", target.entryId.value)
        is TimerTarget.Paused -> put("revision", target.revision)
    }
}

/** Sizes shared by responsive fitting and the widget layout, in dp. */
object WidgetLayout {
    // Approved 298×222 and 375×338 cards, with room for launcher padding.
    const val MEDIUM_WIDTH_DP = 260f
    const val MEDIUM_HEIGHT_DP = 200f
    const val LARGE_WIDTH_DP = 340f
    const val LARGE_HEIGHT_DP = 310f
    const val MAX_SEGMENTS = 5

    fun clockRowHeight(large: Boolean, scale: Float): Float = if (large) {
        grown(LARGE_CLOCK_ROW_DP, LARGE_CLOCK_ROW_GROWTH_DP, scale)
    } else {
        grown(CLOCK_ROW_DP, CLOCK_ROW_GROWTH_DP, scale)
    }
    fun reportHeadingHeight(scale: Float): Float = grown(REPORT_HEADING_DP, REPORT_HEADING_GROWTH_DP, scale)
    fun statsHeight(scale: Float): Float = grown(STATS_DP, STATS_GROWTH_DP, scale)
    fun todayRowHeight(scale: Float): Float = grown(TODAY_ROW_DP, TODAY_ROW_GROWTH_DP, scale)

    /** A row [base] tall at font scale 1 that grows by [growth] for each step of font scale above 1. */
    private fun grown(base: Float, growth: Float, scale: Float): Float = base + growth * (scale.coerceAtLeast(1f) - 1f)

    private const val LARGE_CLOCK_ROW_DP = 46f
    private const val LARGE_CLOCK_ROW_GROWTH_DP = 18f
    private const val CLOCK_ROW_DP = 28f
    private const val CLOCK_ROW_GROWTH_DP = 20f
    private const val REPORT_HEADING_DP = 22f
    private const val REPORT_HEADING_GROWTH_DP = 8f
    private const val STATS_DP = 44f
    private const val STATS_GROWTH_DP = 20f
    private const val TODAY_ROW_DP = 26f
    private const val TODAY_ROW_GROWTH_DP = 14f
    const val PADDING_DP = 12f
}
