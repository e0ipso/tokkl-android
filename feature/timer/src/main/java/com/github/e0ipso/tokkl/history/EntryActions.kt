package com.github.e0ipso.tokkl.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.UndoOutcome
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.entries.SessionEntries
import com.github.e0ipso.tokkl.entryfields.SplitHalves
import com.github.e0ipso.tokkl.entryfields.SplitProblem
import com.github.e0ipso.tokkl.entryfields.SplitResult
import com.github.e0ipso.tokkl.entryfields.splitTimes
import com.github.e0ipso.tokkl.favorites.FavoritesProblem
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.time.SnappedSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/**
 * A message the history shows about the last action on an entry. [serial] tells two equal
 * messages apart, so the second one shows too.
 */
sealed interface EntryNotice {
    val serial: Long

    /** [entryId] is deleted. With [undo], Undo can take the delete back. */
    data class Deleted(override val serial: Long, val entryId: EntryId, val undo: Boolean) : EntryNotice

    /** Undo of the delete of [entryId] needs the server, which the phone cannot reach. Undo can be tried again. */
    data class UndoNeedsServer(override val serial: Long, val entryId: EntryId) : EntryNotice

    /** The server did not restore the entry. [status] is its HTTP status, or null without one. */
    data class UndoFailed(override val serial: Long, val status: Int?) : EntryNotice

    data class Duplicated(override val serial: Long) : EntryNotice

    data class Split(override val serial: Long) : EntryNotice

    data class Pinned(override val serial: Long) : EntryNotice

    /** Nothing was pinned, because the favorites already hold the most there can be. */
    data class PinFull(override val serial: Long) : EntryNotice

    /** Nothing was pinned, because pinning needs the server, which the phone cannot reach. */
    data class PinNeedsServer(override val serial: Long) : EntryNotice

    data class PinFailed(override val serial: Long, val problem: ServerProblem) : EntryNotice
}

/**
 * The dialog that splits the stopped [source] at a clock time (docs/PRD.md H6).
 *
 * @property whole its span as the server stores it, which the split time must fall inside.
 * @property text the split time as typed, such as `10:30` or `1030`.
 * @property halves the halves [text] cuts the entry into, which the dialog previews, or null.
 * @property problem why Split did not split, until the time changes.
 */
data class SplitDialog(
    val source: TimeEntry,
    val whole: SnappedSpan,
    val zone: ZoneId,
    val text: String = "",
    val halves: SplitHalves? = null,
    val problem: SplitProblem? = null,
)

/**
 * What the history's entry actions show over the list.
 *
 * @property confirmDelete the entry whose delete waits for the user's yes, on a server that
 *   cannot restore a deleted entry.
 * @property notice the message to show, until it is dismissed or acted on.
 */
@Immutable
data class EntryActionsState(
    val confirmDelete: EntryId? = null,
    val notice: EntryNotice? = null,
    val split: SplitDialog? = null,
)

/**
 * The actions on an entry in the history (docs/PRD.md H3 and H5). Delete goes through the change queue,
 * so the entry leaves the list at once, also offline, and a message offers Undo, as the web app's
 * EntryList.tsx does. Undo takes a delete the queue has not sent out of the queue, with no
 * request, and asks the server to restore one it has sent. When the server cannot restore a
 * deleted entry, as Toggl Track, Delete asks first and the message offers no Undo
 * (docs/PRD.md section 8.3). Duplicate makes a copy through the queue too, so it works offline,
 * and so does Split (H6), which asks for a clock time and cuts the entry in two there. Pin as
 * favorite (T5) needs the server, as the web app's does, so offline it pins nothing and says so.
 * Call everything on the main thread.
 *
 * @param removals the session's entries, which leave a deleted entry out and show a restored one again.
 * @param capabilities whether the server restores deleted entries, `Capabilities.restore`, read
 *   at a tap on a screen, which shows once the capabilities have loaded.
 */
