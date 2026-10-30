package com.github.e0ipso.tokkl.widget

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroWidgetSyncTest {
    @Test fun eachProviderKeepsItsOwnKeyAcrossChangesAndProcessRestart() = runBlocking {
        val disk = mutableMapOf("timer" to "running", "pomodoro" to "work")
        val updates = mutableListOf<String>()
        fun sync(name: String, vararg keys: String) = WidgetSync(
            keys = flowOf(*keys),
            loadShown = { disk[name] }, saveShown = { disk[name] = it },
            update = { updates += name }, scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        )
        sync("timer", "running", "running").follow()
        sync("pomodoro", "work", "work", "ended").follow()
        assertEquals(listOf("pomodoro"), updates)
        assertEquals("running", disk["timer"])
        sync("pomodoro", "ended").follow()
        sync("timer", "idle").follow()
        assertEquals(listOf("pomodoro", "timer"), updates)
        assertEquals("ended", disk["pomodoro"])
    }

    @Test fun aFailedDrawDoesNotRememberAKeyThatWasNeverDrawn() = runBlocking {
        var saved: String? = "work"
        val sync = WidgetSync(
            keys = flowOf("ended"), loadShown = { saved }, saveShown = { saved = it },
            update = { throw IOException("launcher unavailable") },
            scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        )
        try { sync.follow() } catch (_: IOException) { /* Retry on the next process or state. */ }
        assertEquals("work", saved)
    }

    @Test fun failedStorageStillAllowsTheNextPhaseToDraw() = runBlocking {
        var draws = 0
        val sync = WidgetSync(
            keys = flowOf("work", "work", "ended"), loadShown = { null },
            saveShown = { throw IOException("disk full") }, update = { draws++ },
            scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        )
        sync.follow()
        assertEquals(2, draws)
    }
}
