package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The form or question open over the clients page as the page keeps it in its saved state, so
 * that a new process restoring the page opens it as it was: the client it is about, none for a
 * new one, what the form holds, and whether it is the question whether to archive the client.
 */
@Serializable
internal data class SavedClientsOverlay(
    val client: SavedClient? = null,
    val name: String = "",
    val notes: String = "",
    val confirmArchive: Boolean = false,
) {

    /** The overlay as it was, or null for a question about no client. */
    fun overlay(): ClientsOverlay? {
        val saved = client?.client()
        if (confirmArchive) return saved?.let(ClientsOverlay::ConfirmArchive)
        val draft = ClientDraft(name, notes)
        return saved?.let { ClientsOverlay.Edit(it, draft) } ?: ClientsOverlay.New(draft)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(overlay: ClientsOverlay) = when (overlay) {
            is ClientsOverlay.Form -> SavedClientsOverlay(
                client = (overlay as? ClientsOverlay.Edit)?.client?.let(::SavedClient),
                name = overlay.draft.name,
                notes = overlay.draft.notes,
            )

            is ClientsOverlay.ConfirmArchive -> SavedClientsOverlay(SavedClient(overlay.client), confirmArchive = true)
        }

        /** The overlay [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): SavedClientsOverlay? = try {
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
