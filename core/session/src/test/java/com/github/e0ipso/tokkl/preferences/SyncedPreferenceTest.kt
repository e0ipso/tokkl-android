package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.TimerViewChange
import com.github.e0ipso.tokkl.api.mePreferences
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Preference changes, refreshes and sign-out while disk or server writes are in flight. */
class SyncedPreferenceTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(dispatcher)
    private val account = CurrentAccount(scope)

    /** A second refresh can read the old server value while the first refresh's offline retry is in flight. */
    @Test
    fun anOfflineRetryAcknowledgedDuringTheNextRefreshKeepsTheLocalChoice() {
        val release = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertTrue(release.await(10, TimeUnit.SECONDS))
                return MockResponse.Builder().code(200).body("{}").build()
            }
        }
        server.start()
        val background = CoroutineScope(Dispatchers.IO)
        try {
            val url = checkNotNull(ServerUrl.parse(server.url("/").toString()))
            val testApi = TestApi(url).apply { trust = InMemoryServerTrust(url) }
            val api = testApi.api(url, ApiCredentials.ApiToken("test"))
            var online = false
            val preference = SyncedPreference(
                InMemoryValueStore<StoredPreference<Int>>(null),
                { api.takeIf { online } },
                CurrentAccount(background),
                background,
                Dispatchers.IO,
                default = 0,
                codec = Sent,
                sharing = { true },
            )
            runBlocking {
                preference.ready()
                preference.change(5).join()
            }
            online = true
            val retry = runBlocking { preference.refreshed(mePreferences("{}"), keepsPreferences = true) }
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            // The next refresh read the old preference before this POST completed.
            val stale = mePreferences("{}")
            release.countDown()
            runBlocking {
                retry.join()
                preference.refreshed(stale, keepsPreferences = true).join()
            }

            assertEquals(
                "the acknowledgement cannot make an older read overwrite the choice",
                5,
                preference.value.value,
            )
            assertEquals(1, server.requestCount)
            // A later refresh still takes a change made on another device.
            runBlocking { preference.refreshed(mePreferences("{}"), keepsPreferences = true).join() }
            assertEquals(0, preference.value.value)
        } finally {
            release.countDown()
            background.cancel()
            server.close()
        }
    }

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
        val preference =
            SyncedPreference(
                store,
                { null },
                account,
                scope,
                dispatcher,
                default = 0,
                codec = Codec,
                sharing = { false },
            )
        scheduler.runCurrent()

        preference.change(5)
        scheduler.runCurrent()
        scope.launch { signOut(preference) }
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
        val delayed = CompletableDeferred<Boolean>()
        // The change waits for its unsent flag, as for the capabilities, while sign-out runs.
        val preference = SyncedPreference(
            store,
            { null },
            account,
            scope,
            dispatcher,
            default = 0,
            codec = Codec,
            sharing = { delayed.await() },
        )
        scheduler.runCurrent()

        preference.change(5)
        scope.launch { signOut(preference) }
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
            val preference = SyncedPreference(
                store,
                { api },
                account,
                CoroutineScope(Dispatchers.IO),
                Dispatchers.IO,
                default = 0,
                codec = Sent,
                sharing = { true },
            )
            runBlocking { preference.ready() }

            val change = preference.change(5)
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

    /**
     * A change made offline is stored as unsent. When the phone fails to read it at the next process
     * start, the refresh after that reads it again rather than storing the server's value over it.
     */
    @Test
    fun aRefreshAfterAFailedLoadKeepsTheUnsentChangeOnDisk() {
        val ops = mutableListOf<String>()
        var reads = 0
        val store = object : ClearableStore<StoredPreference<Int>> {
            override suspend fun load(): StoredPreference<Int> {
                if (reads++ == 0) throw IOException("busy")
                return StoredPreference(7, unsent = true)
            }

            override suspend fun save(value: StoredPreference<Int>) {
                ops += "saved ${value.value} unsent=${value.unsent}"
            }

            override suspend fun clear() {
                ops += "clear"
            }
        }
        val preference = SyncedPreference(
            store,
            { null },
            account,
            scope,
            dispatcher,
            default = 0,
            codec = Codec,
            sharing = { false },
        )
        scheduler.runCurrent()

        scope.launch { preference.refreshed(mePreferences("{}"), keepsPreferences = true) }
        scheduler.runCurrent()

        assertEquals("the stored change shows", 7, preference.value.value)
        assertEquals("and stays unsent on disk", listOf("saved 7 unsent=true"), ops)
    }

    /** A change whose write fails stays in memory, and the next refresh writes it, still unsent. */
    @Test
    fun aChangeWhoseWriteFailedIsWrittenByTheNextRefresh() {
        val ops = mutableListOf<String>()
        var failing = true
        val store = object : ClearableStore<StoredPreference<Int>> {
            override suspend fun load(): StoredPreference<Int>? = null

            override suspend fun save(value: StoredPreference<Int>) {
                if (failing) throw IOException("disk full")
                ops += "saved ${value.value} unsent=${value.unsent}"
            }

            override suspend fun clear() {
                ops += "clear"
            }
        }
        val preference = SyncedPreference(
            store,
            { null },
            account,
            scope,
            dispatcher,
            default = 0,
            codec = Codec,
            sharing = { true },
        )
        scheduler.runCurrent()
        preference.change(5)
        scheduler.runCurrent()
        assertEquals(5, preference.value.value)

        failing = false
        scope.launch { preference.refreshed(mePreferences("{}"), keepsPreferences = true) }
        scheduler.runCurrent()

        assertEquals(listOf("saved 5 unsent=true"), ops)
    }

    /**
     * What sign-out does to one synced preference: ends the account's context first, then forgets
     * the preference and deletes its file.
     */
    private suspend fun signOut(preference: SyncedPreference<Int>) {
        account.end()
        preference.forget()
        preference.clear()
    }

    private object Sent : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = TimerViewChange("view-$value")
    }

    private object Codec : PreferenceCodec<Int> {
        override fun fromServer(preferences: MePreferences): Int = 0

        override fun body(value: Int): PreferencesChange = error("nothing is sent")
    }
}
