package com.github.e0ipso.tokkl.model

import kotlinx.serialization.Serializable

// The ids of the account's data. Each wraps the server's number, so a project id cannot go where
// a task id belongs. Serialized, each is that number.

/**
 * A time entry's id: the server's, or a negative local id while the server has not answered the
 * create the phone queued for it. The queue's calls take either, and resolve a local id the server
 * has answered.
 */
@Serializable
@JvmInline
value class EntryId(val value: Long) {

    val local: LocalEntryId? get() = if (value < 0) LocalEntryId(value) else null

    override fun toString(): String = value.toString()
}

/**
 * The negative id the phone gives an entry it creates, until the server answers the create with
 * the entry's server id. Only [EntryId.local] makes one, so a server id never goes where the
 * phone looks up a local one.
 */
@JvmInline
value class LocalEntryId internal constructor(val value: Long) {

    val entryId: EntryId get() = EntryId(value)

    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class WorkspaceId(val value: Long) {
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class ProjectId(val value: Long) {
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class ClientId(val value: Long) {
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class TaskId(val value: Long) {
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class TagId(val value: Long) {
    override fun toString(): String = value.toString()
}
