package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.timerControls
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The timer's model against a running entry that the test sets, as the cache would show it, and
 * a change queue that writes it, and can wait as a slow write to the phone's database would. Each
 * tap must change what the timer shows before the queue has written anything.
 */
class TimerModelTest {

    private val main: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainDispatcher: ExecutorCoroutineDispatcher = main.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val now = AtomicReference(TAP)
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val running = MutableStateFlow<EntryWithSendState?>(null)
    private val settings = MutableStateFlow<AccountSettings?>(SETTINGS)
    private val changes = FakeChanges()
    private val taps = CopyOnWriteArrayList<String>()
    private val trace = TapTrace { section ->
        taps += "begin $section"
        val end = { taps += "end $section" }
        end
    }
    private lateinit var model: TimerModel

    @Before
    fun showTheTimer() {
        model = TimerModel(
            controls = running.map { timerControls(it, chain = null, session = 0) },
            projects = MutableStateFlow(listOf(APP, CLIENT_WORK)),
            tasks = MutableStateFlow(listOf(DESIGN)),
            accountSettings = settings,
            changes = changes,
            scope = scope,
            clock = clock,
            trace = trace,
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
    fun startShowsTheDraftRunningAtOnceAndQueuesItAtTheTapTime() {
        awaitIdle()
        onMain {
            model.editDescription("Write the timer")
            model.setBillable(true)
        }
        assertEquals("the idle timer keeps a draft on the phone alone", emptyList<String>(), changes.calls)

        changes.gate = CompletableDeferred()
        onMain { model.start() }
        val shown = awaitState("the draft runs before the queue has written it") { it is TimerState.Running }
        val draft = EntryFields(description = "Write the timer", billable = true)
        assertEquals(
            TimerState.Running(
                entryId = null,
                workspaceId = WORKSPACE,
                start = TAP,
                fields = draft,
                labels = EntryLabels(),
                original = Picked(),
                unsent = true,
            ),
            shown,
        )
        assertEquals("the queue read the tap time on the tap", listOf("start $WORKSPACE $draft at $TAP"), changes.calls)

        changes.gate.complete(Unit)
        val cached = awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null } as TimerState.Running
        assertEquals(TAP, cached.start)
        assertEquals(draft, cached.fields)

        onMain { model.stop() }
        assertEquals("Start's draft has gone", EntryFields(), (awaitIdle()).fields)
    }

    @Test
    fun aSecondStartBeforeTheCacheShowsTheFirstIsIgnored() {
        awaitIdle()
        changes.gate = CompletableDeferred()
        onMain {
            model.start()
            model.start()
        }
        awaitState("the first start shows") { it is TimerState.Running }
        onMain { model.start() }
        changes.gate.complete(Unit)
        awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null }
        onMain { model.start() }

        assertEquals("one entry started", 1, changes.calls.count { it.startsWith("start") })
    }

    @Test
    fun startAndStopTapsAreTracedUntilTheScreenShowsWhatTheyDid() {
        val idle = awaitIdle()
        changes.gate = CompletableDeferred()
        onMain {
            model.start()
            model.start()
        }
        assertEquals("one section for the Start that acted", listOf("begin TimerStart"), taps)
        onMain { model.shown(idle) }
        assertEquals("the idle timer is not what Start did", listOf("begin TimerStart"), taps)

        val starting = awaitState("the draft runs") { it is TimerState.Running }
        onMain { model.shown(starting) }
        assertEquals(listOf("begin TimerStart", "end TimerStart"), taps)
        changes.gate.complete(Unit)
        val cached = awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null }
        onMain { model.shown(cached) }
        assertEquals("a state with no tap open ends nothing", 2, taps.size)

