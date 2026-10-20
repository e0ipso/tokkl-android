package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeSender.UndoOutcome
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.entry.SplitHalves
import com.github.e0ipso.tokkl.entry.SplitProblem
import com.github.e0ipso.tokkl.entry.SplitResult
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.entry.splitTimes
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A message the history shows about the last action on an entry. [serial] tells two equal
 * messages apart, so the second one shows too.
 */
sealed interface EntryNotice {
    val serial: Long

    /** [entryId] is deleted. With [undo], Undo can take the delete back. */
    data class Deleted(override val serial: Long, val entryId: Long, val undo: Boolean) : EntryNotice

    /** Undo of the delete of [entryId] needs the server, which the phone cannot reach. Undo can be tried again. */
    data class UndoNeedsServer(override val serial: Long, val entryId: Long) : EntryNotice

    /** The server did not restore the entry. [status] is its HTTP status, or null without one. */
    data class UndoFailed(override val serial: Long, val status: Int?) : EntryNotice

    /** A copy of an entry is on the phone and queued for the server. */
    data class Duplicated(override val serial: Long) : EntryNotice

    /** An entry is split in two on the phone, and both changes are queued for the server. */
    data class Split(override val serial: Long) : EntryNotice
}

/**
 * The dialog that splits the stopped [source] at a clock time (docs/PRD.md H6).
 *
 * @property source the cached entry as the dialog opened on it.
 * @property whole its span as the server stores it, which the split time must fall inside.
 * @property zone the account's timezone, which the dialog shows times in.
 * @property text the split time as typed, such as `10:30` or `1030`.
 * @property halves the halves [text] cuts the entry into, which the dialog previews, or null.
 * @property problem why Split did not split, until the time changes.
 */
