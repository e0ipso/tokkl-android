package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import java.security.MessageDigest
import java.time.Instant

/**
 * What the running timer's notification shows (docs/PRD.md N1): the entry's description, its
 * project and task, and the start the system chronometer counts from.
 *
 * @param entryId the id the cache held the entry under, which the Stop action stops.
 * @param description as typed; blank when the entry has none.
 * @param project null when the entry has no project or the cache does not know it.
 * @param key changes with every edit of the running entry, with its start to the second, and with
 *   what the notification shows. It stays when the server answers: an entry made on the phone
 *   gets the server's id, its start whole seconds, its description trimmed and its tags the case
 *   of the account's tags. So two starts with the same fields within one second give the same
 *   key, and [entryId] tells them apart (see [runningNotificationChange]). It is a SHA-256 hash,
 *   so the store that keeps it holds no description or name.
 */
data class RunningNotificationContent(
    val entryId: Long,
    val description: String,
    val project: String?,
    val task: String?,
    val start: Instant,
    val key: String,
)

/** The notification for the [running] entry, or null when none runs. */
fun runningNotificationContent(
    running: CachedTimeEntry?,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
): RunningNotificationContent? {
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
    return RunningNotificationContent(
        entryId = running.id,
        description = running.description,
        project = labels.project,
        task = labels.task,
        start = running.start,
        key = key,
    )
}

/** What to do with the notification. */
sealed interface RunningNotificationChange {
    /** Post it, or replace the one on show. */
    data class Post(val content: RunningNotificationContent) : RunningNotificationChange

    /** Take it away: no entry runs. */
    data object Remove : RunningNotificationChange
}

/**
 * The running entry the notification last followed.
 *
 * @param key the entry's [RunningNotificationContent.key].
 * @param entryId the id the cache held the entry under. Null for a key kept before the store kept
 *   the id too, which then matches any entry with that key.
 */
data class FollowedEntry(val key: String, val entryId: Long?)

/**
 * The change to make when the running entry is [content] and the notification last followed
 * [followed], null for none, with [FollowedEntry.entryId] as the cache holds that entry now: the
 * server's id once the server has answered the phone's create. Nothing, when the entry is the one
 * the notification last followed: the app opening, a refresh with the same entry, or the server
 * giving the entry its id. So a notification swiped away on Android 14 and later stays away
 * until the next start, stop or edit. Two starts with the same fields within one second give the
 * same key, so the id tells them apart, and the notification's Stop stops the newer one.
 */
fun runningNotificationChange(followed: FollowedEntry?, content: RunningNotificationContent?): RunningNotificationChange? =
    when {
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
