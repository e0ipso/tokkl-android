package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sign-out's order for one synced preference: the file goes last, after every write in flight. */
class SyncedPreferenceTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler))

    @Test
    fun signOutDeletesTheFileAfterAWriteInFlight() {
        val release = CompletableDeferred<Unit>()
        val ops = mutableListOf<String>()
        val store = object : ClearableStore<StoredPreference<Int>> {
            override suspend fun load(): StoredPreference<Int>? = null

            override suspend fun save(value: StoredPreference<Int>) {
                release.await()
                ops += "saved ${value.value}"
            }

            override suspend fun clear() {
                ops += "clear"
            }
        }
        val preference = SyncedPreference(store, { null }, scope, default = 0, codec = Codec)
        scheduler.runCurrent()

        preference.change(5, unsent = { false }, sendIf = { false })
        scheduler.runCurrent()
        scope.launch {
            preference.forget()
            preference.clear()
        }
        scheduler.runCurrent()
        assertEquals("sign-out waits for the write", emptyList<String>(), ops)
        release.complete(Unit)
        scheduler.runCurrent()

        assertEquals(listOf("saved 5", "clear"), ops)
        assertEquals(0, preference.value.value)
    }

    @Test
    fun aChangeQueuedBeforeSignOutWritesNothingAfterIt() {
        val ops = mutableListOf<String>()
        val store = object : ClearableStore<StoredPreference<Int>> {
            override suspend fun load(): StoredPreference<Int>? = null

            override suspend fun save(value: StoredPreference<Int>) {
                ops += "saved ${value.value}"
            }

            override suspend fun clear() {
                ops += "clear"
            }
        }
        val preference = SyncedPreference(store, { null }, scope, default = 0, codec = Codec)
        val delayed = CompletableDeferred<Boolean>()
        scheduler.runCurrent()

        // The change waits for its unsent flag, as for the capabilities, while sign-out runs.
        preference.change(5, unsent = { delayed.await() }, sendIf = { false })
        scope.launch {
            preference.forget()
            preference.clear()
        }
        scheduler.runCurrent()
        delayed.complete(false)
        scheduler.runCurrent()

        assertEquals(listOf("clear"), ops)
        assertEquals(0, preference.value.value)
    }

    private object Codec : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = error("nothing is sent")
    }
}
