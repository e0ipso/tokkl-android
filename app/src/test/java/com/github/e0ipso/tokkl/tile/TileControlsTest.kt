package com.github.e0ipso.tokkl.tile

import android.service.quicksettings.TileService
import com.github.e0ipso.tokkl.MergedManifest
import com.github.e0ipso.tokkl.ServerTimerStates
import com.github.e0ipso.tokkl.androidAttribute
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.childElements
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.PauseChainActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The compact controls a long press on the Quick Settings tile opens, and the subtitle that
 * points at them. The activity draws [tileControlsPanel] and sends each button's action and
 * target through [TimerSurfaceActions.tap].
 */
class TileControlsTest {

    private val start = Instant.parse("2026-10-19T12:00:00Z")
    private val calls = mutableListOf<String>()

    /** What the phone shows when a button is pressed. */
    private var now: TimerControls = TimerControls.Idle

    @Test
    fun aRunningTimerOffersPauseAndStopForTheTargetItShows() {
        now = ServerTimerStates.controls(entry(4))
        val panel = tileControlsPanel(now)!!

        assertEquals("Design review", panel.description)
        assertFalse(panel.paused)
        assertEquals(listOf(TimerSurfaceAction.Pause, TimerSurfaceAction.Stop), panel.buttons.map { it.action })
        assertTrue(panel.buttons.all { it.target == TimerTarget.server(entryId = 4, account = ServerTimerStates.ACCOUNT) })

        val pause = press(panel.buttons[0]) as SurfaceTap.Request
        assertTrue("Pause waits for the server, and queues nothing", calls.isEmpty())
        runBlocking { pause.send() }
        assertEquals(SurfaceTap.Queued, press(panel.buttons[1]))
        assertEquals(listOf("pause", "stop 4"), calls)
    }

    @Test
    fun pauseIsLeftOutWithoutTheTimerApiAndDuringAPomodoroSession() {
        val session = ServerTimerStates.held(5, start, pomodoro = ServerTimerStates.session(entryId = 4, at = start))

        for (controls in listOf(ServerTimerStates.controls(entry(4), timerApi = false), ServerTimerStates.controls(entry(4), session))) {
            assertEquals(listOf(TimerSurfaceAction.Stop), tileControlsPanel(controls)!!.buttons.map { it.action })
        }
    }

    @Test
    fun aPausedTimerOffersResumeAndStopOnTheServerAtTheRevisionItShows() {
        now = ServerTimerStates.controls(null, ServerTimerStates.held(3, start, ServerTimerStates.paused(600, EntryFields("Paused work"))))
        val panel = tileControlsPanel(now)!!

        assertEquals("Paused work", panel.description)
        assertTrue(panel.paused)
        assertEquals(listOf(TimerSurfaceAction.Resume, TimerSurfaceAction.Stop), panel.buttons.map { it.action })
        assertTrue(panel.buttons.all { it.target == TimerTarget.server(revision = 3, account = ServerTimerStates.ACCOUNT) })
        runBlocking { panel.buttons.forEach { (press(it) as SurfaceTap.Request).send() } }
        assertEquals(listOf("resume 3", "discard 3"), calls)
    }

    @Test
    fun anIdleTimerOffersNoCompactControls() {
        assertNull(tileControlsPanel(TimerControls.Idle))
    }

    @Test
    fun aButtonKeepsTheTargetItWasDrawnWithAfterTheTimerMovesOn() {
        now = ServerTimerStates.controls(entry(4))
        val drawn = tileControlsPanel(now)!!
        now = ServerTimerStates.controls(entry(9))

        assertEquals(TimerTarget.server(entryId = 9, account = ServerTimerStates.ACCOUNT), tileControlsPanel(now)!!.buttons.first().target)
        assertNull("the drawn Pause acts on nothing once another entry runs", press(drawn.buttons.first()))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun theLongPressRegistersADialogActivityForTilePreferences() {
        val activity = preferencesActivity()

        assertTrue(activity.androidAttribute("name").endsWith(".tile.TileControlsActivity"))
        assertEquals("true", activity.androidAttribute("exported"))
        assertEquals("true", activity.androidAttribute("excludeFromRecents"))
        assertEquals("true", activity.androidAttribute("noHistory"))
        assertEquals("", activity.androidAttribute("taskAffinity"))
        assertEquals("@style/Theme.Tokkl.TileControls", activity.androidAttribute("theme"))
        val categories = activity.childElements()
            .filter { it.tagName == "intent-filter" }
            .flatMap { it.childElements() }
            .filter { it.tagName == "category" }
            .map { it.androidAttribute("name") }
        assertEquals(listOf("android.intent.category.DEFAULT"), categories)

        val style = style("Theme.Tokkl.TileControls")
        assertEquals("android:style/Theme.Translucent.NoTitleBar", style.getAttribute("parent"))
        val items = style.childElements().associate { it.getAttribute("name") to it.textContent.trim() }
        assertEquals("@android:color/transparent", items["android:windowBackground"])
        assertEquals("true", items["android:backgroundDimEnabled"])
    }

    private fun press(control: TileControl): SurfaceTap? = runBlocking {
        TimerSurfaceActions({ now }, { ServerTimerStates.ACCOUNT }, RecordingChanges(), RecordingServer(), sendQueued = {}).tap(control.action, control.target)
    }

    private fun entry(id: Long) = CachedTimeEntry(
        id = id,
        workspaceId = 10,
        projectId = null,
        taskId = null,
        description = "Design review",
        start = start,
        stop = null,
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private fun preferencesActivity(): Element {
        val match = MergedManifest.application().childElements()
            .filter { it.tagName == "activity" }
            .filter { activity ->
                activity.childElements()
                    .filter { it.tagName == "intent-filter" }
                    .flatMap { it.childElements() }
                    .any { it.tagName == "action" && it.androidAttribute("name") == TileService.ACTION_QS_TILE_PREFERENCES }
            }
        assertEquals("activities for QS_TILE_PREFERENCES", 1, match.size)
        return match.single()
    }

    private fun style(name: String): Element {
        val styles = parse(File("src/main/res/values/tile_styles.xml")).childElements()
            .filter { it.tagName == "style" && it.getAttribute("name") == name }
        assertEquals("styles named $name", 1, styles.size)
        return styles.single()
    }

    /** Gradle runs unit tests with the app module as the working directory. */
    private fun parse(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private inner class RecordingServer : PauseChainActions {
        override suspend fun pause(): TimerActionResult = TimerActionResult.Done.also { calls += "pause" }
        override suspend fun resume(revision: Long): TimerActionResult = TimerActionResult.Done.also { calls += "resume $revision" }
        override suspend fun discard(revision: Long): TimerActionResult = TimerActionResult.Done.also { calls += "discard $revision" }
    }

    private inner class RecordingChanges : EntryChanges {
        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("nothing starts")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return true
        }

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("nothing is deleted")
    }
}