        onMain { model.stop() }
        onMain { model.shown(cached) }
        assertEquals("the running timer is not what Stop did", listOf("begin TimerStart", "end TimerStart", "begin TimerStop"), taps)
        val stopped = awaitIdle()
        onMain { model.shown(stopped) }
        assertEquals(listOf("begin TimerStart", "end TimerStart", "begin TimerStop", "end TimerStop"), taps)
    }

    @Test
    fun aTapTheScreenNeverShowedEndsAtTheNextTap() {
        awaitIdle()
        onMain { model.start() }
        awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null }
        onMain { model.stop() }
        assertEquals(listOf("begin TimerStart", "end TimerStart", "begin TimerStop"), taps)
    }

    @Test
    fun startWaitsForTheAccountsWorkspace() {
        settings.value = SETTINGS.copy(defaultWorkspaceId = null)
        awaitState("the idle timer has no workspace") { it is TimerState.Idle && it.workspaceId == null }

        onMain { model.start() }

        assertEquals(emptyList<String>(), changes.calls)
        assertTrue(model.state.value is TimerState.Idle)
    }

    @Test
    fun stopShowsTheIdleTimerAtOnceAndTakesTheDescriptionBeingTyped() {
        running.value = cachedRunning(description = "Review")
        awaitState("the cached running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.editDescription("Review the queue") }
        awaitState("the typed description shows") { it is TimerState.Running && it.fields.description == "Review the queue" }
        assertEquals("typing queues nothing", emptyList<String>(), changes.calls)

        changes.gate = CompletableDeferred()
        onMain { model.stop() }

        val idle = awaitState("the idle timer shows before the queue has written the stop") { it is TimerState.Idle } as TimerState.Idle
        assertEquals(EntryFields(), idle.fields)
        assertEquals(
            "the stop carries the description in the same change",
            listOf("stop $RUNNING_ID ${cachedRunning(description = "Review the queue").entry.fields()}"),
            changes.calls,
        )
        changes.gate.complete(Unit)
        awaitCache("the cache has no running entry") { it == null }
        awaitIdle()
    }

    @Test
    fun aDescriptionGoesToTheQueueOnceTypingEnds() {
        running.value = cachedRunning(description = "", billable = true, tags = listOf("research"))
        awaitState("the cached running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }

        onMain {
            model.editDescription("R")
            model.editDescription("Research")
        }
        awaitState("the typed description shows") { it is TimerState.Running && it.fields.description == "Research" }
        assertEquals(emptyList<String>(), changes.calls)

        onMain { model.descriptionDone() }
        awaitCache("the cache has the description") { it?.description == "Research" }
        onMain { model.descriptionDone() }

        val sent = EntryFields(description = "Research", projectId = APP.id, tags = listOf("research"), billable = true)
        assertEquals("one update with every field", listOf("update $RUNNING_ID $sent"), changes.calls)
    }

    @Test
    fun aRefreshCannotTakeBackWhatIsBeingTypedButShowsOnceItIsSent() {
        running.value = cachedRunning(description = "Old")
        awaitState("the cached running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.editDescription("Typed on the phone") }
        awaitState("the typed description shows") { it is TimerState.Running && it.fields.description == "Typed on the phone" }

        // A refresh brings the server's description while the user types.
        running.value = cachedRunning(description = "Changed on the web")
        Thread.sleep(SETTLE_PAUSE_MS)
        assertEquals("Typed on the phone", (model.state.value as TimerState.Running).fields.description)

        onMain { model.descriptionDone() }
        awaitCache("the cache has the description") { it?.description == "Typed on the phone" }
        // The next refresh brings a later change from the web.
        running.value = cachedRunning(description = "Changed on the web again")
        awaitState("the web's later change shows") { it is TimerState.Running && it.fields.description == "Changed on the web again" }
    }

    @Test
    fun picksAndBillableGoToTheQueueAtOnceWithEveryField() {
        running.value = cachedRunning(description = "Plan", projectId = null)
        awaitState("the cached running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.editDescription("Plan the week") }

        changes.gate = CompletableDeferred()
        onMain { model.setBillable(true) }
        awaitState("billable shows before the queue has written it") { it is TimerState.Running && it.fields.billable }
        changes.gate.complete(Unit)
        awaitCache("the cache is billable") { it?.billable == true }

        onMain { model.pick(Picked(projectId = APP.id, taskId = DESIGN.id, tags = listOf("planning"))) }
        val shown = awaitState("the project and task show") { it is TimerState.Running && it.labels.task != null } as TimerState.Running

        assertEquals(
            listOf(
                "update $RUNNING_ID ${EntryFields(description = "Plan the week", billable = true)}",
                "update $RUNNING_ID ${EntryFields("Plan the week", APP.id, DESIGN.id, listOf("planning"), billable = true)}",
            ),
            changes.calls,
        )
        assertEquals(EntryLabels(project = APP.name, projectColor = APP.color, task = DESIGN.name), shown.labels)
    }

    @Test
    fun pickingABillableProjectMakesTheEntryBillableAndTagsAloneDoNot() {
        awaitIdle()
        onMain { model.pick(Picked(tags = listOf("call"))) }
        awaitState("the tag shows") { it is TimerState.Idle && it.fields.tags == listOf("call") }
        assertEquals(false, (model.state.value as TimerState.Idle).fields.billable)

        onMain { model.pick(Picked(projectId = CLIENT_WORK.id, tags = listOf("call"))) }
        val idle = awaitState("the project shows") { it is TimerState.Idle && it.fields.projectId == CLIENT_WORK.id } as TimerState.Idle
        assertEquals(true, idle.fields.billable)
        assertEquals(EntryLabels(project = CLIENT_WORK.name, projectColor = CLIENT_WORK.color), idle.labels)
    }

    @Test
    fun theTimerFollowsTheCachesRunningEntry() {
        awaitIdle()
        running.value = cachedRunning(description = "Started on the web")
        val shown = awaitState("the entry started elsewhere shows") { it is TimerState.Running } as TimerState.Running
        assertEquals("Started on the web", shown.fields.description)
        assertEquals(Picked(projectId = APP.id), shown.original)

        running.value = null
        awaitIdle()
        assertEquals(emptyList<String>(), changes.calls)
    }

    @Test
    fun aFavoriteStartsAtOnceAndTheDraftGoes() {
        awaitIdle()
        onMain { model.editDescription("A draft") }
        val favorite = EntryFields(description = "Write the report", projectId = APP.id, tags = listOf("call"), billable = true)

        changes.gate = CompletableDeferred()
        onMain { model.startFrom(favorite) }
        val shown = awaitState("the favorite runs before the queue has written it") { it is TimerState.Running } as TimerState.Running
        assertEquals(favorite, shown.fields)
        assertEquals(TAP, shown.start)
        assertEquals("the queue read the tap time on the tap", listOf("start $WORKSPACE $favorite at $TAP"), changes.calls)

        changes.gate.complete(Unit)
        awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null }
        onMain { model.stop() }
        assertEquals("the draft has gone, as after Start", EntryFields(), awaitIdle().fields)
    }

    /**
     * While an entry runs, a favorite starts anyway, and the description still being typed for
     * the running entry reaches it before the start, which stops it.
     */
    @Test
    fun aFavoriteStartsWhileAnEntryRunsAfterItsTypedDescription() {
        running.value = cachedRunning("Old")
        awaitState("the running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.editDescription("Old, typed") }
        val favorite = EntryFields(description = "Write the report")

        onMain { model.startFrom(favorite) }
        awaitCache("the favorite runs") { it?.description == "Write the report" }
        assertEquals(
            listOf("update $RUNNING_ID ${cachedRunning("Old, typed").entry.fields()}", "start $WORKSPACE $favorite at $TAP"),
            changes.calls,
        )
        val shown = awaitState("the favorite shows running") { it is TimerState.Running && it.entryId != RUNNING_ID } as TimerState.Running
        assertEquals(favorite, shown.fields)
    }

    @Test
    fun aFavoriteWaitsForTheWorkspace() {
        settings.value = null
        awaitIdle()
        onMain { model.startFrom(EntryFields(description = "Write the report")) }
        Thread.sleep(SETTLE_PAUSE_MS)
        assertEquals(emptyList<String>(), changes.calls)
    }

    /** A picked suggestion fills every field, as the web app's pickSuggestion does. */
    @Test
    fun aSuggestionFillsEveryFieldOfTheDraftAndOfTheRunningEntry() {
        awaitIdle()
        val picked = EntryFields(description = "Write the report", projectId = APP.id, taskId = DESIGN.id, tags = listOf("call"), billable = true)
        onMain {
            model.editDescription("rep")
            model.fill(picked)
        }
        awaitState("the draft has every picked field") { it is TimerState.Idle && it.fields == picked }
        assertEquals("the idle timer keeps its draft on the phone", emptyList<String>(), changes.calls)

        running.value = cachedRunning("Old")
        awaitState("the running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.fill(picked) }
        awaitCache("the running entry has every field") { it?.fields() == picked }
        assertEquals(listOf("update $RUNNING_ID $picked"), changes.calls)
    }

    @Test
    fun theClockCountsWholeSecondsFromTheStart() {
        val start = Instant.parse("2026-10-19T09:00:00Z")
        assertEquals(0L, elapsedSeconds(start, start.plusMillis(999)))
        assertEquals(1L, elapsedSeconds(start, start.plusMillis(1_000)))
        assertEquals(3_725L, elapsedSeconds(start, Instant.parse("2026-10-19T10:02:05.5Z")))
        assertEquals("a start after the phone's clock shows zero", 0L, elapsedSeconds(start, start.minusSeconds(5)))
    }

    @Test
    fun continueStartsThePastEntrysFieldsInItsWorkspaceAndStopsTheRunningOne() {
        running.value = cachedRunning("Write the timer")
        awaitState("the running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }

        changes.gate = CompletableDeferred()
        onMain { model.continueEntry(PAST) }
        val shown = awaitState("the continued entry runs before the queue has written it") {
            it is TimerState.Running && it.entryId == null
        } as TimerState.Running
        assertEquals(PAST.fields(), shown.fields)
        assertEquals("the past entry's workspace, where its project and tags are", OTHER_WORKSPACE, shown.workspaceId)
        assertEquals(TAP, shown.start)
        assertEquals(
            "one queued start, which stops the running entry as the server does",
            listOf("start $OTHER_WORKSPACE ${PAST.fields()} at $TAP"),
            changes.calls,
        )

        changes.gate.complete(Unit)
        val cached = awaitState("the cache's entry shows") { it is TimerState.Running && it.entryId != null } as TimerState.Running
        assertEquals(PAST.fields(), cached.fields)
    }

    @Test
    fun aSecondContinueBeforeTheCacheShowsTheFirstIsIgnored() {
        awaitIdle()
        changes.gate = CompletableDeferred()

        onMain {
            model.continueEntry(PAST)
            model.continueEntry(PAST)
            model.start()
        }

        assertEquals(listOf("start $OTHER_WORKSPACE ${PAST.fields()} at $TAP"), changes.calls)
        changes.gate.complete(Unit)
    }

    @Test
    fun continueKeepsTheDescriptionBeingTypedForTheRunningEntry() {
        running.value = cachedRunning("Write the timer")
        awaitState("the running entry shows") { it is TimerState.Running && it.entryId == RUNNING_ID }
        onMain { model.editDescription("Write the timer model") }

        onMain { model.continueEntry(PAST) }

        awaitState("the continued entry shows") { it is TimerState.Running && it.entryId != null && it.entryId != RUNNING_ID }
        val typed = cachedRunning("Write the timer").entry.fields().copy(description = "Write the timer model")
        assertEquals(
            "the typed description goes to the queue before the start stops its entry",
            listOf("update $RUNNING_ID $typed", "start $OTHER_WORKSPACE ${PAST.fields()} at $TAP"),
            changes.calls,
        )
    }

    @Test
    fun continueBringsTheDraftBackWhenNothingStarts() {
        awaitIdle()
        onMain { model.editDescription("A draft") }
        changes.failStart = true

        onMain { model.continueEntry(PAST) }

        val idle = awaitState("the idle timer shows again") { it is TimerState.Idle && it.fields.description.isNotEmpty() } as TimerState.Idle
        assertEquals(EntryFields(description = "A draft"), idle.fields)
    }

    /**
     * Start, Continue and a favorite each announce their start once the queue has it, for the
     * notification permission's question, and a start the queue refused announces nothing.
     */
    @Test
    fun eachStartMadeHereIsAnnouncedAfterTheQueueHasIt() {
        val announced = CopyOnWriteArrayList<Int>()
        onMain {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                model.starts.collect { announced += changes.calls.count { it.startsWith("start") } }
            }
        }
        awaitIdle()

        changes.gate = CompletableDeferred()
        onMain { model.start() }
        awaitState("the draft runs") { it is TimerState.Running }
        Thread.sleep(SETTLE_PAUSE_MS)
        assertEquals("nothing is announced while the queue writes the start", emptyList<Int>(), announced)
        changes.gate.complete(Unit)
        awaitAnnounced(announced, listOf(1))

        tapUntilTaken { model.continueEntry(PAST) }
        awaitAnnounced(announced, listOf(1, 2))

        tapUntilTaken { model.startFrom(EntryFields(description = "Write the report")) }
        awaitAnnounced(announced, listOf(1, 2, 3))

        changes.failStart = true
        tapUntilTaken { model.continueEntry(PAST) }
        Thread.sleep(SETTLE_PAUSE_MS)
        assertEquals("a refused start is not announced", listOf(1, 2, 3), announced)
    }

    @Test
    fun aStartMadeElsewhereDoesNotAnnounceAnInAppStart() {
        awaitIdle()
        onMain { running.value = EntryWithSendState(PAST.copy(stop = null), unsent = false) }
        awaitState("the external running entry") { it is TimerState.Running }
        assertEquals(emptyList<Unit>(), model.starts.replayCache)
    }

    @Test
    fun aStartWaitsForACollectorWithoutHoldingUpTheTimer() {
        awaitIdle()
        onMain { model.start() }
        awaitState("the queued start") { it is TimerState.Running && it.entryId != null }
        assertEquals(listOf(Unit), model.starts.replayCache)
    }

    private fun awaitAnnounced(announced: List<Int>, expected: List<Int>) {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (announced != expected) {
            if (System.nanoTime() > deadline) throw AssertionError("announced $announced, not $expected, after ${changes.calls}")
            Thread.sleep(POLL_MS)
        }
    }

    /** Taps until the model takes the tap, which it ignores while the last start is pending. */
    private fun tapUntilTaken(tap: () -> Unit) {
        val before = changes.calls.size
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (true) {
            onMain(tap)
            if (changes.calls.size > before) return
            if (System.nanoTime() > deadline) throw AssertionError("the tap was never taken, after ${changes.calls}")
            Thread.sleep(POLL_MS)
        }
    }

    private fun awaitIdle(): TimerState.Idle = awaitState("the idle timer shows") { it is TimerState.Idle } as TimerState.Idle

    private fun awaitState(what: String, condition: (TimerState) -> Boolean): TimerState {
        var last: TimerState? = null
        return runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError("timed out waiting until $what; the last state was $last, after ${changes.calls}")
    }

    /** Waits until the running entry the cache shows passes [condition]. */
    private fun awaitCache(what: String, condition: (CachedTimeEntry?) -> Boolean) {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (!condition(running.value?.entry)) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting until $what, after ${changes.calls}")
            Thread.sleep(POLL_MS)
        }
    }

    /** The tap handlers run on the main thread, which [scope] stands in for. */
    private fun onMain(block: () -> Unit) = runBlocking(mainDispatcher) { block() }

    private fun cachedRunning(
        description: String,
        projectId: Long? = APP.id,
        tags: List<String> = emptyList(),
        billable: Boolean = false,
    ) = EntryWithSendState(
        CachedTimeEntry(
            id = RUNNING_ID,
            workspaceId = WORKSPACE,
            projectId = projectId,
            taskId = null,
            description = description,
            start = Instant.parse("2026-10-19T09:00:00Z"),
            stop = null,
            billable = billable,
            tags = tags,
            tagIds = emptyList(),
        ),
        unsent = false,
    )

    private fun CachedTimeEntry.fields() = EntryFields(description, projectId, taskId, tags, billable)

    /**
     * The change queue: each call reads the tap time first, then waits for [gate], then changes
     * the running entry the cache shows.
     */
    private inner class FakeChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)
        private var nextLocalId = -1L

        /** Start throws, as a write to the phone's database that fails would. */
        @Volatile
        var failStart = false

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long {
            val at = clock.instant()
            calls += "start $workspaceId $fields at $at"
            gate.await()
            if (failStart) throw IllegalStateException("the database refused the start")
            val id = nextLocalId--
            running.value = EntryWithSendState(
                CachedTimeEntry(id, workspaceId, fields.projectId, fields.taskId, fields.description, at, null, fields.billable, fields.tags, emptyList()),
                unsent = true,
            )
            return id
        }

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
            calls += "stop $entryId $fields"
            gate.await()
            if (running.value?.entry?.id != entryId) return false
            running.value = null
            return true
        }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
            calls += "update $entryId $fields"
            gate.await()
            val current = running.value?.entry?.takeIf { it.id == entryId } ?: return false
            running.value = EntryWithSendState(
                current.copy(
                    description = fields.description,
                    projectId = fields.projectId,
                    taskId = fields.taskId,
                    tags = fields.tags,
                    billable = fields.billable,
                    start = start,
                    stop = stop,
                ),
                unsent = true,
            )
            return true
        }

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("the timer creates no stopped entry")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("the timer deletes nothing")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Long enough for the model to show a cache change, which it must not. */
        const val SETTLE_PAUSE_MS = 300L
        const val POLL_MS = 10L
        const val WORKSPACE = 10L
        const val RUNNING_ID = 42L
        val TAP: Instant = Instant.parse("2026-10-19T10:00:00.250Z")
        val SETTINGS = AccountSettings(
            timeZone = "Europe/Madrid",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
            defaultWorkspaceId = WORKSPACE,
        )
        val APP = CachedProject(1, WORKSPACE, clientId = null, name = "Tokkl for Android", color = "#0b83d9", active = true, billable = false)
        val CLIENT_WORK = CachedProject(2, WORKSPACE, clientId = null, name = "Client work", color = "#d94182", active = true, billable = true)
        val DESIGN = CachedTask(5, WORKSPACE, projectId = APP.id, name = "Design", active = true)
        const val OTHER_WORKSPACE = 20L

        /** A stopped entry in a workspace other than the default one, which Continue starts again. */
        val PAST = CachedTimeEntry(
            id = 7,
            workspaceId = OTHER_WORKSPACE,
            projectId = CLIENT_WORK.id,
            taskId = DESIGN.id,
            description = "Review the mockups",
            start = Instant.parse("2026-10-18T08:00:00Z"),
            stop = Instant.parse("2026-10-18T09:30:00Z"),
            billable = true,
            tags = listOf("review", "remote"),
            tagIds = emptyList(),
        )
    }
}
