package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.UndoOutcome
import com.github.e0ipso.tokkl.entries.sessionEntries
import com.github.e0ipso.tokkl.entryfields.SplitProblem
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.MAX_FAVORITES
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.MainDispatcherRule
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.time.SnappedSpan
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The history's entry actions against a cache the test fills and a change queue and an Undo that
 * record what they are asked to do.
 */
class EntryActionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()
    private val scope = main.scope
    private val calls = CopyOnWriteArrayList<String>()

    @Volatile
    private var undoOutcome: UndoOutcome = UndoOutcome.Restored

    @Volatile
    private var cached = true

    /** The phone's storage refuses this queue call, as a full disk does, or none while null. */
    @Volatile
    private var refused: String? = null

    /** What escaped the actions' effects, which on the phone would reach the main thread and end the app. */
    private val escaped = CopyOnWriteArrayList<Throwable>()

    @Volatile
    private var favoritesAnswer: ApiResult<Unit> = ApiResult.Success(Unit)
    private val saved = CopyOnWriteArrayList<List<EntryFields>>()

    private val entries = MutableStateFlow(
        listOf(EntryWithSendState(STOPPED, unsent = true), EntryWithSendState(RUNNING, unsent = false)),
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun withRestoreADeleteGoesAtOnceAndOffersUndo() {
        val model = model(canRestore = true)

        onMain { model.delete(EntryId(ENTRY)) }

        val notice = awaitNotice(model) { it is EntryNotice.Deleted } as EntryNotice.Deleted
        assertEquals(EntryId(ENTRY) to true, notice.entryId to notice.undo)
        assertNull("nothing to confirm", model.state.value.confirmDelete)
        assertEquals(listOf("delete $ENTRY"), calls)
    }

    @Test
    fun withoutRestoreADeleteAsksFirstAndOffersNoUndo() {
        val model = model(canRestore = false)

        onMain { model.delete(EntryId(ENTRY)) }

        assertEquals(EntryActionsState(confirmDelete = EntryId(ENTRY)), model.state.value)
        assertEquals("nothing deleted before the answer", emptyList<String>(), calls)

        onMain { model.confirmDelete() }

        val notice = awaitNotice(model) { it is EntryNotice.Deleted } as EntryNotice.Deleted
        assertEquals(EntryId(ENTRY) to false, notice.entryId to notice.undo)
        assertNull(model.state.value.confirmDelete)
        assertEquals(listOf("delete $ENTRY"), calls)
    }

    @Test
    fun cancellingTheQuestionDeletesNothing() {
        val model = model(canRestore = false)

        onMain {
            model.delete(EntryId(ENTRY))
            model.cancelDelete()
            model.confirmDelete()
        }

        assertEquals(EntryActionsState(), model.state.value)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aDeleteOfAnEntryTheCacheNoLongerHoldsShowsNothing() {
        cached = false
        val model = model(canRestore = true)

        onMain { model.delete(EntryId(ENTRY)) }

        awaitCalls(1)
        onMain {}
        assertEquals(EntryActionsState(), model.state.value)
    }

    @Test
    fun undoTakesTheDeleteBackAndClearsTheMessage() {
        for (outcome in listOf(UndoOutcome.Cancelled, UndoOutcome.Restored)) {
            calls.clear()
            undoOutcome = outcome
            val model = model(canRestore = true)
            onMain { model.delete(EntryId(ENTRY)) }
            awaitNotice(model) { it is EntryNotice.Deleted }

            onMain { model.undo(EntryId(ENTRY)) }

            awaitCalls(2)
            onMain {}
            assertEquals("after $outcome", EntryActionsState(), model.state.value)
            assertEquals(listOf("delete $ENTRY", "undo $ENTRY"), calls)
        }
    }

    @Test
    fun undoOfflineSaysItNeedsTheServerAndCanBeTriedAgain() {
        undoOutcome = UndoOutcome.NeedsServer
        val model = model(canRestore = true)

        onMain { model.undo(EntryId(ENTRY)) }
        val first = awaitNotice(model) { it is EntryNotice.UndoNeedsServer } as EntryNotice.UndoNeedsServer
        assertEquals(EntryId(ENTRY), first.entryId)

        onMain { model.undo(EntryId(ENTRY)) }
        val second = awaitNotice(model) { it is EntryNotice.UndoNeedsServer && it != first }

        assertEquals("the same message shows again, as a new one", first.copy(serial = second.serial), second)
        assertEquals(listOf("undo $ENTRY", "undo $ENTRY"), calls)
    }

    @Test
    fun aRefusedRestoreSaysSo() {
        undoOutcome = UndoOutcome.Failed(404)
        val model = model(canRestore = true)

        onMain { model.undo(EntryId(ENTRY)) }

        val notice = awaitNotice(model) { it is EntryNotice.UndoFailed } as EntryNotice.UndoFailed
        assertEquals(404, notice.status)
    }

    @Test
    fun aDismissedMessageGoesAndALaterOneStays() {
        val model = model(canRestore = true)
        onMain { model.delete(EntryId(ENTRY)) }
        val first = awaitNotice(model) { it is EntryNotice.Deleted }
        onMain { model.delete(EntryId(OTHER)) }
        val second = awaitNotice(model) { it is EntryNotice.Deleted && it.entryId.value == OTHER }

        onMain { model.noticeDone(first) }
        assertEquals("a message that went already leaves the new one", second, model.state.value.notice)

        onMain { model.noticeDone(second) }
        assertNull(model.state.value.notice)
    }

    @Test
    fun duplicateCopiesTheFieldsAndTimesOfAStoppedEntry() {
        val model = model(canRestore = true)

        onMain { model.duplicate(STOPPED.id) }

        awaitNotice(model) { it is EntryNotice.Duplicated }
        val fields = EntryFields(
            description = "Code review",
            projectId = ProjectId(3),
            taskId = TaskId(4),
            tags = listOf("review", "remote"),
            billable = true,
        )
        assertEquals(
            "the source's workspace, fields, start and stop, as the web app posts them",
            listOf("create 10 $fields ${STOPPED.start} ${STOPPED.stop}"),
            calls,
        )
    }

    @Test
    fun aRunningEntryOrOneTheCacheDroppedMakesNoCopy() {
        val model = model(canRestore = true)

        onMain {
            model.duplicate(RUNNING.id)
            model.duplicate(EntryId(DROPPED))
        }
        onMain {}

        assertEquals(emptyList<String>(), calls)
        assertEquals(EntryActionsState(), model.state.value)
    }

    @Test
    fun duplicateOfANoTimeEntryStaysNoTimeOnItsStoredDay() {
        // The stored day is the 19th, though the start (22:00 UTC) is now midnight of the 20th in Madrid.
        val noTime = NO_TIME.copy(
            timing = EntryTiming.AllDay(
                LocalDate.of(2026, 10, 19),
                Instant.parse("2026-10-19T22:00:00Z"),
                Duration.ofMinutes(90),
            ),
        )
        entries.value = listOf(EntryWithSendState(noTime, unsent = false))
        val model = model(canRestore = true)

        onMain { model.duplicate(noTime.id) }

        awaitNotice(model) { it is EntryNotice.Duplicated }
        val fields = EntryFields(
            description = "Code review",
            projectId = ProjectId(3),
            taskId = TaskId(4),
            tags = listOf("review", "remote"),
            billable = true,
        )
        assertEquals(
            listOf("create 10 $fields ${noTime.start} ${noTime.stop} noTimeDay=2026-10-19"),
            calls,
        )
    }

    @Test
    fun duplicateOfANoTimeEntryWithoutAStoredDayTakesItsDayInTheAccountZone() {
        // Madrid midnight of the 19th, which is the 18th in UTC.
        val noTime = NO_TIME.copy(timing = (NO_TIME.timing as EntryTiming.AllDay).copy(day = null))
        entries.value = listOf(EntryWithSendState(noTime, unsent = false))
        val model = model(canRestore = true)

        onMain { model.duplicate(noTime.id) }

        awaitNotice(model) { it is EntryNotice.Duplicated }
        assertEquals(1, calls.size)
        assertEquals(true, calls.single().endsWith(" noTimeDay=2026-10-19"))
    }

    @Test
    fun splitOpensNothingForANoTimeEntry() {
        entries.value = listOf(EntryWithSendState(NO_TIME, unsent = false))
        val model = model(canRestore = true)

        onMain { model.split(NO_TIME.id) }
        onMain {}

        assertEquals(EntryActionsState(), model.state.value)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun splitCutsTheEntryInTwoThroughTheQueueAndTheHalvesAddUpToTheWhole() {
        val model = model(canRestore = true)

        onMain { model.split(STOPPED.id) }
        val dialog = awaitSplit(model)
        // At 15 minutes, 08:00:12 to 09:05:40 stores as 08:00 to 09:00, with each endpoint rounded independently.
        assertEquals(
            SnappedSpan(Instant.parse("2026-10-19T08:00:00Z"), Instant.parse("2026-10-19T09:00:00Z")),
            dialog.whole,
        )

        // 10:30 in Madrid, CEST.
        onMain { model.setSplitText("10:30") }
        val halves = model.state.value.split?.halves ?: throw AssertionError("no preview for 10:30")
        assertEquals(
            SnappedSpan(Instant.parse("2026-10-19T08:00:00Z"), Instant.parse("2026-10-19T08:30:00Z")),
            halves.first,
        )
        assertEquals(
            SnappedSpan(Instant.parse("2026-10-19T08:30:00Z"), Instant.parse("2026-10-19T09:00:00Z")),
            halves.second,
        )

        onMain { model.confirmSplit() }

        awaitNotice(model) { it is EntryNotice.Split }
        assertNull("the dialog closes", model.state.value.split)
        val fields = EntryFields(
            description = "Code review",
            projectId = ProjectId(3),
            taskId = TaskId(4),
            tags = listOf("review", "remote"),
            billable = true,
        )
        assertEquals(
            "one split with the source's fields: the original ends at the split, and the second half runs on",
            listOf(
                "split 70 $fields 2026-10-19T08:00:00Z 2026-10-19T08:30:00Z 2026-10-19T08:30:00Z 2026-10-19T09:00:00Z",
            ),
            calls,
        )
    }

    @Test
    fun aSplitTimeOutsideTheEntryOrUnreadableIsRefusedAndNothingIsQueued() {
        val model = model(canRestore = true)
        onMain { model.split(STOPPED.id) }
        awaitSplit(model)

        for ((text, problem) in listOf(
            "11:30" to SplitProblem.OUTSIDE,
            "10:00" to SplitProblem.OUTSIDE,
            "noon" to SplitProblem.UNREADABLE,
        )) {
            onMain { model.setSplitText(text) }
            assertNull("no preview for $text", model.state.value.split?.halves)
            onMain { model.confirmSplit() }
            awaitState(model) { it.split?.problem == problem }
            onMain { model.setSplitText(text) }
            assertNull("typing clears the problem", model.state.value.split?.problem)
        }

        onMain { model.cancelSplit() }
        assertEquals(EntryActionsState(), model.state.value)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aRunningEntryOrOneTheCacheDroppedOpensNoSplit() {
        val model = model(canRestore = true)

        onMain {
            model.split(RUNNING.id)
            model.split(EntryId(DROPPED))
        }
        onMain {}

        assertEquals(EntryActionsState(), model.state.value)
    }

    @Test
    fun whenTheOriginalLeftTheCacheNothingIsSplitAndTheDialogCloses() {
        cached = false
        val model = model(canRestore = true)
        onMain { model.split(STOPPED.id) }
        awaitSplit(model)
        onMain { model.setSplitText("10:30") }

        onMain { model.confirmSplit() }
        main.settle(POLL_MS)

        assertEquals(emptyList<String>(), calls)
        assertNull(model.state.value.split)
        assertNull(model.state.value.notice)
    }

    /**
     * A split whose write the storage refuses queues neither half and keeps the dialog with its
     * time, so Split can be tapped again.
     */
    @Test
    fun aSplitTheStorageRefusesQueuesNeitherHalfAndKeepsTheDialog() {
        refused = "update"
        val model = model(canRestore = true)
        onMain { model.split(STOPPED.id) }
        awaitSplit(model)
        onMain { model.setSplitText("10:30") }

        onMain { model.confirmSplit() }
        main.settle(POLL_MS)

        assertEquals("nothing queued", emptyList<String>(), calls)
        assertEquals("10:30", model.state.value.split?.text)
        assertNull(model.state.value.notice)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    /** A copy the storage refuses says nothing and throws nothing; Duplicate can be tapped again. */
    @Test
    fun aDuplicateTheStorageRefusesShowsNothingAndThrowsNothing() {
        refused = "create"
        val model = model(canRestore = true)

        onMain { model.duplicate(STOPPED.id) }
        main.settle(POLL_MS)

        assertNull(model.state.value.notice)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    /** A delete the storage refuses leaves the entry and lets Delete be tapped again. */
    @Test
    fun aDeleteTheStorageRefusesThrowsNothingAndCanBeTriedAgain() {
        refused = "delete"
        val model = model(canRestore = true)

        onMain { model.delete(EntryId(ENTRY)) }
        main.settle(POLL_MS)
        assertNull(model.state.value.notice)
        assertEquals(emptyList<Throwable>(), escaped.toList())

        refused = null
        onMain { model.delete(EntryId(ENTRY)) }
        awaitNotice(model) { it is EntryNotice.Deleted }
    }

    /** An Undo the storage refuses keeps the message that offers it, so Undo can be tapped again. */
    @Test
    fun anUndoTheStorageRefusesKeepsTheOfferAndThrowsNothing() {
        val model = model(canRestore = true)
        onMain { model.delete(EntryId(ENTRY)) }
        val offered = awaitNotice(model) { it is EntryNotice.Deleted }
        refused = "undo"

        onMain { model.undo(EntryId(ENTRY)) }
        main.settle(POLL_MS)

        assertEquals(offered, model.state.value.notice)
        assertEquals(emptyList<Throwable>(), escaped.toList())
    }

    @Test
    fun pinSavesTheEntrysFieldsAtTheEndOfTheFavoritesAndSaysSo() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        onMain { model.pin(STOPPED.id, favorites::add, offline = false) }

        awaitNotice(model) { it is EntryNotice.Pinned }
        val fields = EntryFields(
            description = "Code review",
            projectId = ProjectId(3),
            taskId = TaskId(4),
            tags = listOf("review", "remote"),
            billable = true,
        )
        assertEquals(listOf(listOf(PINNED.fields(), fields)), saved)
        assertEquals("nothing goes to the queue", emptyList<String>(), calls)
    }

    @Test
    fun offlineAPinTriesNothingAndSaysTheServerIsNeeded() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        onMain { model.pin(STOPPED.id, favorites::add, offline = true) }

        awaitNotice(model) { it is EntryNotice.PinNeedsServer }
        assertEquals(emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun withNineFavoritesAPinSaysTheListIsFull() {
        val favorites = favorites(
            (0 until MAX_FAVORITES).map { PINNED.copy(position = it, description = "Favorite $it") },
        )
        val model = model(canRestore = true)

        onMain { model.pin(STOPPED.id, favorites::add, offline = false) }

        awaitNotice(model) { it is EntryNotice.PinFull }
        assertEquals("nothing went to the server", emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun aServerThatCannotBeReachedOrDoesNotStoreThePinSaysWhy() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        favoritesAnswer = ApiResult.Failure(ApiError.Network(IOException("no route")))
        onMain { model.pin(STOPPED.id, favorites::add, offline = false) }
        awaitNotice(model) { it is EntryNotice.PinNeedsServer }

        favoritesAnswer = ApiResult.Failure(ApiError.AuthFailed)
        onMain { model.pin(STOPPED.id, favorites::add, offline = false) }
        val notice = awaitNotice(model) { it is EntryNotice.PinFailed } as EntryNotice.PinFailed
        assertEquals(ServerProblem.AuthFailed, notice.problem)
        assertEquals(
            "both tries left the favorites as they were",
            listOf(PINNED.fields()),
            favorites.state.value.favorites.map { it.fields },
        )
    }

    @Test
    fun anEntryTheCacheDroppedPinsNothing() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        onMain { model.pin(EntryId(DROPPED), favorites::add, offline = false) }
        // A notice after it shows that the pin of the dropped entry had its turn on the main thread.
        onMain { model.pin(STOPPED.id, favorites::add, offline = true) }

        assertEquals(EntryNotice.PinNeedsServer(1), awaitNotice(model) { true })
        // A save the first pin started would have run by now.
        onMain {}
        assertEquals(emptyList<List<EntryFields>>(), saved)
    }

    /** Favorites that hold [cachedFavorites] and save to [saved], shown as the Timer page shows them. */
    private fun favorites(cachedFavorites: List<Favorite>): FavoritesViewModel {
        val cachedFlow = MutableStateFlow(cachedFavorites)
        val model = FavoritesViewModel(
            favorites = cachedFlow,
            projects = MutableStateFlow(emptyList()),
            tasks = MutableStateFlow(emptyList()),
            available = MutableStateFlow(true),
            store = { list ->
                saved += list
                favoritesAnswer.also { answer ->
                    if (answer is ApiResult.Success) {
                        cachedFlow.value = list.mapIndexed { at, fields ->
                            Favorite(
                                at,
                                fields.description,
                                fields.projectId,
                                fields.taskId,
                                fields.tags,
                                fields.billable,
                            )
                        }
                    }
                }
            },
            scope = scope,
            picker = flowOf(PickerSource()),
            offline = MutableStateFlow(false),
            workspaceId = flowOf(null),
        )
        scope.launch { model.state.collect {} }
        main.onMain {
            withTimeoutOrNull(
                TIMEOUT_MS,
            ) { model.state.first { it.favorites.size == cachedFavorites.size && it.available } }
        } ?: throw AssertionError("the favorites never showed")
        return model
    }

    private fun Favorite.fields() = EntryFields(description, projectId, taskId, tags, billable)

    private fun awaitSplit(model: EntryActionsViewModel): SplitDialog = awaitState(model) { it.split != null }.split!!

    private fun awaitState(model: EntryActionsViewModel, condition: (EntryActionsState) -> Boolean): EntryActionsState {
        var last: EntryActionsState? = null
        return main.onMain {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError("timed out; the last state was $last, after $calls")
    }

    private fun model(canRestore: Boolean) = EntryActionsViewModel(
        entries = entries,
        changes = FakeChanges(),
        removals = sessionEntries(entries, MutableStateFlow(SETTINGS), scope, Clock.systemUTC()),
        undoDelete = { entryId ->
            refuse("undo")
            calls += "undo $entryId"
            undoOutcome
        },
        capabilities = MutableStateFlow(Capabilities.NONE.copy(restore = canRestore)),
        accountSettings = MutableStateFlow(SETTINGS),
        scope = scope + CoroutineExceptionHandler { _, thrown -> escaped += thrown },
        clock = Clock.systemUTC(),
    )

    private fun refuse(call: String) {
        if (refused == call) throw IOException("disk full")
    }

    private fun awaitNotice(model: EntryActionsViewModel, condition: (EntryNotice) -> Boolean): EntryNotice {
        var last: EntryActionsState? = null
        return main.onMain {
            withTimeoutOrNull(
                TIMEOUT_MS,
            ) { model.state.onEach { last = it }.first { it.notice?.let(condition) == true } }
        }?.notice ?: throw AssertionError("timed out; the last state was $last, after $calls")
    }

    private fun awaitCalls(count: Int) {
        var waited = 0L
        while (calls.size < count) {
            if (waited >= TIMEOUT_MS) throw AssertionError("timed out after $calls")
            main.settle(POLL_MS)
            waited += POLL_MS
        }
    }

    /** The model's handlers run on the main thread, which [main] stands in for. */
    private fun onMain(block: () -> Unit) {
        main.runCurrent()
        block()
        main.runCurrent()
    }

    private inner class FakeChanges : EntryChanges {
        override suspend fun delete(entryId: EntryId): Boolean {
            refuse("delete")
            calls += "delete $entryId"
            return cached
        }

        override suspend fun create(
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): EntryId {
            refuse("create")
            val start = timing.start
            val stop = timing.stop
            val noTimeDay = (timing as? EntryTiming.AllDay)?.day
            calls += "create $workspaceId $fields $start $stop" + (noTimeDay?.let { " noTimeDay=$it" } ?: "")
            return EntryId(-1)
        }

        override suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean {
            refuse("update")
            val start = timing.start
            val stop = (timing as? EntryTiming.Ended)?.stop
            calls += "update $entryId $fields $start $stop"
            return cached
        }

        override suspend fun split(
            entryId: EntryId,
            fields: EntryFields,
            first: EntryTiming.Stopped,
            second: EntryTiming.Stopped,
        ): Boolean {
            refuse("update")
            if (!cached) return false
            calls += "split $entryId $fields ${first.start} ${first.stop} ${second.start} ${second.stop}"
            return true
        }

        override suspend fun updateOlder(
            entryId: EntryId,
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): Unit = throw AssertionError("unexpected updateOlder")

        override suspend fun delete(entry: TimeEntry): TimeEntry? = if (delete(entry.id)) entry else null

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val ENTRY = 55L
        const val OTHER = 56L
        const val DROPPED = 57L

        /** An entry with a change the server lacks: the copy takes its times as the cache holds them. */
        val STOPPED = TimeEntry(
            id = EntryId(70),
            workspaceId = WorkspaceId(10),
            projectId = ProjectId(3),
            taskId = TaskId(4),
            description = "Code review",
            timing = EntryTiming.Stopped(
                Instant.parse("2026-10-19T08:00:12.250Z"),
                Instant.parse("2026-10-19T09:05:40.500Z"),
            ),
            billable = true,
            tags = listOf("review", "remote"),
            tagIds = listOf(TagId(1), TagId(2)),
        )

        val RUNNING = STOPPED.copy(id = EntryId(71), timing = EntryTiming.Running(STOPPED.start))

        /** A no-time entry on 19 October in Madrid: its start is local midnight, 22:00 UTC the day before. */
        val NO_TIME = STOPPED.copy(
            id = EntryId(72),
            timing = EntryTiming.AllDay(
                LocalDate.of(2026, 10, 19),
                Instant.parse("2026-10-18T22:00:00Z"),
                Duration.ofMinutes(90),
            ),
        )

        val PINNED = Favorite(
            0,
            "Write the report",
            projectId = null,
            taskId = null,
            tags = emptyList(),
            billable = false,
        )

        /** Madrid at a resolution of 15 minutes, on a 24-hour clock. */
        val SETTINGS = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = 15.0)
    }
}
