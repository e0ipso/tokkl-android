package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Sign-out's order for one synced preference: the file goes last, after every write in flight. */
class SyncedPreferenceTest {

    private val executor = Executors.newSingleThreadExecutor()
    private val io = executor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun signOutDeletesTheFileAfterAWriteInFlight() {
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ops = CopyOnWriteArrayList<String>()
        val store = object : ClearableStore<StoredPreference<Int>> {
            override fun load(): StoredPreference<Int>? = null

            override fun save(value: StoredPreference<Int>) {
                writing.countDown()
                release.await()
                ops += "saved ${value.value}"
            }

            override fun clear() {
                ops += "clear"
            }
        }
        val preference = SyncedPreference(store, { null }, CoroutineScope(io), io, default = 0, codec = Codec)

        preference.change(5, unsent = false, sendIf = { false })
        writing.await()
        val signOut = thread {
            preference.forget()
            preference.clear()
        }
        // Sign-out waits for the write, or has run past it.
        while (signOut.isAlive && signOut.state != Thread.State.BLOCKED) Thread.onSpinWait()
        release.countDown()
        signOut.join()

        assertEquals(listOf("saved 5", "clear"), ops)
        assertEquals(0, preference.value.value)
    }

    private object Codec : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = error("nothing is sent")
    }
}
