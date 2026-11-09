package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The tags page's model over a repository with one tag, Work, in workspace 7. Unconfined runs
 * each load to the end inside the call.
 */
class TagsViewModelTest {

    private val repository = OneTagRepository()
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: TagsViewModel
    private lateinit var screen: TestScreen

    @Before
    fun setUp() {
        model = TagsViewModel(
            repository = repository,
            workspaceId = { WorkspaceId(7) },
            refreshCache = {},
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedTags = flowOf(emptyList()),
            offline = MutableStateFlow(false),
            app = TestApp(scheduler).app,
        )
        screen = TestScreen(model.page, scope, scheduler)
    }

    /**
     * Another activity hides the page for longer than its state is shared. The open rename form
     * stays, and the page loads the tags again when it shows.
     */
    @Test
    fun theOpenFormSurvivesTheScreenHiddenForLonger() {
        screen.show()
        val work = model.state.value.data!!.tags.single()
        model.askToRename(work)

        screen.leave()
        screen.show()

        assertEquals(TagsOverlay.Rename(work), model.state.value.overlay)
        assertEquals(listOf(work), model.state.value.data?.tags)
        assertEquals(2, repository.loads)
    }

    /** Lists Work in workspace 7 and counts the loads; the changes are not sent. */
    private class OneTagRepository : TagsRepository {
        var loads = 0

        override suspend fun workspace(stored: WorkspaceId?) = ApiResult.Success(stored ?: WorkspaceId(7))

        override suspend fun load(workspace: WorkspaceId): ApiResult<TagsData> {
            loads++
            return ApiResult.Success(TagsData(listOf(Tag(TagId(3), workspace, "Work"))))
        }

        override suspend fun create(workspace: WorkspaceId, name: String, known: Set<TagId>) = notSent()

        override suspend fun rename(workspace: WorkspaceId, id: TagId, name: String) = notSent()

        override suspend fun delete(workspace: WorkspaceId, id: TagId): ApiResult<Unit> = notSent()

        private fun notSent(): Nothing = throw AssertionError("the test sends no change")
    }
}
