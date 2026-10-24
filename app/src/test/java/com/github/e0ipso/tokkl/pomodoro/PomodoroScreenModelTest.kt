package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source coverage for page commands; no Android clock, alarm or network is needed. */
class PomodoroScreenModelTest {
    private var now = Instant.parse("2026-10-24T09:07:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val defaults = MutableStateFlow(PomodoroDefaults(40, 8, 20, 3))
    private val settings = MutableStateFlow<AccountSettings?>(
        AccountSettings("europe/madrid", DayOfWeek.MONDAY, null, defaultWorkspaceId = 11),
    )
    private val unsent = MutableStateFlow(0)
    private val refused = MutableStateFlow<List<PendingChange>>(emptyList())
    private val database = PageDatabase()
    private var denied = false
    private var callbackFailure = false
    private val alarms = object : PomodoroAlarmScheduler {
        override val permissionDenied: Boolean get() = denied
        override fun schedule(triggerAt: Instant): Boolean = !denied
        override fun cancel() = Unit
        override fun watchSession(running: Boolean) = Unit
    }
    private val store = PomodoroSessionStore(
        database, Dispatchers.Unconfined, clock,
        accountTime = { settings.value?.time(clock) },
        onQueued = { if (callbackFailure) throw IOException("background sender unavailable") },
        alarms = alarms,
    )
    private fun model() = PomodoroScreenModel(store, defaults, settings, unsent, refused, scope).also { it.reload() }

    @After
    fun stopCollectors() { scope.cancel() }

    @Test
    fun settingsChangesAffectTheNextSessionWithoutChangingTheRunningSession() = runBlocking {
        val page = model()
        val custom = PomodoroLengths("1", "2", "3", "2")
        page.editLengths(custom)
        page.start()!!.join()
        defaults.value = PomodoroDefaults(50, 10, 30, 4)
        assertEquals(custom, page.state.value.lengths)
        assertEquals(PomodoroSessionConfig(1.0, 2.0, 3.0, 2.0), store.current()!!.screenLengths())
        page.end()!!.join()
        assertNull(store.current())
        assertEquals(PomodoroLengths("50", "10", "30", "4"), page.state.value.lengths)
        page.start()!!.join()
        assertEquals(PomodoroSessionConfig(50.0, 10.0, 30.0, 4.0), store.current()!!.screenLengths())
    }

    @Test
    fun aSessionEndedElsewhereResetsTheNextFormToCurrentDefaultsOnReload() = runBlocking {
        val page = model()
        page.editLengths(PomodoroLengths("1", "2", "3", "2"))
        page.start()!!.join()
        defaults.value = PomodoroDefaults(60, 12, 25, 5)
        store.end()
        page.reload().join()
        assertNull(page.state.value.session)
        assertEquals(PomodoroLengths("60", "12", "25", "5"), page.state.value.lengths)
        defaults.value = PomodoroDefaults(45, 9, 20, 3)
        assertEquals(PomodoroLengths("45", "9", "20", "3"), page.state.value.lengths)
    }

    @Test
    fun storedDefaultsRefreshUntilEditedAndEditedLengthsStartOnlyThisSession() = runBlocking {
        val page = model()
        assertEquals(PomodoroLengths("40", "8", "20", "3"), page.state.value.lengths)
        defaults.value = PomodoroDefaults(50, 10, 30, 4)
        assertEquals("50", page.state.value.lengths.work)
        val edited = PomodoroLengths("1", "2", "3", "2")
        page.editLengths(edited)
        defaults.value = PomodoroDefaults.DEFAULT
        assertEquals(edited, page.state.value.lengths)
        page.start()!!.join()
        val saved = store.current()!!
        assertEquals(PomodoroSessionConfig(1.0, 2.0, 3.0, 2.0), saved.screenLengths())
        assertEquals(11L, saved.workspaceId)
        assertEquals(PomodoroDefaults.DEFAULT, defaults.value)
    }

    @Test
    fun everyLengthRejectsMissingFractionalAndOutOfRangeValuesAndAcceptsBothBounds() {
        val page = model()
        val valid = PomodoroLengths("25", "5", "15", "4")
        val edits: List<(String) -> PomodoroLengths> = listOf(
            { valid.copy(work = it) }, { valid.copy(shortRest = it) },
            { valid.copy(longRest = it) }, { valid.copy(every = it) },
        )
        val maxima = listOf(180, 60, 60, 12)
        for ((index, edit) in edits.withIndex()) {
            for (bad in listOf("", "0", "-1", "1.5", "NaN", "999999999999", (maxima[index] + 1).toString())) {
                page.editLengths(edit(bad))
                assertFalse("input $index: $bad", page.state.value.canStart)
                assertNull(page.start())
                assertNull(database.row)
            }
            page.editLengths(edit("1"))
            assertTrue(page.state.value.canStart)
            page.editLengths(edit(maxima[index].toString()))
            assertTrue(page.state.value.canStart)
        }
    }

    @Test
    fun skipOvertimeAndEndKeepOneContinuousEntryWithCurrentFields() = runBlocking {
        val page = model()
        page.start()!!.join()
        val work = page.state.value.session!!
        assertEquals(PomodoroClock(false, 2400), work.clockAt(now))
        now = now.plusSeconds(60)
        page.next()!!.join()
        val rest = page.state.value.session!!
        assertEquals("shortRest", rest.phaseKind)
        assertEquals(1L, rest.number())
        assertEquals("work", rest.nextKind())
        assertTrue(database.creates.isEmpty())
        now = now.plusSeconds(8 * 60 + 13)
        assertEquals(PomodoroClock(true, 13), rest.clockAt(now))
        page.editFields(EntryFields("Current", projectId = 3, taskId = 5, tags = listOf("focus"), billable = true))!!.join()
        page.end()!!.join()
        assertNull(page.state.value.session)
        assertEquals(1, database.creates.size)
        assertEquals(EntryFields("Current", 3, 5, listOf("focus"), true), database.creates.single().fields)
        assertEquals(work.pomodoroStartedAt, database.creates.single().start)
        assertEquals(now, database.creates.single().stop)
        assertEquals(PomodoroNotice.SAVED_ON_PHONE, page.state.value.notice)
        assertNull(page.end())
    }

    @Test
    fun editingTheNewPomodoroCannotRewriteOrDuplicateTheImmutablePriorEntry() = runBlocking {
        val page = model()
        page.editFields(EntryFields(description = "First", tags = listOf("old")))
        page.start()!!.join()
        now = now.plusSeconds(100)
        page.next()!!.join()
        now = now.plusSeconds(60)
        page.next()!!.join()
        val prior = database.creates.single()
        page.editFields(EntryFields(description = "Second", tags = listOf("new"), billable = true))!!.join()
        page.editFields(page.state.value.fields.copy(projectId = 8, taskId = 9))!!.join()
        assertEquals(listOf(prior), database.creates)
        assertEquals("First", prior.fields.description)
        assertEquals(listOf("old"), prior.fields.tags)
        assertEquals("Second", store.current()!!.fields.description)
        assertEquals(2L, page.state.value.session!!.number())
        now = now.plusSeconds(120)
        page.end()!!.join()
        assertEquals(2, database.creates.size)
        assertEquals(prior, database.creates.first())
        assertEquals("Second", database.creates.last().fields.description)
    }

    @Test
    fun tooShortAndQueuedCountStaySeparateAndCountsAndRefusalsReloadAfterEnd() = runBlocking {
        settings.value = settings.value!!.copy(timeResolutionMinutes = 15.0)
        val page = model()
        page.start()!!.join()
        now = now.plusSeconds(10)
        page.end()!!.join()
        assertEquals(PomodoroNotice.TOO_SHORT, page.state.value.notice)
        assertTrue(database.creates.isEmpty())
        unsent.value = 2
        val held = PendingChange.of(ChangeKind.CREATE, com.github.e0ipso.tokkl.cache.CachedTimeEntry(
            id = -1, workspaceId = 11, projectId = null, taskId = null, description = "First",
            start = now, stop = now.plusSeconds(60), billable = false, tags = emptyList(), tagIds = emptyList(),
        )).copy(held = true, refusal = "Project does not belong to workspace")
        refused.value = listOf(held)
        assertEquals(2, page.state.value.unsentCount)
        val reopened = model().state.value
        assertNull(reopened.session)
        assertEquals(2, reopened.unsentCount)
        assertEquals("Project does not belong to workspace", reopened.refused.single().refusal)
    }

    @Test
    fun saveFailureNamesTheActionAndReasonAndRetainsTheSessionForRetry() = runBlocking {
        val page = model()
        page.start()!!.join()
        now = now.plusSeconds(60)
        database.failCreate = true
        page.end()!!.join()
        assertEquals(PomodoroSaveProblem(PomodoroAction.END, "disk full"), page.state.value.problem)
        assertNotNull(page.state.value.session)
        assertNull(page.state.value.notice)
        assertTrue(database.creates.isEmpty())
        database.failCreate = false
        page.end()!!.join()
        assertNull(page.state.value.problem)
        assertEquals(1, database.creates.size)
    }

    @Test
    fun aPostCommitCallbackFailureReloadsTheRowWithoutRepeatingTheFinishedEntry() = runBlocking {
        val page = model()
        page.editLengths(PomodoroLengths("1", "2", "3", "2"))
        page.start()!!.join()
        defaults.value = PomodoroDefaults(50, 10, 30, 4)
        now = now.plusSeconds(60)
        callbackFailure = true
        page.end()!!.join()
        assertNull(page.state.value.session)
        assertEquals("background sender unavailable", page.state.value.problem!!.reason)
        assertEquals(PomodoroLengths("50", "10", "30", "4"), page.state.value.lengths)
        assertEquals(1, database.creates.size)
        assertNull(page.end())
        assertEquals(1, database.creates.size)
    }

    @Test
    fun aFailedFieldWriteSurvivesReloadAndMustBeStoredBeforeEndCanSave() = runBlocking {
        val page = model()
        page.start()!!.join()
        database.failSave = true
        page.editFields(EntryFields(description = "Keep this edit"))!!.join()
        assertEquals(PomodoroSaveProblem(PomodoroAction.FIELDS, "session write refused"), page.state.value.problem)
        page.reload().join()
        assertEquals("Keep this edit", page.state.value.fields.description)
        now = now.plusSeconds(60)
        page.end()!!.join()
        assertNotNull(page.state.value.session)
        assertTrue(database.creates.isEmpty())
        database.failSave = false
        page.end()!!.join()
        assertEquals("Keep this edit", database.creates.single().fields.description)
    }

    @Test
    fun deniedAlarmsRemainVisibleAcrossStartAndReloadWithoutBlockingTheSession() = runBlocking {
        denied = true
        val page = model()
        assertTrue(page.state.value.alarmDenied)
        page.start()!!.join()
        assertNotNull(page.state.value.session)
        assertTrue(page.state.value.alarmDenied)
        denied = false
        page.reload().join()
        assertFalse(page.state.value.alarmDenied)
    }

    @Test
    fun accountTimezoneParsingUsesIntlCaseAndOffsetSemanticsForSnapping() = runBlocking {
        settings.value = settings.value!!.copy(timeZone = "asia/kathmandu", timeResolutionMinutes = 30.0)
        val page = model()
        page.start()!!.join()
        now = now.plusSeconds(30 * 60)
        page.end()!!.join()
        assertEquals(Instant.parse("2026-10-24T09:15:00Z"), database.creates.single().start)
        assertEquals(Instant.parse("2026-10-24T09:45:00Z"), database.creates.single().stop)
    }

    @Test
    fun unknownAccountZoneKeepsDurationsAndCommandsAvailableWithoutInventingAZone() = runBlocking {
        settings.value = settings.value!!.copy(timeZone = "Not/AZone", timeResolutionMinutes = 30.0)
        val page = model()
        assertTrue(page.state.value.canStart)
        page.start()!!.join()
        now = now.plusSeconds(600)
        page.end()!!.join()
        assertEquals(Instant.parse("2026-10-24T09:07:00Z"), database.creates.single().start)
        assertEquals(now, database.creates.single().stop)
    }

    @Test
    fun longRestLabelAndNumberFollowTheConfiguredWorkCount() = runBlocking {
        val page = model()
        page.editLengths(PomodoroLengths("25", "5", "15", "1"))
        page.start()!!.join()
        assertEquals("longRest", page.state.value.session!!.nextKind())
        now = now.plusSeconds(60)
        page.next()!!.join()
        assertEquals("longRest", page.state.value.session!!.phaseKind)
        assertEquals(1L, page.state.value.session!!.number())
        now = now.plusSeconds(60)
        page.next()!!.join()
        assertEquals(1L, page.state.value.session!!.number())
    }

    @Test
    fun missingWorkspaceDisablesStartUntilAccountSettingsArrive() {
        settings.value = null
        val page = model()
        assertFalse(page.state.value.canStart)
        assertNull(page.start())
        settings.value = AccountSettings("+05:30", DayOfWeek.MONDAY, null, defaultWorkspaceId = 11)
        assertTrue(page.state.value.canStart)
    }

    private data class Created(val fields: EntryFields, val start: Instant, val stop: Instant)

    private inner class PageDatabase : PomodoroSessionDatabase {
        var row: PomodoroSession? = null
        val creates = mutableListOf<Created>()
        var failCreate = false
        var failSave = false
        override fun current() = row
        override fun save(session: PomodoroSession) {
            if (failSave) throw IOException("session write refused")
            row = session
        }
        override fun delete() { row = null }
        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            if (failCreate) throw IOException("disk full")
            creates += Created(fields, start, stop)
            unsent.value = creates.size
            return -creates.size.toLong()
        }
        override fun <T> transaction(block: () -> T): T {
            val previous = row
            val oldCreates = creates.toList()
            return try { block() } catch (e: Exception) {
                row = previous
                creates.clear()
                creates.addAll(oldCreates)
                throw e
            }
        }
    }
}
