package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeSender.UndoOutcome
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.entryfields.SplitProblem
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.MAX_FAVORITES
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.MainDispatcherRule
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.SnappedSpan
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The history's entry actions against a cache the test fills and a change queue and an Undo that
 * record what they are asked to do. With Tokkl's restore endpoint a delete offers Undo at once, as
 * the web app's EntryList.tsx does; without it the delete asks first and offers none. Duplicate
 * copies a stopped entry's fields and times, as the web app's duplicate does, and Split cuts one
 * in two at a clock time in the account's timezone, as the web app's editor does. Save as template
 * saves the entry's fields at the end of the timer templates, as the web app's EntryList.tsx does, and
 * says why when it saves nothing.
 */
class EntryActionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()
    private val mainDispatcher = main.dispatcher
    private val scope = main.scope
    private val calls = CopyOnWriteArrayList<String>()

    @Volatile
    private var undoOutcome: UndoOutcome = UndoOutcome.Restored

    @Volatile
    private var cached = true

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

        onMain { model.delete(ENTRY) }

        val notice = awaitNotice(model) { it is EntryNotice.Deleted } as EntryNotice.Deleted
        assertEquals(ENTRY to true, notice.entryId to notice.undo)
        assertNull("nothing to confirm", model.state.value.confirmDelete)
        assertEquals(listOf("delete $ENTRY"), calls)
    }

    @Test
    fun withoutRestoreADeleteAsksFirstAndOffersNoUndo() {
        val model = model(canRestore = false)

        onMain { model.delete(ENTRY) }

        assertEquals(EntryActionsState(confirmDelete = ENTRY), model.state.value)
        assertEquals("nothing deleted before the answer", emptyList<String>(), calls)

        onMain { model.confirmDelete() }

        val notice = awaitNotice(model) { it is EntryNotice.Deleted } as EntryNotice.Deleted
        assertEquals(ENTRY to false, notice.entryId to notice.undo)
        assertNull(model.state.value.confirmDelete)
        assertEquals(listOf("delete $ENTRY"), calls)
    }

    @Test
    fun cancellingTheQuestionDeletesNothing() {
        val model = model(canRestore = false)

        onMain {
            model.delete(ENTRY)
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

        onMain { model.delete(ENTRY) }

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
            onMain { model.delete(ENTRY) }
            awaitNotice(model) { it is EntryNotice.Deleted }

            onMain { model.undo(ENTRY) }

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

        onMain { model.undo(ENTRY) }
        val first = awaitNotice(model) { it is EntryNotice.UndoNeedsServer } as EntryNotice.UndoNeedsServer
        assertEquals(ENTRY, first.entryId)

        onMain { model.undo(ENTRY) }
        val second = awaitNotice(model) { it is EntryNotice.UndoNeedsServer && it != first }

        assertEquals("the same message shows again, as a new one", first.copy(serial = second.serial), second)
        assertEquals(listOf("undo $ENTRY", "undo $ENTRY"), calls)
    }

    @Test
    fun aRefusedRestoreSaysSo() {
        undoOutcome = UndoOutcome.Failed(404)
        val model = model(canRestore = true)

        onMain { model.undo(ENTRY) }

        val notice = awaitNotice(model) { it is EntryNotice.UndoFailed } as EntryNotice.UndoFailed
        assertEquals(404, notice.status)
    }

    @Test
    fun aDismissedMessageGoesAndALaterOneStays() {
        val model = model(canRestore = true)
        onMain { model.delete(ENTRY) }
        val first = awaitNotice(model) { it is EntryNotice.Deleted }
        onMain { model.delete(OTHER) }
        val second = awaitNotice(model) { it is EntryNotice.Deleted && it.entryId == OTHER }

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
            projectId = 3,
            taskId = 4,
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
            model.duplicate(DROPPED)
        }
        onMain {}

        assertEquals(emptyList<String>(), calls)
        assertEquals(EntryActionsState(), model.state.value)
    }

    @Test
    fun duplicateOfANoTimeEntryStaysNoTimeOnItsStoredDay() {
        // The stored day is the 19th, though the start (22:00 UTC) is now midnight of the 20th in Madrid.
        val noTime = NO_TIME.copy(
            day = LocalDate.of(2026, 10, 19),
            start = Instant.parse("2026-10-19T22:00:00Z"),
            stop = Instant.parse("2026-10-19T23:30:00Z"),
        )
        entries.value = listOf(EntryWithSendState(noTime, unsent = false))
        val model = model(canRestore = true)

        onMain { model.duplicate(noTime.id) }

        awaitNotice(model) { it is EntryNotice.Duplicated }
        val fields = EntryFields(
            description = "Code review",
            projectId = 3,
            taskId = 4,
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
        val noTime = NO_TIME.copy(day = null)
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
            projectId = 3,
            taskId = 4,
            tags = listOf("review", "remote"),
            billable = true,
        )
        assertEquals(
            "the second half first, with the source's workspace and fields, then the original ends at the split",
            listOf(
                "create 10 $fields 2026-10-19T08:30:00Z 2026-10-19T09:00:00Z",
                "update 70 $fields 2026-10-19T08:00:00Z 2026-10-19T08:30:00Z",
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
            model.split(DROPPED)
        }
        onMain {}

        assertEquals(EntryActionsState(), model.state.value)
    }

    @Test
    fun whenTheOriginalLeftTheCacheTheSecondHalfGoesToo() {
        cached = false
        val model = model(canRestore = true)
        onMain { model.split(STOPPED.id) }
        awaitSplit(model)
        onMain { model.setSplitText("10:30") }

        onMain { model.confirmSplit() }

        awaitCalls(3)
        assertEquals("delete -1", calls.last())
        assertNull(model.state.value.notice)
    }

    @Test
    fun pinSavesTheEntrysFieldsAtTheEndOfTheFavoritesAndSaysSo() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        onMain { model.pin(STOPPED.id, favorites, offline = false) }

        awaitNotice(model) { it is EntryNotice.Pinned }
        val fields = EntryFields(
            description = "Code review",
            projectId = 3,
            taskId = 4,
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

        onMain { model.pin(STOPPED.id, favorites, offline = true) }

        awaitNotice(model) { it is EntryNotice.PinNeedsServer }
        assertEquals(emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun withNineFavoritesAPinSaysTheListIsFull() {
        val favorites = favorites(
            (0 until MAX_FAVORITES).map { PINNED.copy(position = it, description = "Favorite $it") },
        )
        val model = model(canRestore = true)

        onMain { model.pin(STOPPED.id, favorites, offline = false) }

        awaitNotice(model) { it is EntryNotice.PinFull }
        assertEquals("nothing went to the server", emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun aServerThatCannotBeReachedOrDoesNotStoreThePinSaysWhy() {
        val favorites = favorites(listOf(PINNED))
        val model = model(canRestore = true)

        favoritesAnswer = ApiResult.Failure(ApiError.Network(IOException("no route")))
        onMain { model.pin(STOPPED.id, favorites, offline = false) }
        awaitNotice(model) { it is EntryNotice.PinNeedsServer }

        favoritesAnswer = ApiResult.Failure(ApiError.AuthFailed)
        onMain { model.pin(STOPPED.id, favorites, offline = false) }
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

        onMain { model.pin(DROPPED, favorites, offline = false) }
        // A notice after it shows that the pin of the dropped entry had its turn on the main thread.
        onMain { model.pin(STOPPED.id, favorites, offline = true) }

        assertEquals(EntryNotice.PinNeedsServer(1), awaitNotice(model) { true })
        // A save the first pin started would have run by now.
        onMain {}
        assertEquals(emptyList<List<EntryFields>>(), saved)
    }

    /** Favorites that hold [cachedFavorites] and save to [saved], shown as the Timer page shows them. */
    private fun favorites(cachedFavorites: List<CachedFavorite>): FavoritesViewModel {
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
                            CachedFavorite(
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
            io = mainDispatcher,
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

    private fun CachedFavorite.fields() = EntryFields(description, projectId, taskId, tags, billable)

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
        undo = { entryId ->
            calls += "undo $entryId"
            undoOutcome
        },
        canRestore = { canRestore },
        accountSettings = MutableStateFlow(SETTINGS),
        scope = scope,
        clock = Clock.systemUTC(),
    )

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
        override suspend fun delete(entryId: Long): Boolean {
            calls += "delete $entryId"
            return cached
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError(
            "no timer starts",
        )

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("no timer stops")

        override suspend fun create(
            workspaceId: Long,
            fields: EntryFields,
            start: Instant,
            stop: Instant,
            noTimeDay: LocalDate?,
        ): Long {
            calls += "create $workspaceId $fields $start $stop" + (noTimeDay?.let { " noTimeDay=$it" } ?: "")
            return -1
        }

        override suspend fun update(
            entryId: Long,
            fields: EntryFields,
            start: Instant,
            stop: Instant?,
            noTimeDay: LocalDate?,
        ): Boolean {
            calls += "update $entryId $fields $start $stop"
            return cached
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val ENTRY = 55L
        const val OTHER = 56L
        const val DROPPED = 57L

        /** An entry with a change the server lacks: the copy takes its times as the cache holds them. */
        val STOPPED = CachedTimeEntry(
            id = 70,
            workspaceId = 10,
            projectId = 3,
            taskId = 4,
            description = "Code review",
            start = Instant.parse("2026-10-19T08:00:12.250Z"),
            stop = Instant.parse("2026-10-19T09:05:40.500Z"),
            billable = true,
            tags = listOf("review", "remote"),
            tagIds = listOf(1, 2),
        )

        val RUNNING = STOPPED.copy(id = 71, stop = null)

        /** A no-time entry on 19 October in Madrid: its start is local midnight, 22:00 UTC the day before. */
        val NO_TIME = STOPPED.copy(
            id = 72,
            start = Instant.parse("2026-10-18T22:00:00Z"),
            stop = Instant.parse("2026-10-18T23:30:00Z"),
            allDay = true,
            day = LocalDate.of(2026, 10, 19),
        )

        val PINNED = CachedFavorite(
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
