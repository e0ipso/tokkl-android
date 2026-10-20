package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The entry form's model, in Madrid, against entries the test puts in the cache and a change
 * queue that records what it is asked to queue. The web app's EntryEditor.tsx and the manual mode
 * of its TimerBar.tsx are the reference for what each set of fields saves.
 */
class EntryFormModelTest {

    private val main: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainDispatcher: ExecutorCoroutineDispatcher = main.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val entries = MutableStateFlow(listOf(EntryWithSendState(ENTRY, unsent = false)))
    private val settings = MutableStateFlow<AccountSettings?>(SETTINGS)
    private val changes = FakeChanges()
    private lateinit var model: EntryFormModel

    @Before
    fun showTheForm() {
        model = EntryFormModel(
            entries = entries,
            projects = MutableStateFlow(listOf(APP, CLIENT_WORK)),
            tasks = MutableStateFlow(listOf(DESIGN)),
            accountSettings = settings,
            changes = changes,
            scope = scope,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
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
    fun anEntryOpensWithItsFieldsOnTheAccountsClock() {
        val form = openEntry()

        assertEquals(
            EntryFormState.Open(
                editing = true,
                workspaceId = WORKSPACE,
                fields = EntryFields(description = "Write the form"),
                labels = EntryLabels(),
                original = Picked(),
                date = LocalDate.of(2026, 10, 14),
                start = "09:00",
                end = "10:30",
                duration = "1:30:25",
                mistakes = emptyMap(),
                endsOn = null,
                saving = false,
            ),
            form,
        )
    }

    @Test
    fun everyFieldChangesAndTheQueueGetsThemAll() {
        openEntry()
        onMain {
            model.setDescription("Edit the entry")
            model.pick(Picked(APP.id, DESIGN.id, listOf("call")))
            model.setBillable(true)
            model.setDate(LocalDate.of(2026, 10, 15))
            model.setStart("930")
            model.setEnd("4pm")
            model.timesDone()
        }
        // The names come from the cached projects and tasks, which the form reads once it shows.
        val form = awaitOpen("the duration follows the times and the names show") { it.duration == "6:30:00" && it.labels.task != null }
        assertEquals(EntryLabels(project = APP.name, projectColor = APP.color, task = DESIGN.name), form.labels)

        save()

        val fields = EntryFields("Edit the entry", APP.id, DESIGN.id, listOf("call"), billable = true)
        assertEquals(listOf("update $ENTRY_ID $fields 2026-10-15T07:30:00Z 2026-10-15T14:00:00Z"), changes.calls)
    }

    @Test
    fun aProjectThatIsBillableMakesTheEntryBillable() {
        entries.value = listOf(EntryWithSendState(ENTRY.copy(projectId = APP.id), unsent = false))
        openEntry()
        // The cached projects say which are billable once the form shows them.
        awaitOpen("the project's name shows") { it.labels.project == APP.name }
        onMain { model.pick(Picked(CLIENT_WORK.id)) }

        awaitOpen("the entry is billable") { it.fields.billable }
    }

    /** As in the web app, a time left as it opened is not sent back typed to the minute. */
    @Test
    fun timesLeftAsTheyOpenedKeepTheEntrysOwnInstants() {
        openEntry()
        onMain { model.setDescription("Only the description") }

        save()

        assertEquals(listOf("update $ENTRY_ID ${ENTRY_FIELDS.copy(description = "Only the description")} ${ENTRY.start} ${ENTRY.stop}"), changes.calls)
    }

    /** As in the web app's editor: the stop is the start plus the duration, and the end shows it. */
    @Test
    fun aChangedDurationSetsTheStopFromTheStart() {
        openEntry()
        onMain {
            model.setDuration("2h")
            model.durationDone()
        }
        val form = awaitOpen("the end follows the duration") { it.end == "11:00" }
        assertEquals("2:00:00", form.duration)

        save()

        assertEquals(listOf("update $ENTRY_ID $ENTRY_FIELDS ${ENTRY.start} ${ENTRY.start.plusSeconds(2 * 3600)}"), changes.calls)
    }

    @Test
    fun anEndEarlierOnTheClockThanTheStartIsOnTheNextDay() {
        openEntry()
        onMain {
            model.setStart("23:00")
            model.setEnd("1")
            model.timesDone()
        }
        val form = awaitOpen("the duration crosses midnight") { it.duration == "2:00:00" }
        assertEquals(LocalDate.of(2026, 10, 15), form.endsOn)

        save()

        assertEquals(listOf("update $ENTRY_ID $ENTRY_FIELDS 2026-10-14T21:00:00Z 2026-10-14T23:00:00Z"), changes.calls)
    }

    @Test
    fun timesThatCannotBeReadAreMarkedAndNothingIsQueued() {
        openEntry()
        onMain {
            model.setStart("soon")
            model.save()
        }
        awaitOpen("the start is marked") { it.mistakes == mapOf(TimeField.START to TimeMistake.TIMES) }

        onMain {
            model.setStart("9")
            model.setEnd("later")
            model.save()
        }
        awaitOpen("the end is marked") { it.mistakes == mapOf(TimeField.END to TimeMistake.TIMES) }

        onMain {
            model.setEnd("10")
            model.setDuration("a while")
            model.save()
        }
        awaitOpen("the duration is marked") { it.mistakes == mapOf(TimeField.DURATION to TimeMistake.DURATION) }

        onMain { model.setDuration("1h") }
        awaitOpen("typing clears the mark") { it.mistakes.isEmpty() }
        assertEquals("nothing queued", emptyList<String>(), changes.calls)
    }

    /**
     * The server refuses an entry that stops before it starts, and one longer than 999 hours. Since
     * tokkl a4b825d parseDuration reads a duration over 999 hours as no duration, so the web app
     * asks to check the duration, and so does the form.
     */
    @Test
    fun spansTheServerRefusesAreMarkedAndNothingIsQueued() {
        openEntry()
        // The end stays at 10:30, before the new start.
        onMain {
            model.setStart("11")
            model.save()
        }
        awaitOpen("the end is marked") { it.mistakes == mapOf(TimeField.END to TimeMistake.END_BEFORE_START) }

        onMain {
            model.setStart("09:00")
            model.setDuration("1000h")
            model.save()
        }
        awaitOpen("the duration is marked") { it.mistakes == mapOf(TimeField.DURATION to TimeMistake.DURATION) }
        assertEquals("nothing queued", emptyList<String>(), changes.calls)
    }

    /** As the history shows it: the times the server will store, snapped in the account's timezone. */
    @Test
    fun anUnsentEntryOpensWithTheTimesTheServerWillStore() {
        settings.value = SETTINGS.copy(timeResolutionMinutes = 15.0)
        val raw = ENTRY.copy(start = Instant.parse("2026-10-14T07:07:31.500Z"), stop = Instant.parse("2026-10-14T08:01:02.250Z"))
        entries.value = listOf(EntryWithSendState(raw, unsent = true))

        val form = openEntry()

        assertEquals("09:15", form.start)
        assertEquals("10:15", form.end)
        assertEquals("1:00:00", form.duration)
    }

    @Test
    fun twelveHourAccountsSeeAndTypeTheirOwnClock() {
        settings.value = SETTINGS.copy(twelveHour = true)
        val form = openEntry()
        assertEquals("9:00 AM", form.start)
        assertEquals("10:30 AM", form.end)

        // Without AM or PM, the end is the first 11 after the start.
        onMain {
            model.setEnd("11")
            model.timesDone()
        }
        awaitOpen("the duration follows the end") { it.duration == "2:00:00" }

        save()

        assertEquals(listOf("update $ENTRY_ID $ENTRY_FIELDS ${ENTRY.start} 2026-10-14T09:00:00Z"), changes.calls)
    }

    @Test
    fun anEntryThatIsNotOnThePhoneOrRunsCannotBeEdited() {
        onMain { model.edit(404) }
        awaitState("an entry the cache lacks is missing") { it == EntryFormState.Missing }

        entries.value = listOf(EntryWithSendState(ENTRY.copy(stop = null), unsent = false))
        onMain { model.edit(ENTRY_ID) }
        awaitState("the running entry is the timer's") { it == EntryFormState.Missing }
    }

    @Test
    fun anEntryTheCacheDroppedBeforeTheSaveIsMissing() {
        openEntry()
        changes.updates = false
        onMain { model.setDescription("Too late") }

        onMain { model.save() }

        awaitState("the form says the entry is gone") { it == EntryFormState.Missing }
    }

    @Test
    fun withoutTheAccountsTimezoneTheFormTakesNoTimes() {
        settings.value = SETTINGS.copy(timeZone = "Mars/Olympus_Mons")
        onMain { model.edit(ENTRY_ID) }

        awaitState("the form names the zone") { it == EntryFormState.NoAccountTime("Mars/Olympus_Mons") }
    }

    @Test
    fun aPastEntryOpensOnTodayInTheAccountsTimezone() {
        assertEquals(
            EntryFormState.Open(
                editing = false,
                workspaceId = WORKSPACE,
                fields = EntryFields(),
                labels = EntryLabels(),
                original = Picked(),
                date = LocalDate.of(2026, 10, 19),
                start = "",
                end = "",
                duration = "",
                mistakes = emptyMap(),
                endsOn = null,
                saving = false,
            ),
            openNew(),
        )
    }

    /**
     * The examples of docs/PRD.md M1 save what the web app saves: its manual mode for a start and
     * an end, and its editor's start plus the duration for a duration.
     */
    @Test
    fun everyM1ExampleSavesWhatTheWebAppSaves() {
        for (case in listOf(
            Typed(start = "9", end = "930", stop = "2026-10-19T07:30:00Z", from = "2026-10-19T07:00:00Z"),
            Typed(start = "9:30", end = "4pm", stop = "2026-10-19T14:00:00Z", from = "2026-10-19T07:30:00Z"),
            Typed(start = "930", duration = "1h 30m", stop = "2026-10-19T09:00:00Z", from = "2026-10-19T07:30:00Z"),
            Typed(start = "9", duration = "90", stop = "2026-10-19T08:30:00Z", from = "2026-10-19T07:00:00Z"),
            Typed(start = "9", duration = "1,5h", stop = "2026-10-19T08:30:00Z", from = "2026-10-19T07:00:00Z"),
            Typed(start = "23:00", end = "1:00", stop = "2026-10-19T23:00:00Z", from = "2026-10-19T21:00:00Z"),
        )) {
            changes.calls.clear()
            openNew()
            onMain {
                model.setStart(case.start)
                case.end?.let(model::setEnd)
                case.duration?.let(model::setDuration)
                model.save()
            }
            awaitState("$case saves") { it == EntryFormState.Saved }
            assertEquals("$case", listOf("create $WORKSPACE ${EntryFields()} ${case.from} ${case.stop}"), changes.calls)
        }
    }

    @Test
    fun anEntryFrom23To01EndsOnTheNextDay() {
        openNew()
        onMain {
            model.setStart("23:00")
            model.setEnd("01:00")
            model.timesDone()
        }
        val form = awaitOpen("the duration crosses midnight") { it.duration == "2:00:00" }
        assertEquals(LocalDate.of(2026, 10, 20), form.endsOn)

        save()

        assertEquals(listOf("create $WORKSPACE ${EntryFields()} 2026-10-19T21:00:00Z 2026-10-19T23:00:00Z"), changes.calls)
    }

    @Test
    fun theDurationFillsInTheEndAndTheTimesTheDuration() {
        openNew()
        onMain {
            model.setStart("9")
            model.setDuration("1h 30m")
            model.durationDone()
        }
        awaitOpen("the end follows the duration") { it.end == "10:30" && it.duration == "1:30:00" }

        onMain {
            model.setEnd("11")
            model.timesDone()
        }
        awaitOpen("the duration follows the end") { it.duration == "2:00:00" }
        save()

        assertEquals(listOf("create $WORKSPACE ${EntryFields()} 2026-10-19T07:00:00Z 2026-10-19T09:00:00Z"), changes.calls)
    }

    /** zonedToUtc's rules, which time.test.ts pins, decide the clock changes, as on the web app. */
    @Test
    fun wallTimesOnTheDaysTheClocksChangeResolveAsTokklResolvesThem() {
        for (case in listOf(
            // Spring: 01:30 CET to 03:30 CEST is one hour.
            Typed(LocalDate.of(2026, 3, 29), "1:30", end = "3:30", from = "2026-03-29T00:30:00Z", stop = "2026-03-29T01:30:00Z"),
            // 02:30 does not exist then, so it lands after the gap, at 03:30 CEST.
            Typed(LocalDate.of(2026, 3, 29), "2:30", end = "4", from = "2026-03-29T01:30:00Z", stop = "2026-03-29T02:00:00Z"),
            // Autumn: 02:30 happens twice, and the later one, in CET, is the one.
            Typed(LocalDate.of(2026, 10, 25), "2:30", end = "3", from = "2026-10-25T01:30:00Z", stop = "2026-10-25T02:00:00Z"),
        )) {
            changes.calls.clear()
            openNew()
            onMain {
                model.setDate(case.date)
                model.setStart(case.start)
                model.setEnd(case.end!!)
                model.save()
            }
            awaitState("$case saves") { it == EntryFormState.Saved }
            assertEquals("$case", listOf("create $WORKSPACE ${EntryFields()} ${case.from} ${case.stop}"), changes.calls)
        }
    }

    /** As the web app guesses for a 12-hour clock: the start nearer to now, then the first end after it. */
    @Test
    fun twelveHourAccountsGetTheNearerStartAndTheFirstEndAfterIt() {
        settings.value = SETTINGS.copy(twelveHour = true)
        openNew()
        onMain {
            // At noon, 4 is nearer as 4 PM, and 5 after it is 5 PM.
            model.setStart("4")
            model.setEnd("5")
        }

        save()

        assertEquals(listOf("create $WORKSPACE ${EntryFields()} 2026-10-19T14:00:00Z 2026-10-19T15:00:00Z"), changes.calls)
    }

    @Test
    fun aPastEntryWithoutReadableTimesIsMarkedInTheManualFormsWords() {
        openNew()
        onMain { model.save() }
        awaitOpen("the start is marked") { it.mistakes == mapOf(TimeField.START to TimeMistake.MANUAL_TIMES) }

        onMain {
            model.setStart("9")
            model.save()
        }
        awaitOpen("the end is marked") { it.mistakes == mapOf(TimeField.END to TimeMistake.MANUAL_TIMES) }
        assertEquals("nothing queued", emptyList<String>(), changes.calls)
    }

    @Test
    fun aPastEntryWaitsForTheAccountsWorkspace() {
        settings.value = SETTINGS.copy(defaultWorkspaceId = null)
        openNew()
        onMain {
            model.setStart("9")
            model.setEnd("10")
            model.save()
        }

        val form = awaitOpen("the form stays open") { it.workspaceId == null }
        assertEquals(false, form.saving)
        assertEquals("nothing queued", emptyList<String>(), changes.calls)
    }

    private fun openNew(): EntryFormState.Open {
        onMain { model.new() }
        return awaitOpen("the form for a past entry opens") { !it.editing && it.start.isEmpty() }
    }

    /** What a test types into the form, and the span it saves. */
    private data class Typed(
        val date: LocalDate = LocalDate.of(2026, 10, 19),
        val start: String,
        val end: String? = null,
        val duration: String? = null,
        val from: String,
        val stop: String,
    )

    private fun openEntry(): EntryFormState.Open {
        onMain { model.edit(ENTRY_ID) }
        return awaitOpen("the form opens") { true }
    }

    private fun save() {
        onMain { model.save() }
        awaitState("the form saved") { it == EntryFormState.Saved }
    }

    private fun awaitOpen(what: String, condition: (EntryFormState.Open) -> Boolean): EntryFormState.Open =
        awaitState(what) { it is EntryFormState.Open && condition(it) } as EntryFormState.Open

    private fun awaitState(what: String, condition: (EntryFormState) -> Boolean): EntryFormState {
        var last: EntryFormState? = null
        return runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.onEach { last = it }.first(condition) }
        } ?: throw AssertionError("timed out waiting until $what; the last state was $last, after ${changes.calls}")
    }

