package com.github.e0ipso.tokkl.picker

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.cache.CachedTask

/**
 * What an entry has picked: a project or none, a task of that project or none, and tags by name.
 * The queue sends the tags by name, and the server creates the ones it does not know.
 */
data class Picked(
    val projectId: Long? = null,
    val taskId: Long? = null,
    val tags: List<String> = emptyList(),
) {

    /** The project alone, with no task. */
    fun withProject(projectId: Long?): Picked = copy(projectId = projectId, taskId = null)

    /** The task and its project. */
    fun withTask(task: CachedTask): Picked = copy(projectId = task.projectId, taskId = task.id)

    /** The tags with [name] added, or taken out when picked already. Tag names ignore case, as on the server. */
    fun withTagToggled(name: String): Picked =
        if (tags.any { it.equals(name, ignoreCase = true) }) {
            copy(tags = tags.filterNot { it.equals(name, ignoreCase = true) })
        } else {
            copy(tags = tags + name)
        }
}

/** The cached rows the picker offers. */
data class PickerSource(
    val projects: List<CachedProject> = emptyList(),
    val clients: List<CachedClient> = emptyList(),
    val tasks: List<CachedTask> = emptyList(),
    val tags: List<CachedTag> = emptyList(),
)

/** The projects of one client, or of none when [clientName] is null. */
data class ProjectGroup(val clientName: String?, val clientArchived: Boolean, val projects: List<ProjectOption>)

/** A project, [selected] when the entry has it, also together with one of its tasks. */
data class ProjectOption(
    val project: CachedProject,
    val selected: Boolean,
    val archived: Boolean,
    val tasks: List<TaskOption>,
)

data class TaskOption(val task: CachedTask, val selected: Boolean, val archived: Boolean)

/** A tag by name. [cached] is false for a name the phone has no tag for, such as one typed offline. */
data class TagOption(val name: String, val selected: Boolean, val cached: Boolean)

/**
 * What the picker shows.
 *
 * @property noProject whether to offer "No project", which only an empty search does.
 * @property newTag the search as a new tag name, when no tag has that name.
 */
data class PickerOptions(
    val noProject: Boolean,
    val noProjectSelected: Boolean,
    val groups: List<ProjectGroup>,
    val tags: List<TagOption>,
    val newTag: String?,
)

/**
 * The options for an entry in [workspaceId] that started with [original] and now has [picked],
 * filtered by [query], as the web app's pickers do (../tokkl/packages/web/src/components/
 * Pickers.tsx): a project shows when its name or its client's name holds the query, ignoring
 * case, or when one of its tasks does. Its tasks show when the project matches or the task does.
 * Groups go by client name, the projects without a client first, and the tags match by name.
 *
 * Archived projects and done tasks show only when [original] uses them, so an old entry keeps
 * them and nothing else can pick them. Neither Tokkl nor Toggl archives tags. A tag name of
 * [picked] that no cached tag has shows too, selected.
 */
fun pickerOptions(workspaceId: Long, original: Picked, picked: Picked, query: String, source: PickerSource): PickerOptions {
    val search = query.trim()
    val clients = source.clients.associateBy { it.id }
    val tasksByProject = source.tasks
        .filter { it.workspaceId == workspaceId && (it.active || it.id == original.taskId) }
        .groupBy { it.projectId }
    val projects = source.projects.filter { it.workspaceId == workspaceId && (it.active || it.id == original.projectId) }
    val options = projects.mapNotNull { project ->
        val client = project.clientId?.let(clients::get)
        val projectMatches = project.name.contains(search, ignoreCase = true) ||
            client?.name?.contains(search, ignoreCase = true) == true
        val tasks = tasksByProject[project.id].orEmpty()
            .filter { projectMatches || it.name.contains(search, ignoreCase = true) }
            .map { TaskOption(it, selected = it.id == picked.taskId, archived = !it.active) }
        if (!projectMatches && tasks.isEmpty()) return@mapNotNull null
        client to ProjectOption(
            project = project,
            selected = project.id == picked.projectId,
            archived = !project.active,
            tasks = tasks,
        )
    }
    val groups = options
        .groupBy({ it.first?.name }, { it.second })
        .map { (name, list) ->
            val archived = options.first { it.first?.name == name }.first?.archived == true
            ProjectGroup(name, archived, list)
        }
        .sortedWith(compareBy<ProjectGroup> { it.clientName != null }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.clientName.orEmpty() })

    val cachedTags = source.tags.filter { it.workspaceId == workspaceId }.map { it.name }
    val typedTags = picked.tags.filterNot { name -> cachedTags.any { it.equals(name, ignoreCase = true) } }
    val tags = (cachedTags.map { it to true } + typedTags.map { it to false })
        .filter { (name, _) -> name.contains(search, ignoreCase = true) }
        .map { (name, cached) -> TagOption(name, selected = picked.tags.any { it.equals(name, ignoreCase = true) }, cached = cached) }
    val knownTag = (cachedTags + typedTags).any { it.equals(search, ignoreCase = true) }

    return PickerOptions(
        noProject = search.isEmpty(),
        noProjectSelected = picked.projectId == null,
        groups = groups,
        tags = tags,
        newTag = search.takeIf { it.isNotEmpty() && !knownTag },
    )
}
