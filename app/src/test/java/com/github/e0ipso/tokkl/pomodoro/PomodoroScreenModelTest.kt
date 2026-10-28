package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pomodoro page as the controller of the server's session: the start form and the open pomodoro's fields. */
class PomodoroScreenModelTest {
    private val fixtures = mutableListOf<PageFixture>()
    private fun idle() = PageFixture().also { fixtures += it }
    private fun running(description: String = "Focused work") =
        PageFixture(heldState(revision = 4, description = description), cachedEntry(description)).also { fixtures += it }

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
    fun aDefaultChangeReachesTheFormUntilTheUserEditsIt() {
        val f = idle()
        f.defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals(PomodoroLengths("45", "9", "20", "3"), f.page.state.value.lengths)
    }

    @Test
    fun rangesMatchWhatTheServerAcceptsAndAnyOtherLengthBlocksStart() {
        val f = idle()
        assertEquals(PomodoroLengths("180", "60", "60", "12").sessionLengths()!!.run { listOf(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery) }, listOf(180, 60, 60, 12))
        assertNotNull(PomodoroLengths("1", "1", "1", "1").sessionLengths())
        for (bad in listOf(
            PomodoroLengths("181", "5", "15", "4"), PomodoroLengths("25", "61", "15", "4"),
            PomodoroLengths("25", "5", "0", "4"), PomodoroLengths("25", "5", "15", "13"),
            PomodoroLengths("", "5", "15", "4"), PomodoroLengths("25", "5.5", "15", "4"),
        )) {
            f.page.editLengths(bad)
            assertFalse("$bad", f.page.state.value.canStart)
            assertNull(f.page.start())
        }
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun startSendsTheFormToTheServerAndCreatesNothingOnThePhone() = runBlocking {
        val f = idle()
        f.page.editLengths(PomodoroLengths("30", "5", "12", "2"))
        f.page.editFields(EntryFields(description = "First work", tags = listOf("deep")))
        var started = 0
        f.page.start { started++ }!!.join()
        assertEquals(listOf("start $WORKSPACE EntryFields(description=First work, projectId=null, taskId=null, tags=[deep], billable=false) 30/5/12/2"), f.server.calls)
        assertEquals(1, started)
        assertTrue(f.changes.updates.isEmpty())
        // The server's answer is the session the page shows.
        assertNotNull(f.page.state.value.pomodoro)
        assertFalse(f.page.state.value.busy)
        assertNull(f.page.state.value.problem)
    }

    @Test
    fun aStartThatDoesNotGoThroughKeepsTheFormAndSaysWhy() = runBlocking {
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
    fun aSecondTapWhileTheFirstIsOnItsWayStartsNothing() = runBlocking {
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
        val unread = PomodoroScreenModel(
            emptyFlow(), emptyFlow(), f.server, f.changes, f.defaults, f.settings, f.unsent, f.refused, f.scope,
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
    fun aPickOrBillableEditGoesToTheQueuedRunningEntryEditOfThePomodorosEntry() = runBlocking {
        val f = running()
        val shown = f.page.state.value.shown!!
        f.page.editFields(EntryFields("Focused work", projectId = 9, billable = true), shown.entryId)!!.join()
        val (identity, fields) = f.changes.updates.single()
        assertEquals(RunningEntryIdentity(ENTRY, WORKSPACE, T0), identity)
        assertEquals(EntryFields("Focused work", projectId = 9, billable = true), fields)
        assertEquals(fields, f.page.state.value.fields)
        // No session request: the server carries the fields to the later pomodoros.
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun aTypedDescriptionShowsAtOnceAndGoesToTheQueueWhenTheFieldIsDone() = runBlocking {
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
        assertNull(f.page.editFields(EntryFields("Stale"), entryId = ENTRY + 1))
        assertNull(f.page.editFields(EntryFields("Stale form edit"), entryId = null))
        assertEquals("Focused work", f.page.state.value.fields.description)
        assertTrue(f.changes.updates.isEmpty())
    }

    @Test
    fun anEditTheQueueRefusesDoesNotStayOnScreen() = runBlocking {
        val f = running()
        f.changes.accepts = false
        f.page.editFields(EntryFields("Lost"), f.page.state.value.shown!!.entryId)!!.join()
        assertEquals("Focused work", f.page.state.value.fields.description)
    }

    @Test
    fun theStartFormKeepsTheLastPomodorosFieldsAfterTheSessionEnds() {
        val f = running("Report")
        f.page.editLengths(PomodoroLengths("1", "1", "1", "1"))
        f.held.value = heldState(revision = 5, pomodoro = null)
        f.controls.value = com.github.e0ipso.tokkl.cache.TimerControls.Idle
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
        f.page.fullScreenPermissionChanged(false)
        f.page.permissionsChanged(PomodoroPermissions(notifications = false, exactAlarm = true, fullScreen = false))
        assertTrue(f.page.state.value.notificationsDenied)
        assertFalse(f.page.state.value.alarmDenied)
        assertTrue(f.page.state.value.fullScreenDenied)
        f.page.fullScreenPermissionChanged(true)
        assertFalse(f.page.state.value.fullScreenDenied)
        assertTrue("the notification denial stays", f.page.state.value.notificationsDenied)
        f.unsent.value = 2
        assertEquals(2, f.page.state.value.unsentCount)
    }
}