class EntryActionsViewModel(
    private val entries: Flow<List<EntryWithSendState>>,
    private val changes: EntryChanges,
    private val removals: SessionEntries,
    private val undoDelete: suspend (EntryId) -> UndoOutcome,
    private val capabilities: StateFlow<Capabilities>,
    private val accountSettings: StateFlow<AccountSettings?>,
    scope: CoroutineScope,
    private val clock: Clock,
) : ViewModel(scope) {

    private val mutableState = MutableStateFlow(EntryActionsState())

    private val deleting = mutableSetOf<EntryId>()
    private var undoing = false
    private var splitting = false

    fun canRestoreDelete(): Boolean = capabilities.value.restore

    /** Called only after the editor's deletion transaction commits. The session's entries leave it out. */
    fun deleted(entry: TimeEntry, undo: Boolean) {
        removals.deleted(entry)
        mutableState.update { it.copy(notice = EntryNotice.Deleted(++serial, entry.id, undo)) }
    }

    private var serial = 0L

    val state: StateFlow<EntryActionsState> = mutableState.asStateFlow()

    /** Deletes the cached entry [entryId], or asks first when the server cannot restore it. */
    fun delete(entryId: EntryId) {
        if (canRestoreDelete()) {
            queueDelete(entryId, undo = true)
        } else {
            mutableState.update { it.copy(confirmDelete = entryId) }
        }
    }

    fun confirmDelete() {
        val entryId = mutableState.value.confirmDelete ?: return
        mutableState.update { it.copy(confirmDelete = null) }
        queueDelete(entryId, undo = false)
    }

    fun cancelDelete() {
        mutableState.update { it.copy(confirmDelete = null) }
    }

    /** Takes back the delete of [entryId], which a [EntryNotice.Deleted] or [EntryNotice.UndoNeedsServer] offered. */
    fun undo(entryId: EntryId) {
        if (undoing) return
        undoing = true
        val offered = mutableState.value.notice
        viewModelScope.launch {
            try {
                // A refusal of the phone's storage keeps the message, so Undo can be tapped again.
                val undone = localWrite { undoDelete(entryId) } as? LocalWrite.Written ?: return@launch
                val notice = when (val outcome = undone.value) {
                    UndoOutcome.Cancelled, UndoOutcome.Restored -> {
                        removals.restored(entryId)
                        null
                    }

                    UndoOutcome.NeedsServer -> EntryNotice.UndoNeedsServer(++serial, entryId)

                    is UndoOutcome.Failed -> EntryNotice.UndoFailed(++serial, outcome.status)
                }
                mutableState.update { if (it.notice == offered) it.copy(notice = notice) else it }
            } finally {
                undoing = false
            }
        }
    }

    /**
     * Copies the cached stopped entry [entryId] from its start to its stop, as `duplicate` in the
     * web app's EntryList.tsx and EntryEditor.tsx does, into the source's workspace through the
     * change queue. A running entry or one the cache no longer holds makes no copy. A no-time
     * source makes a no-time copy on its day; without an account timezone to read that day in, it
     * makes none, and so does a write the phone's storage refuses.
     */
    fun duplicate(entryId: EntryId) {
        viewModelScope.launch {
            val source = entries.first().firstOrNull { it.entry.id == entryId }?.entry ?: return@launch
            val timing = when (val kept = source.timing) {
                is EntryTiming.Running -> return@launch

                is EntryTiming.Stopped -> kept

                is EntryTiming.AllDay -> kept.copy(
                    day = kept.day ?: accountSettings.value?.time(clock)?.zone?.let(kept::dayIn) ?: return@launch,
                )
            }
            if (localWrite { changes.create(source.workspaceId, source.fields(), timing) } is LocalWrite.Written) {
                mutableState.update { it.copy(notice = EntryNotice.Duplicated(++serial)) }
            }
        }
    }

    /**
     * Opens the split dialog on the cached stopped entry [entryId]. A running entry, one the cache
     * no longer holds, a no-time entry, which has no time to cut at, or an account timezone the
     * phone cannot resolve opens nothing.
     */
    fun split(entryId: EntryId) {
        viewModelScope.launch {
            val source = entries.first().firstOrNull { it.entry.id == entryId }?.entry ?: return@launch
            val timed = source.timing as? EntryTiming.Stopped ?: return@launch
            val stop = timed.stop.takeIf { !it.isBefore(timed.start) } ?: return@launch
            val time = accountSettings.value?.time(clock) ?: return@launch
            val span = time.snapUnsent(source.start, stop) ?: return@launch
            mutableState.update { it.copy(split = SplitDialog(source, span, time.zone)) }
        }
    }

    /**
     * Saves the cached entry [entryId] as a timer template through [templates], as `pin` in the web
     * app's EntryList.tsx does. Templates live on the server, so when the phone is [offline] nothing
     * is tried and the message says the server is needed.
     */
    fun pin(entryId: EntryId, templates: FavoritesViewModel, offline: Boolean) {
        if (offline) {
            mutableState.update { it.copy(notice = EntryNotice.PinNeedsServer(++serial)) }
            return
        }
        viewModelScope.launch {
            val source = entries.first().firstOrNull { it.entry.id == entryId }?.entry ?: return@launch
            templates.add(source.fields()) { problem ->
                val notice = when (problem) {
                    null -> EntryNotice.Pinned(++serial)

                    FavoritesProblem.Full -> EntryNotice.PinFull(++serial)

                    is FavoritesProblem.Server -> if (problem.problem == ServerProblem.Unreachable) {
                        EntryNotice.PinNeedsServer(++serial)
                    } else {
                        EntryNotice.PinFailed(++serial, problem.problem)
                    }
                }
                mutableState.update { it.copy(notice = notice) }
            }
        }
    }

    /** The split time is now [text]: the preview follows it, and the last problem goes. */
    fun setSplitText(text: String) {
        mutableState.update { state ->
            val dialog = state.split ?: return@update state
            state.copy(
                split = dialog.copy(text = text, halves = splitOf(dialog.source, text) as? SplitHalves, problem = null),
            )
        }
    }

    fun cancelSplit() {
        mutableState.update { it.copy(split = null) }
    }

    /**
     * Splits the entry at the dialog's time, as split in the web app's EntryEditor.tsx does: the
     * original ends there, and a copy of its fields runs on to the original stop. Both go to the
     * change queue in one write ([EntryChanges.split]), the new entry first, as the web app posts
     * it first. A time that cuts nowhere keeps the dialog open with its problem, and so does a
     * write the phone's storage refuses, so Split can be tapped again. An entry that has left the
     * cache closes the dialog.
     */
    fun confirmSplit() {
        val dialog = mutableState.value.split ?: return
        if (splitting) return
        splitting = true
        viewModelScope.launch {
            try {
                split(dialog)
            } finally {
                splitting = false
            }
        }
    }

    private suspend fun split(dialog: SplitDialog) {
        val source = entries.first().firstOrNull { it.entry.id == dialog.source.id }?.entry
        val result = source?.let { splitOf(it, dialog.text) }
        if (source == null || result == null) {
            mutableState.update { if (it.split == dialog) it.copy(split = null) else it }
            return
        }
        if (result !is SplitHalves) {
            mutableState.update {
                if (it.split == dialog) {
                    it.copy(split = dialog.copy(halves = null, problem = result as SplitProblem))
                } else {
                    it
                }
            }
            return
        }
        val first = EntryTiming.Stopped(result.first.start, result.first.stop)
        val second = EntryTiming.Stopped(result.second.start, result.second.stop)
        val split = localWrite { changes.split(source.id, source.fields(), first, second) }
        if (split !is LocalWrite.Written) return
        mutableState.update {
            val closed = if (it.split == dialog) it.copy(split = null) else it
            // The original left the cache meanwhile, so nothing was split.
            if (split.value) closed.copy(notice = EntryNotice.Split(++serial)) else closed
        }
    }

    /** [notice] went away without its action. */
    fun noticeDone(notice: EntryNotice) {
        mutableState.update { if (it.notice == notice) it.copy(notice = null) else it }
    }

    /** What [text] cuts the stopped [source] into, or null without an account timezone. */
    private fun splitOf(source: TimeEntry, text: String): SplitResult? {
        val settings = accountSettings.value ?: return null
        val time = settings.time(clock) ?: return null
        val stop = source.stop ?: return null
        return splitTimes(text, source.start, stop, time, settings.twelveHour)
    }

    private fun queueDelete(entryId: EntryId, undo: Boolean) {
        if (!deleting.add(entryId)) return
        viewModelScope.launch {
            try {
                val snapshot = entries.first().firstOrNull { it.entry.id == entryId }?.entry
                // A refusal of the phone's storage leaves the entry, so Delete can be tapped again.
                if (snapshot != null) {
                    (localWrite { changes.delete(snapshot) } as? LocalWrite.Written)?.value?.let { deleted(it, undo) }
                } else if (localWrite { changes.delete(entryId) } == LocalWrite.Written(true)) {
                    mutableState.update { it.copy(notice = EntryNotice.Deleted(++serial, entryId, undo)) }
                }
            } finally {
                deleting.remove(entryId)
            }
        }
    }
}
