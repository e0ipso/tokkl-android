package com.github.e0ipso.tokkl.pomodoro

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The Pomodoro page as the controller of the server's session: the start form and the open pomodoro's fields. */
class PomodoroViewModelTest {
    private val fixtures = mutableListOf<PageFixture>()
    private fun idle() = PageFixture().also { fixtures += it }
    private fun running(description: String = "Focused work") = PageFixture(
        heldState(revision = 4, description = description),
        cachedEntry(description),
    ).also { fixtures += it }

    /** What a new process gets back of [saved]: only what it holds. */
    private fun afterProcessDeath(saved: SavedStateHandle) =
        SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })

    @After
    fun stop() = fixtures.forEach { it.close() }

    @Test
    fun theFormStartsFromTheDefaultsAndEditingItLeavesThemAlone() {
        val f = idle()
        assertEquals(PomodoroLengths("40", "8", "20", "3"), f.page.state.value.lengths)
        f.page.editLengths(PomodoroLengths("30", "5", "12", "2"))
        assertEquals(PomodoroDefaults(40, 8, 20, 3), f.defaults.value)
        f.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals("a changed default does not overwrite what the user typed", "30", f.page.state.value.lengths.work)
    }

    @Test
    fun thePageNamesTheFieldsProjectAndTaskAndPickingABillableProjectMakesThemBillable() {
        val f = idle()
        val drawn = f.scope.launch { f.page.page.collect {} }
        f.picker.value = PickerSource(
            projects = listOf(
                Project(ProjectId(7), WorkspaceId(WORKSPACE), null, "Ops", "#d94182", active = true, billable = true),
            ),
            tasks = listOf(Task(TaskId(70), WorkspaceId(WORKSPACE), ProjectId(7), "Triage", active = true)),
        )
        f.page.pick(
            EntryFields(description = "Rota"),
            Picked(projectId = ProjectId(7), taskId = TaskId(70)),
            entryId = null,
        )
        assertEquals(
            EntryFields(description = "Rota", projectId = ProjectId(7), taskId = TaskId(70), billable = true),
            f.page.state.value.fields,
        )
        assertEquals(EntryLabels(project = "Ops", projectColor = "#d94182", task = "Triage"), f.page.page.value.labels)
        drawn.cancel()
    }

    /**
     * The process died with the start form typed in: the page it restores shows the fields and the
     * lengths as they were typed, and lengths left alone follow the defaults again.
     */
    @Test
    fun theStartFormComesBackAsTypedWhenANewProcessRestoresThePage() {
        val f = idle()
        f.page.editFields(EntryFields(description = "Plan", tags = listOf("deep")), entryId = null)
        f.page.editLengths(PomodoroLengths("30", "5", "12", "2"))

        val restored = PageFixture(saved = afterProcessDeath(f.saved)).also { fixtures += it }

        assertEquals(EntryFields(description = "Plan", tags = listOf("deep")), restored.page.state.value.fields)
        assertEquals(PomodoroLengths("30", "5", "12", "2"), restored.page.state.value.lengths)
        val untouched = PageFixture(saved = afterProcessDeath(idle().saved)).also { fixtures += it }
        untouched.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals(PomodoroLengths("45", "9", "20", "3"), untouched.page.state.value.lengths)
    }

    @Test
    fun aDefaultChangeReachesTheFormUntilTheUserEditsIt() {
        val f = idle()
        f.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals(PomodoroLengths("45", "9", "20", "3"), f.page.state.value.lengths)
    }

    @Test
    fun rangesMatchWhatTheServerAcceptsAndAnyOtherLengthBlocksStart() {
        val f = idle()
        assertEquals(
            PomodoroLengths(
                "180",
                "60",
                "60",
                "12",
            ).sessionLengths()!!.run { listOf(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) },
            listOf(180, 60, 60, 12),
        )
        assertNotNull(PomodoroLengths("1", "1", "1", "1").sessionLengths())
        for (bad in listOf(
            PomodoroLengths("181", "5", "15", "4"),
            PomodoroLengths("25", "61", "15", "4"),
            PomodoroLengths("25", "5", "0", "4"),
            PomodoroLengths("25", "5", "15", "13"),
            PomodoroLengths("", "5", "15", "4"),
            PomodoroLengths("25", "5.5", "15", "4"),
        )) {
            f.page.editLengths(bad)
            assertFalse("$bad", f.page.state.value.canStart)
            assertNull(f.page.start())
        }
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun startSendsTheFormToTheServerAndCreatesNothingOnThePhone() = runTest {
        val f = idle()
        f.page.editLengths(PomodoroLengths("30", "5", "12", "2"))
        f.page.editFields(EntryFields(description = "First work", tags = listOf("deep")))
        var started = 0
        f.page.start { started++ }!!.join()
        assertEquals(
            listOf(
                "start $WORKSPACE EntryFields(description=First work, projectId=null, taskId=null, tags=[deep], " +
                    "billable=false) 30/5/12/2",
            ),
            f.server.calls,
        )
        assertEquals(1, started)
        assertTrue(f.changes.updates.isEmpty())
        // The server's answer is the session the page shows.
        assertNotNull(f.page.state.value.pomodoro)
        assertFalse(f.page.state.value.busy)
        assertNull(f.page.state.value.problem)
    }

    @Test
    fun aStartThatDoesNotGoThroughKeepsTheFormAndSaysWhy() = runTest {
        for (result in listOf(TimerActionResult.Offline, TimerActionResult.Refused)) {
            val f = idle()
            f.server.answer = result
            f.page.editFields(EntryFields(description = "Keep me"))
            var started = 0
            f.page.start { started++ }!!.join()
            assertEquals(0, started)
            assertEquals(PomodoroProblem(PomodoroAction.START, result), f.page.state.value.problem)
            assertNull(f.page.state.value.pomodoro)
            assertEquals("Keep me", f.page.state.value.fields.description)
            assertTrue(f.page.state.value.canStart)
        }
    }

    @Test
    fun aSecondTapWhileTheFirstIsOnItsWayStartsNothing() = runTest {
        val f = idle()
        f.server.gate = kotlinx.coroutines.CompletableDeferred()
        val first = f.page.start()!!
        assertNull(f.page.start())
        f.server.gate.complete(Unit)
        first.join()
        assertEquals(1, f.server.calls.size)
    }

    @Test
    fun withoutAWorkspaceOrWhileLoadingNothingStarts() {
        val f = idle()
        f.settings.value = null
        assertNull(f.page.start())
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun nothingStartsUntilTheHeldStateAndTheCacheHaveBeenRead() {
        val f = idle()
        val unread = PomodoroViewModel(
            emptyFlow(), emptyFlow(), PomodoroSessionEdits(emptyFlow(), f.server, f.changes, sender = {}), f.defaults,
            f.settings, f.unsent, f.refused, f.scope,
            exactAlarms = { false }, picker = flowOf(PickerSource()), offline = MutableStateFlow(false),
            saved = SavedStateHandle(),
        )
        assertTrue(unread.state.value.loading)
        assertFalse(unread.state.value.canStart)
        assertNull(unread.start())
        assertTrue(f.server.calls.isEmpty())
        assertFalse("read as soon as both inputs have spoken", f.page.state.value.loading)
    }

    @Test
    fun theOpenPomodorosFieldsComeFromTheCacheAndNeverStartASession() {
        val f = running("Typed on the phone")
        // The cache carries a queued edit the server's state does not have yet.
        assertEquals("Typed on the phone", f.page.state.value.fields.description)
        assertNull(f.page.start())
        f.page.editLengths(PomodoroLengths("1", "1", "1", "1"))
        assertEquals("the lengths of a running session are not editable", "40", f.page.state.value.lengths.work)
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun withoutACachedRowTheOpenPomodorosFieldsComeFromTheServersState() {
        val f = PageFixture(heldState(revision = 4, description = "From the server")).also { fixtures += it }
        assertEquals("From the server", f.page.state.value.fields.description)
    }

    @Test
    fun aPickOrBillableEditGoesToTheQueuedRunningEntryEditOfThePomodorosEntry() = runTest {
        val f = running()
        val shown = f.page.state.value.shown!!
        f.page.editFields(
            EntryFields("Focused work", projectId = ProjectId(9), billable = true),
            shown.entryId,
        )!!.join()
        val (identity, fields) = f.changes.updates.single()
        assertEquals(RunningEntryIdentity(EntryId(ENTRY), WorkspaceId(WORKSPACE), T0), identity)
        assertEquals(EntryFields("Focused work", projectId = ProjectId(9), billable = true), fields)
        assertEquals(fields, f.page.state.value.fields)
        // No session request: the server carries the fields to the later pomodoros.
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun aTypedDescriptionShowsAtOnceAndGoesToTheQueueWhenTheFieldIsDone() = runTest {
        val f = running()
        val entry = f.page.state.value.shown!!.entryId
        assertNull(f.page.editFields(EntryFields("Focused work, typed"), entry, send = false))
        assertEquals("Focused work, typed", f.page.state.value.fields.description)
        assertTrue(f.changes.updates.isEmpty())
        f.page.fieldsDone(entry)!!.join()
        assertEquals("Focused work, typed", f.changes.updates.single().second.description)
    }

    @Test
    fun aDescriptionTypedBackToTheOriginalSendsNothing() {
        val f = running()
        val entry = f.page.state.value.shown!!.entryId
        f.page.editFields(EntryFields("x"), entry, send = false)
        f.page.editFields(EntryFields("Focused work"), entry, send = false)
        assertNull(f.page.fieldsDone(entry))
        assertTrue(f.changes.updates.isEmpty())
    }

    @Test
    fun anEditFromAPageDrawnForAnotherPomodoroChangesNothing() {
        val f = running()
        assertNull(f.page.editFields(EntryFields("Stale"), entryId = EntryId(ENTRY + 1)))
        assertNull(f.page.editFields(EntryFields("Stale form edit"), entryId = null))
        assertEquals("Focused work", f.page.state.value.fields.description)
        assertTrue(f.changes.updates.isEmpty())
    }

    @Test
    fun anEditTheQueueRefusesDoesNotStayOnScreen() = runTest {
        val f = running()
        f.changes.accepts = false
        f.page.editFields(EntryFields("Lost"), f.page.state.value.shown!!.entryId)!!.join()
        assertEquals("Focused work", f.page.state.value.fields.description)
    }

    @Test
    fun anEditTheStorageRefusesStaysAsTypedAndGoesAgainWhenTheFieldIsDone() = runTest {
        val f = running()
        val entry = f.page.state.value.shown!!.entryId
        f.changes.refusal = IOException("disk full")

        f.page.editFields(EntryFields("Kept"), entry)!!.join()

        assertEquals(emptyList<Throwable>(), f.escaped)
        assertEquals("Kept", f.page.state.value.fields.description)
        f.changes.refusal = null
        f.page.fieldsDone(entry)!!.join()
        assertEquals("Kept", f.changes.updates.single().second.description)
        assertEquals("Kept", f.page.state.value.fields.description)
    }

    @Test
    fun theStartFormKeepsTheLastPomodorosFieldsAfterTheSessionEnds() {
        val f = running("Report")
        f.page.editLengths(PomodoroLengths("1", "1", "1", "1"))
        f.held.value = heldState(revision = 5, pomodoro = null)
        f.controls.value = com.github.e0ipso.tokkl.timerstate.TimerControls.Idle
        assertNull(f.page.state.value.pomodoro)
        assertEquals("Report", f.page.state.value.fields.description)
        assertEquals(PomodoroLengths("40", "8", "20", "3"), f.page.state.value.lengths)
    }

    @Test
    fun theLengthsOfAnEndedSessionGiveWayToTheDefaultsAgain() {
        val f = idle()
        f.page.editLengths(PomodoroLengths("30", "5", "12", "2"))
        f.held.value = heldState(revision = 1)
        f.held.value = heldState(revision = 2, pomodoro = null)
        assertEquals(PomodoroLengths("40", "8", "20", "3"), f.page.state.value.lengths)
        f.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals("45", f.page.state.value.lengths.work)
    }

    @Test
    fun permissionGuidanceStaysIndependentAndTheQueueCountsStayVisible() {
        val f = idle()
        f.alarmsDenied = true
        f.page.permissionsChanged(PomodoroPermissions(notifications = false, exactAlarm = true, fullScreen = false))
        assertTrue(f.page.state.value.notificationsDenied)
        assertFalse(f.page.state.value.alarmDenied)
        assertTrue(f.page.state.value.fullScreenDenied)
        f.page.permissionsChanged(PomodoroPermissions(notifications = false, exactAlarm = true, fullScreen = true))
        assertFalse(f.page.state.value.fullScreenDenied)
        assertTrue("the notification denial stays", f.page.state.value.notificationsDenied)
        f.unsent.value = 2
        assertEquals(2, f.page.state.value.unsentCount)
    }
}
