package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.favorites.fields
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * What the home screen widget shows (docs/PRD.md N3). It comes from the phone alone, never from
 * the server, so the widget shows a Start or Stop at once, also offline.
 */
sealed interface WidgetState {

    /** No account on the phone, or none with a workspace to start in: the widget opens the app. */
    data object NoAccount : WidgetState

    /**
     * @param running the running entry, null when no timer runs.
     * @param favorites in the user's order, empty when the server keeps no favorites.
     */
    data class Ready(val running: WidgetRunning?, val favorites: List<WidgetFavorite>) : WidgetState
}

/**
 * The running entry as the widget shows it.
 *
 * @param entryId the id the cache holds the entry under, which Stop stops.
 * @param description as typed; blank when the entry has none.
 * @param project null when the entry has no project or the cache does not know it.
 */
data class WidgetRunning(
    val entryId: Long,
    val description: String,
    val project: String?,
    val task: String?,
    val start: Instant,
)

/** One favorite: what a tap starts, and the names of its project and task. */
data class WidgetFavorite(val fields: EntryFields, val project: String?, val task: String?)

/**
 * The widget for an account whose default workspace is [workspaceId], null when the phone has no
 * account, with the [running] entry and the cached [favorites]. [favoritesAvailable] is the
 * favorites capability: without it, as on Toggl Track, the widget shows none.
 */
fun widgetState(
    workspaceId: Long?,
    running: CachedTimeEntry?,
    favorites: List<CachedFavorite>,
    favoritesAvailable: Boolean,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
): WidgetState {
    if (workspaceId == null) return WidgetState.NoAccount
    val shown = running?.takeIf { it.stop == null }?.let { entry ->
        val labels = entryLabels(entry.fields(), projects, tasks)
        WidgetRunning(entry.id, entry.description, labels.project, labels.task, entry.start)
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
    return WidgetState.Ready(shown, starts)
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
 * The base of a Chronometer that counts from [start]: the `SystemClock.elapsedRealtime()` at which
 * it read zero, given that [now] on the wall clock is [elapsedRealtime] on that clock. A start
 * after [now], from a clock that runs late, counts from zero.
 */
fun chronometerBase(start: Instant, now: Instant, elapsedRealtime: Long): Long =
    elapsedRealtime - Duration.between(start, now).toMillis().coerceAtLeast(0)

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
    const val PADDING_DP = 12f
    const val TIMER_ROW_DP = 80f
    const val FAVORITE_ROW_DP = 48f

    fun buttonHeight(fontScale: Float): Float = 48f + 16f * (fontScale.coerceAtLeast(1f) - 1f)

    fun timerRowHeight(fontScale: Float): Float = buttonHeight(fontScale) + (TIMER_ROW_DP - 48f) * fontScale.coerceAtLeast(1f)

    fun favoriteRowHeight(fontScale: Float): Float = FAVORITE_ROW_DP * fontScale.coerceAtLeast(1f)
}