    /** The form's handlers run on the main thread, which [scope] stands in for. */
    private fun onMain(block: () -> Unit) = runBlocking(mainDispatcher) { block() }

    private class FakeChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        /** What update answers: false once the cache has dropped the entry. */
        @Volatile
        var updates = true

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
            calls += "update $entryId $fields $start $stop"
            return updates
        }

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            calls += "create $workspaceId $fields $start $stop"
            return -1
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("the form starts no timer")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("the form stops no timer")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("the form deletes nothing")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val WORKSPACE = 10L
        const val ENTRY_ID = 7L

        /** 12:00 in Madrid. */
        val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val SETTINGS = AccountSettings(
            timeZone = MADRID.id,
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
            defaultWorkspaceId = WORKSPACE,
        )
        val APP = CachedProject(1, WORKSPACE, clientId = null, name = "Tokkl for Android", color = "#0b83d9", active = true, billable = false)
        val CLIENT_WORK = CachedProject(2, WORKSPACE, clientId = null, name = "Client work", color = "#d94182", active = true, billable = true)
        val DESIGN = CachedTask(5, WORKSPACE, projectId = APP.id, name = "Design", active = true)

        /** 09:00:20 to 10:30:45 in Madrid on Wednesday 14 October 2026. */
        val ENTRY = CachedTimeEntry(
            id = ENTRY_ID,
            workspaceId = WORKSPACE,
            projectId = null,
            taskId = null,
            description = "Write the form",
            start = Instant.parse("2026-10-14T07:00:20Z"),
            stop = Instant.parse("2026-10-14T08:30:45Z"),
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        )
        val ENTRY_FIELDS = EntryFields(description = "Write the form")
    }
}
