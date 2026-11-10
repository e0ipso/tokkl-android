package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.storage.valueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class PomodoroWidgetSyncTest {
    @Test fun eachProviderKeepsItsOwnKeyAcrossChangesAndProcessRestart() = runTest {
        val disk = mutableMapOf("timer" to "running", "pomodoro" to "work")
        val updates = mutableListOf<String>()
        fun sync(name: String, vararg keys: String) = WidgetSync(
            keys = flowOf(*keys),
            shown = valueStore(load = { disk[name] }, save = { disk[name] = it }),
            redraw = { updates += name },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
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

    @Test fun aFailedDrawRemembersNothingAndTriesAgainWithoutEndingTheSync() = runTest {
        var saved: String? = "work"
        var failures = 2
        val keys = MutableStateFlow("ended")
        val draws = mutableListOf<String>()
        val sync = WidgetSync(
            keys = keys,
            shown = valueStore(load = { saved }, save = { saved = it }),
            redraw = {
                if (failures-- > 0) throw IOException("launcher unavailable")
                draws += keys.value
            },
            scope = backgroundScope,
            io = Dispatchers.Unconfined,
        )
        var ended: Throwable? = null
        val following = launch {
            try {
                sync.follow()
            } catch (failure: IOException) {
                ended = failure
            }
        }

        runCurrent()
        assertEquals("a failed draw remembers no key", "work", saved)
        advanceUntilIdle()
        assertNull("a failed draw ends the sync", ended)
        assertEquals(listOf("ended"), draws)
        assertEquals("ended", saved)

        keys.value = "idle"
        runCurrent()
        assertEquals("the sync still follows", listOf("ended", "idle"), draws)
        following.cancel()
    }

    @Test fun aFailedReadOfTheStateReadsAgainWithoutEndingTheSync() = runTest {
        var reads = 0
        var saved: String? = null
        var draws = 0
        val keys = flow {
            reads++
            emit(if (reads == 1) "work" else "ended")
            if (reads == 1) throw IOException("cache unreadable")
            awaitCancellation()
        }
        val sync = WidgetSync(
            keys = keys,
            shown = valueStore(load = { saved }, save = { saved = it }),
            redraw = { draws++ },
            scope = backgroundScope,
            io = Dispatchers.Unconfined,
        )
        var ended: Throwable? = null
        val following = launch {
            try {
                sync.follow()
            } catch (failure: IOException) {
                ended = failure
            }
        }

        advanceUntilIdle()

        assertNull("a failed read ends the sync", ended)
        assertEquals(2, reads)
        assertEquals(2, draws)
        assertEquals("ended", saved)
        following.cancel()
    }

    @Test fun failedStorageStillAllowsTheNextPhaseToDraw() = runTest {
        var draws = 0
        val sync = WidgetSync(
            keys = flowOf("work", "work", "ended"),
            shown = valueStore(load = { null }, save = { throw IOException("disk full") }),
            redraw = { draws++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
        sync.follow()
        assertEquals(2, draws)
    }
}
