package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.ServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The trash page's model over a repository that answers each call as the test sets it, a 404
 * for anything it does not. Unconfined runs each call to its end inside the call.
 */
class TrashViewModelTest {

    private val trash = AnsweringTrash()
    private var cacheRefreshes = 0
    private var missing = 0

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val screen by lazy { TestScreen(model.page, scope, scheduler) }
    private val model by lazy {
        TrashViewModel(
            repository = trash,
            refreshCache = { cacheRefreshes++ },
            trashMissing = { missing++ },
            scope = scope,
            io = Dispatchers.Unconfined,
            settings = MutableStateFlow(null),
            offline = MutableStateFlow(false),
            app = TestApp(scheduler).app,
        )
    }

    @Test
    fun openingListsTheDeletedEntries() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write"), entry(2, null)))

        screen.show()

        assertEquals(listOf(1L, 2L), model.state.value.entries?.map { it.id.value })
        assertEquals("Write", model.state.value.entries?.first()?.description)
        assertEquals("Website", model.state.value.entries?.first()?.projectName)
        assertTrue(model.state.value.canChange)
    }

    @Test
    fun aServerWithoutTheTrashHidesItInsteadOfFailing() {
        screen.show()

        assertEquals("the 404 told the capabilities", 1, missing)
        assertNull(model.state.value.entries)
    }

    @Test
    fun anAnswerThatIsNotAListHidesTheTrashToo() {
        trash.list = ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("not a list")))

        screen.show()

        assertEquals(1, missing)
    }

    @Test
    fun anUnreachableServerTurnsEveryActionOff() {
        trash.list = ApiResult.Failure(ApiError.Network(IOException("no route")))

        screen.show()

        assertEquals(ServerProblem.Unreachable, model.state.value.loadProblem)
        assertFalse(model.state.value.canChange)
        assertEquals("an unreachable server is no missing trash", 0, missing)
    }

    @Test
    fun restoreTakesTheEntryOutOfTheTrashAndRefreshesTheCache() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write"), entry(2, "Read")))
        trash.restored[EntryId(1)] = ApiResult.Success(Unit)
        screen.show()

        model.restore(EntryId(1))

        assertEquals(listOf(2L), model.state.value.entries?.map { it.id.value })
        assertEquals(TrashChange.Restored, model.state.value.done)
        assertEquals(1, cacheRefreshes)
    }

    @Test
    fun purgeDeletesOneEntryAtOnce() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write"), entry(2, "Read")))
        trash.purged[EntryId(2)] = ApiResult.Success(1)
        screen.show()

        model.purge(EntryId(2))

        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
        assertEquals(TrashChange.Purged, model.state.value.done)
        assertEquals("a purge leaves the cache alone", 0, cacheRefreshes)
    }

    @Test
    fun aPurgeOfAnEntryNoLongerInTheTrashLoadsTheListAgain() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write")))
        trash.purged[EntryId(1)] = ApiResult.Failure(ApiError.UnexpectedStatus(404, "Time entry not found in trash"))
        screen.show()
        trash.list = ApiResult.Success(emptyList())

        model.purge(EntryId(1))

        assertEquals(ServerProblem.Refused("Time entry not found in trash"), model.state.value.changeProblem)
        assertEquals(emptyList<TrashEntry>(), model.state.value.entries)
        assertEquals("a missing entry is no missing trash", 0, missing)
    }

    @Test
    fun emptyingSendsNothingUntilTheQuestionIsAnswered() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write"), entry(2, "Read")))
        trash.purgedAll = ApiResult.Success(2)
        screen.show()

        model.empty()
        assertFalse(PURGE_ALL in trash.calls)

        model.askToEmpty()
        assertTrue(model.state.value.confirmingEmpty)
        model.empty()

        assertTrue(PURGE_ALL in trash.calls)
        assertEquals(emptyList<TrashEntry>(), model.state.value.entries)
        assertFalse(model.state.value.confirmingEmpty)
        assertEquals(TrashChange.Emptied(2), model.state.value.done)
    }

    @Test
    fun anEmptyTrashAsksNothing() {
        trash.list = ApiResult.Success(emptyList())
        screen.show()

        model.askToEmpty()

        assertFalse(model.state.value.confirmingEmpty)
    }

    @Test
    fun cancellingTheQuestionKeepsTheTrash() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write")))
        screen.show()
        model.askToEmpty()

        model.dismiss()
        model.empty()

        assertFalse(PURGE_ALL in trash.calls)
        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
    }

    /**
     * Another activity hides the page for longer than its state is shared. The question whether
     * to empty the trash stays, and the page loads the trash again when it shows.
     */
    @Test
    fun theQuestionSurvivesTheScreenHiddenForLonger() {
        trash.list = ApiResult.Success(listOf(entry(1, "Write")))
        screen.show()
        model.askToEmpty()

        screen.leave()
        screen.show()

        assertTrue(model.state.value.confirmingEmpty)
        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
        assertEquals(2, trash.calls.count { it == LIST })
    }

    /** A deleted entry as Tokkl lists it. */
    private fun entry(id: Long, description: String?) = TrashEntry(
        id = EntryId(id),
        description = description,
        start = "2026-10-19T09:00:00+00:00",
        stop = "2026-10-19T10:30:00+00:00",
        duration = 5400,
        projectId = 3,
        projectName = "Website",
        deletedAt = "2026-10-20T08:00:00+00:00",
    )

    /** Answers each call as set, and a 404 as a server without the trash does otherwise. */
    private class AnsweringTrash : TrashRepository {
        var list: ApiResult<List<TrashEntry>> = NOT_FOUND
        val restored = mutableMapOf<EntryId, ApiResult<Unit>>()
        val purged = mutableMapOf<EntryId, ApiResult<Int>>()
        var purgedAll: ApiResult<Int> = NOT_FOUND
        val calls = mutableListOf<String>()

        override suspend fun list(): ApiResult<List<TrashEntry>> = list.also { calls += LIST }

        override suspend fun restore(id: EntryId): ApiResult<Unit> =
            (restored[id] ?: NOT_FOUND).also { calls += "restore $id" }

        override suspend fun purge(id: EntryId): ApiResult<Int> =
            (purged[id] ?: NOT_FOUND).also { calls += "purge $id" }

        override suspend fun purgeAll() = purgedAll.also { calls += PURGE_ALL }
    }

    private companion object {
        const val LIST = "list"
        const val PURGE_ALL = "purgeAll"
        val NOT_FOUND = ApiResult.Failure(ApiError.UnexpectedStatus(404, "Not found"))
    }
}
