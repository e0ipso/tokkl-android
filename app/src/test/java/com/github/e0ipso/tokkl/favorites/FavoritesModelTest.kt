package com.github.e0ipso.tokkl.favorites

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.entry.EntryLabels
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The favorites' model against a cache the test sets and a server that stores what it gets, or
 * fails as the test says. Every edit saves the whole list, in the web app's format.
 */
class FavoritesModelTest {

    private val main: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainDispatcher: ExecutorCoroutineDispatcher = main.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val cached = MutableStateFlow(listOf(REPORT, WEBSITE))
    private val available = MutableStateFlow(true)
    private val saved = CopyOnWriteArrayList<List<EntryFields>>()

    @Volatile
    private var answer: ApiResult<Unit> = ApiResult.Success(Unit)
    private lateinit var model: FavoritesModel

    @Before
    fun showTheFavorites() {
        model = FavoritesModel(
            favorites = cached,
            projects = MutableStateFlow(listOf(APP)),
            tasks = MutableStateFlow(listOf(DESIGN)),
            available = available,
            save = { list ->
                saved += list
                answer.also { if (it is ApiResult.Success) cached.value = list.mapIndexed { at, fields -> fields.cached(at) } }
            },
            scope = scope,
            io = mainDispatcher,
        )
        // The screen collects the state while it shows.
        scope.launch { model.state.collect {} }
    }

    @After
    fun tearDown() {
        scope.cancel()
        main.shutdown()
    }

    @Test
    fun theCachedFavoritesShowInOrderWithTheirProjects() {
        val shown = awaitState("the favorites show") { it.favorites.size == 2 }
        assertTrue(shown.available)
        assertEquals(
            listOf(
                FavoriteItem(REPORT.fields(), EntryLabels(project = APP.name, projectColor = APP.color, task = DESIGN.name)),
                FavoriteItem(WEBSITE.fields(), EntryLabels()),
            ),
            shown.favorites,
        )
    }

