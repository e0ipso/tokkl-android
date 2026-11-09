package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/** The session's entries over [cached], for a view model test, with no server unless one is given. */
internal fun sessionEntries(
    cached: Flow<List<EntryWithSendState>>,
    settings: StateFlow<AccountSettings?>,
    scope: CoroutineScope,
    clock: Clock,
    changes: EntryChanges = NoEntryChanges,
    olderPages: OlderPages = OlderPages { null },
    serverWeeks: ServerWeeks = ServerWeeks { null },
    app: AppForeground = AppForeground(MutableStateFlow(true), emptyFlow()),
    io: CoroutineContext = Dispatchers.Unconfined,
    accounts: Flow<Int> = emptyFlow(),
    computation: CoroutineContext = Dispatchers.Unconfined,
) = SessionEntries(cached, settings, changes, olderPages, serverWeeks, clock, app, accounts, scope, io, computation)

/** A queue whose entries keep their ids and that takes no change. */
internal object NoEntryChanges : EntryChanges {
    override suspend fun currentId(entryId: EntryId): EntryId = entryId

    override suspend fun create(
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate?,
    ): EntryId = throw AssertionError("unexpected create")

    override suspend fun update(
        entryId: EntryId,
        fields: EntryFields,
        start: Instant,
        stop: Instant?,
        noTimeDay: LocalDate?,
    ): Boolean = throw AssertionError("unexpected update")

    override suspend fun updateOlder(
        entryId: EntryId,
        workspaceId: WorkspaceId,
        fields: EntryFields,
        start: Instant,
        stop: Instant,
        noTimeDay: LocalDate?,
    ): Unit = throw AssertionError("unexpected updateOlder")

    override suspend fun delete(entryId: EntryId): Boolean = throw AssertionError("unexpected delete")

    override suspend fun delete(entry: TimeEntry): TimeEntry? = throw AssertionError("unexpected delete")
}
