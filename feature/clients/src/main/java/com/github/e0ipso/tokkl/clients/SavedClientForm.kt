package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The client form as the page keeps it in its saved state, so that a new process restoring the
 * page opens it as it was: the client it edits, none for a new one, and what it holds.
 */
@Serializable
internal data class SavedClientForm(val client: SavedClient? = null, val name: String, val notes: String) {

    fun form(): ClientsOverlay.Form {
        val draft = ClientDraft(name, notes)
        return client?.let { ClientsOverlay.Edit(it.client(), draft) } ?: ClientsOverlay.New(draft)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(form: ClientsOverlay.Form) = SavedClientForm(
            client = (form as? ClientsOverlay.Edit)?.client?.let(::SavedClient),
            name = form.draft.name,
            notes = form.draft.notes,
        )

        /** The form [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedClientForm? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** A [Client] as the saved state keeps it. */
@Serializable
internal data class SavedClient(
    val id: ClientId,
    val workspaceId: WorkspaceId,
    val name: String,
    val archived: Boolean,
    val notes: String?,
) {
    constructor(client: Client) : this(client.id, client.workspaceId, client.name, client.archived, client.notes)

    fun client() = Client(id, workspaceId, name, archived, notes)
}