    @Test
    fun withoutTheCapabilityNothingShowsAndNothingChanges() {
        available.value = false
        awaitState("the favorites are hidden") { !it.available && it.favorites.size == 2 }
        onMain { model.add(EntryFields(description = "New")) }
        onMain { model.remove(0) }
        Thread.sleep(SETTLE_PAUSE_MS)
        assertEquals(emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun addingPinsAtTheEndAndSavesTheWholeList() {
        awaitState("the favorites show") { it.favorites.size == 2 }
        val pinned = EntryFields(description = "Pinned", projectId = APP.id, tags = listOf("call"), billable = true)
        onMain { model.add(pinned) }
        val shown = awaitState("the new favorite shows") { it.favorites.size == 3 && !it.saving }
        assertEquals(listOf(listOf(REPORT.fields(), WEBSITE.fields(), pinned)), saved)
        assertEquals(pinned, shown.favorites.last().fields)
        assertNull(shown.problem)
    }

    @Test
    fun nineFavoritesAreTheMost() {
        cached.value = (0 until MAX_FAVORITES).map { EntryFields(description = "Favorite $it").cached(it) }
        assertTrue(awaitState("nine favorites show") { it.favorites.size == MAX_FAVORITES }.full)
        onMain { model.add(EntryFields(description = "Tenth")) }
        assertEquals(FavoritesProblem.Full, awaitState("the list says it is full") { it.problem != null }.problem)
        assertEquals("nothing went to the server", emptyList<List<EntryFields>>(), saved)
    }

    @Test
    fun movingAndRemovingSaveTheNewOrder() {
        awaitState("the favorites show") { it.favorites.size == 2 }
        onMain { model.move(1, later = false) }
        awaitState("the order changes") { it.favorites.map { item -> item.fields } == listOf(WEBSITE.fields(), REPORT.fields()) && !it.saving }
        onMain { model.move(1, later = true) }
        onMain { model.remove(0) }
        awaitState("one favorite is left") { it.favorites.size == 1 && !it.saving }
        assertEquals(
            "moving the last one later changes nothing, so only two lists were saved",
            listOf(listOf(WEBSITE.fields(), REPORT.fields()), listOf(REPORT.fields())),
            saved,
        )
    }

    @Test
    fun replacingSavesTheEditedTemplateInPlace() {
        awaitState("the favorites show") { it.favorites.size == 2 }
        val edited = REPORT.fields().copy(description = "Rewrite the report", billable = false)
        onMain { model.replace(0, edited) }
        val shown = awaitState("the edited template shows") {
            it.favorites.first().fields.description == "Rewrite the report" && !it.saving
        }
        assertEquals(listOf(listOf(edited, WEBSITE.fields())), saved)
        assertEquals(edited, shown.favorites.first().fields)
        assertEquals(WEBSITE.fields(), shown.favorites[1].fields)
        assertNull(shown.problem)
    }

    @Test
    fun aChangeTheServerDoesNotStoreLeavesTheListAndSaysWhy() {
        awaitState("the favorites show") { it.favorites.size == 2 }
        answer = ApiResult.Failure(ApiError.Network(IOException("offline")))
        onMain { model.remove(0) }
        val shown = awaitState("the problem shows") { it.problem != null }
        assertEquals(FavoritesProblem.Server(ServerProblem.Unreachable), shown.problem)
        assertEquals(listOf(REPORT.fields(), WEBSITE.fields()), shown.favorites.map { it.fields })
        assertFalse(shown.saving)
    }

    /** The history's Save as template learns how each add went, to say so. */
    @Test
    fun anAddTellsItsCallerHowItWent() {
        awaitState("the favorites show") { it.favorites.size == 2 }
        val outcomes = MutableStateFlow(emptyList<FavoritesProblem?>())
        val record: (FavoritesProblem?) -> Unit = { problem -> outcomes.update { it + problem } }

        onMain { model.add(EntryFields(description = "Stored"), record) }
        assertEquals(listOf<FavoritesProblem?>(null), awaitOutcomes(outcomes, 1))

        answer = ApiResult.Failure(ApiError.Network(IOException("offline")))
        onMain { model.add(EntryFields(description = "Not stored"), record) }
        assertEquals(FavoritesProblem.Server(ServerProblem.Unreachable), awaitOutcomes(outcomes, 2)[1])

        cached.value = (0 until MAX_FAVORITES).map { EntryFields(description = "Favorite $it").cached(it) }
        awaitState("nine favorites show") { it.full }
        onMain { model.add(EntryFields(description = "Tenth"), record) }
        assertEquals(FavoritesProblem.Full, awaitOutcomes(outcomes, 3)[2])
        assertEquals("only the first two went to the server", 2, saved.size)
    }

    private fun awaitOutcomes(outcomes: MutableStateFlow<List<FavoritesProblem?>>, count: Int): List<FavoritesProblem?> =
        runBlocking { withTimeoutOrNull(TIMEOUT_MS) { outcomes.first { it.size >= count } } }
            ?: throw AssertionError("timed out waiting for $count outcomes; got ${outcomes.value}")

    /** The body Tokkl's web app sends from TimerPage.tsx: the favorites key alone, with every field of each. */
    @Test
    fun theSavedBodyIsTheWebAppsFormat() {
        val body = favoritesBody(listOf(REPORT.fields(), WEBSITE.fields()))
        assertEquals(
            Json.parseToJsonElement(
                """
                {"favorites": [
                  {"description": "Write the report", "project_id": 1, "task_id": 5, "tags": ["call", "deep work"], "billable": true},
                  {"description": "Website", "project_id": null, "task_id": null, "tags": [], "billable": false}
                ]}
                """,
            ),
            body,
        )
    }

    private fun awaitState(what: String, condition: (FavoritesState) -> Boolean): FavoritesState {
        var last: FavoritesState? = null
        return runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError("timed out waiting until $what; the last state was $last, after saving $saved")
    }

    /** The tap handlers run on the main thread, which [scope] stands in for. */
    private fun onMain(block: () -> Unit) = runBlocking(mainDispatcher) { block() }

    private fun EntryFields.cached(position: Int) = CachedFavorite(position, description, projectId, taskId, tags, billable)

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_PAUSE_MS = 300L
        const val WORKSPACE = 10L
        val APP = CachedProject(1, WORKSPACE, clientId = null, name = "Tokkl for Android", color = "#0b83d9", active = true, billable = false)
        val DESIGN = CachedTask(5, WORKSPACE, projectId = APP.id, name = "Design", active = true)
        val REPORT = CachedFavorite(0, "Write the report", APP.id, DESIGN.id, listOf("call", "deep work"), billable = true)
        val WEBSITE = CachedFavorite(1, "Website", projectId = null, taskId = null, tags = emptyList(), billable = false)
    }
}
