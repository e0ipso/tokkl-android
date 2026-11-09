package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.TimerViewChange
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun aSendAnsweredAfterSignOutWritesNothingAfterIt() {
        val release = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertTrue(release.await(10, TimeUnit.SECONDS))
                return MockResponse.Builder().code(200).body("{}").build()
            }
        }
        server.start()
        val testApi = TestApi(null)
        try {
            val url = ServerUrl.parse(server.url("/").toString())!!
            testApi.stored = url
            testApi.trust = InMemoryServerTrust(url)
            val api = testApi.api(url, ApiCredentials.ApiToken("test"))
            val ops = mutableListOf<String>()
            val store = object : ClearableStore<StoredPreference<Int>> {
                override suspend fun load(): StoredPreference<Int>? = null

                override suspend fun save(value: StoredPreference<Int>) {
                    ops += "saved ${value.value} unsent=${value.unsent}"
                }

                override suspend fun clear() {
                    ops += "clear"
                }
            }
            val preference = SyncedPreference(store, { api }, CoroutineScope(Dispatchers.IO), default = 0, codec = Sent)
            runBlocking { preference.ready() }

            val change = preference.change(5, unsent = { true }, sendIf = { true })
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            runBlocking { signOut(preference) }
            release.countDown()
            runBlocking { change.join() }

            assertEquals(listOf("saved 5 unsent=true", "clear"), ops)
            assertEquals(0, preference.value.value)
        } finally {
            release.countDown()
            server.close()
        }
    }

    /** What sign-out does to one synced preference: forgets it, then deletes its file. */
    private suspend fun signOut(preference: SyncedPreference<Int>) {
        preference.forget()
        preference.clear()
    }

    /** Posts the value as one key. */
    private object Sent : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = TimerViewChange("view-$value")
    }

    private object Codec : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = error("nothing is sent")
    }
}