data class SplitDialog(
    val source: CachedTimeEntry,
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
 * @property split the open split dialog.
 */
data class EntryActionsState(val confirmDelete: Long? = null, val notice: EntryNotice? = null, val split: SplitDialog? = null)

/**
 * The actions on an entry in the history (docs/PRD.md H3 and H5). Delete goes through the change queue,
 * so the entry leaves the list at once, also offline, and a message offers Undo, as the web app's
 * EntryList.tsx does. Undo takes a delete the queue has not sent out of the queue, with no
 * request, and asks the server to restore one it has sent. When the server cannot restore a
 * deleted entry, as Toggl Track, Delete asks first and the message offers no Undo
 * (docs/PRD.md section 8.3). Duplicate makes a copy through the queue too, so it works offline,
 * and so does Split (H6), which asks for a clock time and cuts the entry in two there. Call
 * everything on the main thread.
 *
 * @param entries the cached entries, as AccountCache.entries gives them, which Duplicate copies
 *   and Split cuts.
 * @param accountSettings the account's timezone, time resolution and clock format, which Split
 *   reads the time with.
 * @param undoDelete takes back the delete of an entry, as `ChangeSender.undoDelete` does.
 * @param canRestore whether the server restores deleted entries, `Capabilities.restore`.
 */
class EntryActionsModel(
    private val entries: Flow<List<EntryWithSendState>>,
    private val changes: EntryChanges,
    private val undoDelete: suspend (Long) -> UndoOutcome,
    private val canRestore: () -> Boolean,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val mutableState = MutableStateFlow(EntryActionsState())

    private var serial = 0L

    val state: StateFlow<EntryActionsState> = mutableState.asStateFlow()

    /** Deletes the cached entry [entryId], or asks first when the server cannot restore it. */
    fun delete(entryId: Long) {
        if (canRestore()) {
            queueDelete(entryId, undo = true)
        } else {
            mutableState.update { it.copy(confirmDelete = entryId) }
        }
    }

    /** The user said yes to the delete that [EntryActionsState.confirmDelete] asks about. */
    fun confirmDelete() {
        val entryId = mutableState.value.confirmDelete ?: return
        mutableState.update { it.copy(confirmDelete = null) }
        queueDelete(entryId, undo = false)
    }

    fun cancelDelete() {
        mutableState.update { it.copy(confirmDelete = null) }
    }

    /** Takes back the delete of [entryId], which a [EntryNotice.Deleted] or [EntryNotice.UndoNeedsServer] offered. */
    fun undo(entryId: Long) {
        mutableState.update { it.copy(notice = null) }
        scope.launch {
            val notice = when (val outcome = undoDelete(entryId)) {
                UndoOutcome.Cancelled, UndoOutcome.Restored -> null
                UndoOutcome.NeedsServer -> EntryNotice.UndoNeedsServer(++serial, entryId)
                is UndoOutcome.Failed -> EntryNotice.UndoFailed(++serial, outcome.status)
            }
            if (notice != null) mutableState.update { it.copy(notice = notice) }
        }
    }

    /**
     * Copies the cached stopped entry [entryId]: a new entry with its description, project, task,
     * tags and billable, from its start to its stop. That is the web app's duplicate rule, in
     * `duplicate` of ../tokkl/packages/web/src/components/EntryList.tsx and of EntryEditor.tsx,
     * which post the source's own start and stop. The copy goes to the source's workspace through
     * the change queue, so it shows at once, also offline. A running entry or one the cache no
     * longer holds makes no copy, as the web app duplicates only a stopped one.
     */
    fun duplicate(entryId: Long) {
        scope.launch {
            val source = entries.first().firstOrNull { it.entry.id == entryId }?.entry ?: return@launch
            val stop = source.stop ?: return@launch
            changes.create(source.workspaceId, source.fields(), source.start, stop)
            mutableState.update { it.copy(notice = EntryNotice.Duplicated(++serial)) }
        }
    }

    /**
     * Opens the split dialog on the cached stopped entry [entryId]. A running entry, one the cache
     * no longer holds, or an account timezone the phone cannot resolve opens nothing.
     */
    fun split(entryId: Long) {
        scope.launch {
            val source = entries.first().firstOrNull { it.entry.id == entryId }?.entry ?: return@launch
            val stop = source.stop?.takeIf { !it.isBefore(source.start) } ?: return@launch
            val time = accountSettings.value?.time(clock) ?: return@launch
            mutableState.update { it.copy(split = SplitDialog(source, time.snapUnsent(source.start, stop), time.zone)) }
        }
    }

    /** The split time is now [text]: the preview follows it, and the last problem goes. */
    fun setSplitText(text: String) {
        mutableState.update { state ->
            val dialog = state.split ?: return@update state
            state.copy(split = dialog.copy(text = text, halves = splitOf(dialog.source, text) as? SplitHalves, problem = null))
        }
    }

    fun cancelSplit() {
        mutableState.update { it.copy(split = null) }
    }

    /**
     * Splits the entry at the dialog's time, as split in the web app's EntryEditor.tsx does: the
     * original ends at the split, and a new entry with its description, project, task, tags and
     * billable runs from there to the original stop. Both go to the change queue, the new entry
     * first, as the web app posts it first, so the split works offline. A time that cuts nowhere
     * keeps the dialog open with its problem. An entry that has left the cache closes the dialog.
     */
    fun confirmSplit() {
        val dialog = mutableState.value.split ?: return
        scope.launch {
            val source = entries.first().firstOrNull { it.entry.id == dialog.source.id }?.entry
            val result = source?.let { splitOf(it, dialog.text) }
            if (source == null || result == null) {
                mutableState.update { if (it.split == dialog) it.copy(split = null) else it }
                return@launch
            }
            if (result !is SplitHalves) {
                mutableState.update { if (it.split == dialog) it.copy(split = dialog.copy(halves = null, problem = result as SplitProblem)) else it }
                return@launch
            }
            mutableState.update { if (it.split == dialog) it.copy(split = null) else it }
            val created = changes.create(source.workspaceId, source.fields(), result.second.start, result.second.stop)
            if (changes.update(source.id, source.fields(), result.first.start, result.first.stop)) {
                mutableState.update { it.copy(notice = EntryNotice.Split(++serial)) }
            } else {
                // The original left the cache meanwhile, so the second half goes too.
                changes.delete(created)
            }
        }
    }

    /** [notice] went away without its action. */
    fun noticeDone(notice: EntryNotice) {
        mutableState.update { if (it.notice == notice) it.copy(notice = null) else it }
    }

    /** What [text] cuts the stopped [source] into, or null without an account timezone. */
    private fun splitOf(source: CachedTimeEntry, text: String): SplitResult? {
        val settings = accountSettings.value ?: return null
        val time = settings.time(clock) ?: return null
        val stop = source.stop ?: return null
        return splitTimes(text, source.start, stop, time, settings.twelveHour)
    }

    private fun queueDelete(entryId: Long, undo: Boolean) {
        scope.launch {
            if (changes.delete(entryId)) {
                mutableState.update { it.copy(notice = EntryNotice.Deleted(++serial, entryId, undo)) }
            }
        }
    }
}
