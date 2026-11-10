package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.countsFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest
import java.time.Instant

/** The timer notification's common labels and the state its actions address. */
sealed interface RunningNotificationContent {
    val description: String
    val project: String?
    val task: String?
    val key: String
    val accumulatedSeconds: Long
    val target: TimerTarget

    val entryId: EntryId? get() = (target as? TimerTarget.Running)?.entryId

    data class Running(
        override val description: String,
        override val project: String?,
        override val task: String?,
        val start: Instant,
        override val key: String,
        override val accumulatedSeconds: Long = 0,
        override val target: TimerTarget.Running,
        val pausable: Boolean = false,
    ) : RunningNotificationContent

    data class Paused(
        override val description: String,
        override val project: String?,
        override val task: String?,
        override val key: String,
        override val accumulatedSeconds: Long,
        override val target: TimerTarget.Paused,
    ) : RunningNotificationContent
}

/**
 * The notification for what the timer [controls] show, or null when no timer runs or is paused.
 * Its key also says which account the actions act for, whether Pause is offered and, for a
 * resumed clone, the chain's stored seconds, so a change to any of them posts it again with
 * actions that match.
 */
fun runningNotificationContent(
    controls: TimerControls,
    projects: List<Project>,
    tasks: List<Task>,
): RunningNotificationContent? = when (controls) {
    TimerControls.Idle -> null

    is TimerControls.Running -> runningNotificationContent(
        controls.entry,
        projects,
        tasks,
        controls.target.account,
    )?.let { content ->
        content.copy(
            start = controls.countsFrom,
            key = hash(
                content.key,
                controls.target.account,
                controls.accumulatedSeconds.toString(),
                controls.pausable.toString(),
            ),
            accumulatedSeconds = controls.accumulatedSeconds,
            target = controls.target,
            pausable = controls.pausable,
        )
    }

    is TimerControls.Paused -> {
        val fields = controls.fields
        val labels = entryLabels(fields, projects, tasks)
        RunningNotificationContent.Paused(
            description = fields.description,
            project = labels.project,
            task = labels.task,
            // The revision too, so the actions send the revision the server holds now.
            key = hash(
                "paused",
                controls.target.account,
                controls.target.revision.toString(),
                controls.seconds.toString(),
                fields.description.trim(),
                fields.projectId?.toString().orEmpty(),
                fields.taskId?.toString().orEmpty(),
                fields.billable.toString(),
                fields.tags.map(::tagKey).distinct().sorted().joinToString("") { field(it) },
                labels.project.orEmpty(),
                labels.task.orEmpty(),
            ),
            accumulatedSeconds = controls.seconds,
            target = controls.target,
        )
    }
}

/**
 * The notification for the [running] entry, or null when none runs. Its actions act on the entry
 * for [account], the name the signed-in account's timer controls carry.
 */
fun runningNotificationContent(
    running: TimeEntry?,
    projects: List<Project>,
    tasks: List<Task>,
    account: String,
): RunningNotificationContent.Running? {
    if (running == null || running.stop != null) return null
    val labels = entryLabels(running.fields(), projects, tasks)
    // As the server stores them, so its answer to a start or an edit leaves the key as it was.
    val key = hash(
        running.workspaceId.toString(),
        running.start.epochSecond.toString(),
        running.description.trim(),
        running.projectId?.toString().orEmpty(),
        running.taskId?.toString().orEmpty(),
        running.billable.toString(),
        running.tags.map(::tagKey).distinct().sorted().joinToString("") { field(it) },
        labels.project.orEmpty(),
        labels.task.orEmpty(),
    )
    return RunningNotificationContent.Running(
        description = running.description,
        project = labels.project,
        task = labels.task,
        start = running.start,
        key = key,
        target = TimerTarget.Running(running.id, account),
    )
}

sealed interface RunningNotificationChange {
    data class Post(val content: RunningNotificationContent) : RunningNotificationChange

    /** No entry runs. */
    data object Remove : RunningNotificationChange
}

/**
 * The running entry the notification last followed.
 *
 * @param entryId the id the cache held the entry under. Null for a key kept before the store kept
 *   the id too, which then matches any entry with that key.
 */
data class FollowedEntry(val key: String, val entryId: EntryId?)

/**
 * The change to make when the running entry is [content] and the notification last followed
 * [followed], null for none, with [FollowedEntry.entryId] as the cache holds that entry now: the
 * server's id once the server has answered the phone's create. Nothing, when the entry is the one
 * the notification last followed: the app opening, a refresh with the same entry, or the server
 * giving the entry its id. So a notification swiped away on Android 14 and later stays away
 * until the next start, stop or edit. Two starts with the same fields within one second give the
 * same key, so the id tells them apart, and the notification's Stop stops the newer one.
 */
fun runningNotificationChange(
    followed: FollowedEntry?,
    content: RunningNotificationContent?,
): RunningNotificationChange? = when {
    content == null -> if (followed == null) null else RunningNotificationChange.Remove
    followed?.key == content.key && (followed.entryId == null || followed.entryId == content.entryId) -> null
    else -> RunningNotificationChange.Post(content)
}

/** Each value with its length first, so no two lists of values give the same text. */
private fun hash(vararg values: String): String {
    val text = values.joinToString("") { field(it) }
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun field(value: String): String = "${value.length}:$value"

/** A tag as Tokkl matches names: trimmed, with ASCII letters in lower case. */
private fun tagKey(name: String): String =
    name.trim().map { if (it in 'A'..'Z') it + ('a' - 'A') else it }.joinToString("")

internal fun runningNotificationContents(
    controls: Flow<TimerControls>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
): Flow<RunningNotificationContent?> = combine(controls, projects, tasks) { current, cachedProjects, cachedTasks ->
    runningNotificationContent(current, cachedProjects, cachedTasks)
}
