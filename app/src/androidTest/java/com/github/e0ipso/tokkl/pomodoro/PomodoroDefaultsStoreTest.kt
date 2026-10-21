package com.github.e0ipso.tokkl.pomodoro

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The real private preference file, reloaded through a fresh store after each write. */
@SignedOut
class PomodoroDefaultsStoreTest {
    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = PomodoroDefaultsStore(context)

    @After
    fun cleanUp() {
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun storedValuesAndTheUnsentFlagSurviveReloadAndClear() {
        assertNull(store.load())
        val pending = StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true)
        store.save(pending)
        assertEquals(pending, store.load())
        store.save(pending.copy(unsent = false))
        assertEquals(pending.copy(unsent = false), store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun everyStoreWriteClampsAllFourValues() {
        store.save(StoredPomodoroDefaults(PomodoroDefaults(0, 61, -1, 13), unsent = true))
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(1, 60, 1, 12), unsent = true), store.load())
        store.save(StoredPomodoroDefaults(PomodoroDefaults(181, 0, 61, 0), unsent = false))
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(180, 1, 60, 1), unsent = false), store.load())
    }

    @Test
    fun aPhoneOnlySettingPersistsItsChangeWithoutGettingAClient() {
        var clients = 0
        fun setting() = PomodoroDefaultsSetting(
            initial = store.load(),
            save = store::save,
            client = { clients++; null },
            keepsPreferences = { false },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
        val setting = setting()
        assertEquals(PomodoroDefaults(25, 5, 15, 4), setting.defaults.value)
        runBlocking { setting.change(PomodoroDefaults(40, 8, 20, 5)).join() }
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false), store.load())
        assertEquals(PomodoroDefaults(40, 8, 20, 5), setting().defaults.value)
        assertEquals(0, clients)
    }
}
