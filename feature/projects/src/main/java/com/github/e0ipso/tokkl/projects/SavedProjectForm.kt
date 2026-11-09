package com.github.e0ipso.tokkl.projects

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The project form as the page keeps it in its saved state, so that a new process restoring the
 * page opens it as it was: the project it edits as the server sent it, none for a new one, and
 * what it holds.
 */
@Serializable
internal data class SavedProjectForm(val project: ProjectDetails? = null, val draft: ProjectDraft) {

    fun form(): ProjectForm = project?.let { ProjectForm.Edit(it, draft) } ?: ProjectForm.New(draft)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(form: ProjectForm) = SavedProjectForm((form as? ProjectForm.Edit)?.project, form.draft)

        /** The form [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedProjectForm? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
