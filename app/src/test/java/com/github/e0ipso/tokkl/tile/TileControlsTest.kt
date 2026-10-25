package com.github.e0ipso.tokkl.tile

import android.service.quicksettings.TileService
import com.github.e0ipso.tokkl.MergedManifest
import com.github.e0ipso.tokkl.androidAttribute
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.childElements
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
 * points at them. The activity draws [tileControlsPanel] and sends each button through
 * [useTileControl].
 */
class TileControlsTest {

    private val calls = mutableListOf<String>()

    @Test
    fun aRunningTimerOffersPauseAndStopForTheTargetItShows() {
        val target = TimerTarget(session = 11, chainId = 22, revision = 3, entryId = 4)
        val panel = tileControlsPanel(running(target))

        assertEquals("Design review", panel!!.description)
        assertFalse(panel.paused)
        assertEquals(listOf(TileControlAction.Pause, TileControlAction.Stop), panel.buttons.map { it.action })
        assertTrue(panel.buttons.all { it.target == target })
        val changes = RecordingChanges()
        runBlocking { panel.buttons.forEach { changes.useTileControl(it) } }
        assertEquals(listOf("pause $target", "stop $target"), calls)
    }

    @Test
    fun aPausedTimerOffersResumeAndStopForTheTargetItShows() {
        val target = TimerTarget(session = 11, chainId = 22, revision = 3)
        val panel = tileControlsPanel(
            TimerControls.Paused(seconds = 600, workspaceId = 10, fields = EntryFields("Paused work"), target = target),
        )

        assertEquals("Paused work", panel!!.description)
        assertTrue(panel.paused)
        assertEquals(listOf(TileControlAction.Resume, TileControlAction.Stop), panel.buttons.map { it.action })
        assertTrue(panel.buttons.all { it.target == target })
        val changes = RecordingChanges()
        runBlocking { panel.buttons.forEach { changes.useTileControl(it) } }
        assertEquals(listOf("resume $target", "stop $target"), calls)
    }

    @Test
    fun anIdleTimerOffersNoCompactControls() {
        assertNull(tileControlsPanel(TimerControls.Idle))
    }

    @Test
    fun aButtonKeepsTheTargetItWasDrawnWithAfterTheTimerMovesOn() {
        val shown = TimerTarget(session = 11, chainId = 22, revision = 3, entryId = 4)
        val later = TimerTarget(session = 11, chainId = 22, revision = 4, entryId = 9)
        val drawn = tileControlsPanel(running(shown))!!
        val movedOn = tileControlsPanel(running(later, id = 9))!!

        assertEquals(later, movedOn.buttons.first().target)
        runBlocking { RecordingChanges().useTileControl(drawn.buttons.first()) }
        assertEquals(listOf("pause $shown"), calls)
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

    private fun running(target: TimerTarget, id: Long = target.entryId) = TimerControls.Running(
        entry = CachedTimeEntry(
            id = id,
            workspaceId = 10,
            projectId = null,
            taskId = null,
            description = "Design review",
            start = Instant.parse("2026-10-19T12:00:00Z"),
            stop = null,
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        ),
        unsent = false,
        accumulatedSeconds = 600,
        target = target,
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

    private inner class RecordingChanges : EntryChanges {
        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("nothing starts")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean {
            calls += "stop $entryId"
            return true
        }

        override suspend fun stop(target: TimerTarget, fields: EntryFields?): Boolean {
            calls += "stop $target"
            return true
        }

        override suspend fun pause(target: TimerTarget, fields: EntryFields?): Boolean {
            calls += "pause $target"
            return true
        }

        override suspend fun resume(target: TimerTarget, fields: EntryFields?): Long? {
            calls += "resume $target"
            return -1
        }

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("nothing is deleted")
    }
}
