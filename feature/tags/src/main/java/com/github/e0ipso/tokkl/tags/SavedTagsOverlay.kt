package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The rename form or the delete question open over the tags page, as the page keeps it in its
 * saved state, so that a new process restoring the page opens it again about the same tag.
 */
@Serializable
internal data class SavedTagsOverlay(
    val id: TagId,
    val workspaceId: WorkspaceId,
    val name: String,
    val confirmDelete: Boolean = false,
) {

    fun overlay(): TagsOverlay {
        val tag = Tag(id, workspaceId, name)
        return if (confirmDelete) TagsOverlay.ConfirmDelete(tag) else TagsOverlay.Rename(tag)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(overlay: TagsOverlay): SavedTagsOverlay = when (overlay) {
            is TagsOverlay.Rename -> overlay.tag.let { SavedTagsOverlay(it.id, it.workspaceId, it.name) }
            is TagsOverlay.ConfirmDelete -> overlay.tag.let { SavedTagsOverlay(it.id, it.workspaceId, it.name, true) }
        }

        /** The overlay [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedTagsOverlay? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
