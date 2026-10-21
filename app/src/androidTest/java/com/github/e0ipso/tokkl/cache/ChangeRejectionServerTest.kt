package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInCredentials
import com.github.e0ipso.tokkl.signin.SignInResult
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Every refusal comes from the real Tokkl container, with throwaway accounts and a Room queue. */
class ChangeRejectionServerTest {
    @get:Rule val session = SessionRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var account: TestAccounts.Account
    private lateinit var database: CacheDatabase
    private lateinit var queue: ChangeQueue
    private lateinit var sender: ChangeSender
    private lateinit var token: String
    private var discarded = 0
    private val start = Instant.now().minus(Duration.ofHours(2))
    private val stop get() = start.plusSeconds(1800)
    private val web get() = account.client

    @Before fun prepare() {
        account = TestAccounts.create("Europe/Madrid")
        token = account.apiToken
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        queue = ChangeQueue(database, Dispatchers.IO, onQueued = {})
        sender = sender()
    }

    @After fun cleanUp() {
        scope.cancel()
        try {
            if (::database.isInitialized) database.close()
        } finally {
            if (::account.isInitialized) {
                TestAccounts.delete(account)

            }
        }
    }

    @Test fun anAlreadyStopped409LeavesTheQueueWithoutADiscardNotice() {
        val id = cachedEntry("Stopped on the web")
        val cached = database.changes().entry(id)!!
        database.changes().insert(PendingChange.of(ChangeKind.STOP, cached))
        // The production PUT preserves the phone's tap time. Only PATCH /stop yields 409 on
        // Tokkl, so route this test's stop there to exercise a genuine server refusal.
        var responseStatus = 0
        val stopRoute = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(request.url.newBuilder().addPathSegment("stop").build())
                .method("PATCH", request.body).build()).also { responseStatus = it.code }
        }.build()
        sender = sender(stopRoute)
        assertEquals(ChangeSender.Outcome.Empty(1), sender.drain())
        assertEquals(409, responseStatus)
        assertEquals(0, count())
        assertEquals(0, discarded)
        assertFalse(sender.signInNeeded.value)
        assertEquals("Stopped on the web", account.entry(id).getValue("description").toString().trim('"'))
    }

    @Test fun a404DropsChangesToTheDeletedEntryAndAggregatesTheNotice() {
        val id = cachedEntry("Deleted elsewhere")
        runBlocking {
            queue.update(id, EntryFields(description = "First edit"), start, stop)
            queue.update(id, EntryFields(description = "Second edit"), start, stop)
        }
        account.deleteEntry(id)
        assertEquals(ChangeSender.Outcome.Empty(2), sender.drain())
        assertEquals(0, count())
        assertEquals(2, discarded)
        assertNull(database.changes().entry(id))
    }

    @Test fun a400HoldsItsEntryWhileOthersSendAndAFixResendsIt() {
        val tooLong = start.plus(Duration.ofHours(1000))
        val id = runBlocking { queue.create(account.workspaceId, EntryFields(description = "Invalid span"), start, tooLong) }
        runBlocking {
            queue.update(id, EntryFields(description = "Later change"), start, tooLong)
            queue.create(account.workspaceId, EntryFields(description = "Unrelated"), start, stop)
        }
        assertEquals(ChangeSender.Outcome.Empty(1), sender.drain())
        val held = runBlocking { queue.held.first() }.single()
        assertEquals("Time entries can not be longer than 999 hours", held.refusal)
        assertEquals(2, count())
        assertNull(RoomChangeStore(database).first())

        // The entry form uses the same update operation. It replaces the refused draft and
        // its dependent changes with the edited draft, keeping a local create a create.
        assertTrue(runBlocking { queue.update(id, EntryFields(description = "Fixed"), start, stop) })
        assertEquals(ChangeSender.Outcome.Empty(1), sender.drain())
        assertEquals(0, count())
        val serverId = runBlocking { queue.currentId(id) }
        assertEquals("Fixed", account.entry(serverId).getValue("description").toString().trim('"'))
        assertEquals(0, discarded)
    }

    @Test fun editingAHeldCreateKeepsExistingEntriesOutOfALostPostLookup() {
        val fixedStart = start.minus(Duration.ofHours(4)).truncatedTo(ChronoUnit.HOURS)
        val fixedStop = fixedStart.plusSeconds(1800)
        val existing = account.createEntry("Fixed", fixedStart, fixedStop)
        database.changes().upsertEntry(web.get(web.server.api("me", "time_entries", existing.toString()), ServerTimeEntry.serializer())
            .orFail("GET existing entry").toCached()!!)
        val local = runBlocking {
            queue.create(account.workspaceId, EntryFields(description = "Invalid"), start, start.plus(Duration.ofHours(1000)))
        }
        assertEquals(ChangeSender.Outcome.Empty(0), sender.drain())
        assertTrue(runBlocking { queue.update(local, EntryFields(description = "Fixed"), fixedStart, fixedStop) })
        // A request that never reached the server can still lose its answer. Before retrying,
        // its lookup must exclude the entry that existed before this edit moved the start.
        val store = RoomChangeStore(database)
        assertTrue(store.maybeSent(checkNotNull(store.first()), true))
        assertEquals(ChangeSender.Outcome.Empty(1), sender.drain())
        assertNotEquals("the retry must create its own entry", existing, runBlocking { queue.currentId(local) })
        assertEquals(0, count())
    }

    @Test fun aHeldChangeCanBeSentAgainOrDiscardedWithItsCachedDraft() {
        val id = runBlocking {
            queue.create(account.workspaceId, EntryFields(description = "Discard me"), start, start.plus(Duration.ofHours(1000)))
        }
        assertEquals(ChangeSender.Outcome.Empty(0), sender.drain())
        val held = runBlocking { queue.held.first() }.single()
        assertTrue(runBlocking { queue.sendAgain(held.sequence) })
        assertEquals(ChangeSender.Outcome.Empty(0), sender.drain())
        assertEquals(held.refusal, runBlocking { queue.held.first() }.single().refusal)
        assertTrue(runBlocking { queue.discard(held.sequence) })
        assertEquals(0, count())
        assertNull(database.changes().entry(id))
        assertFalse(runBlocking { queue.discard(held.sequence) })
    }

    @Test fun resetToken403PausesEverythingUntilTheSameAccountSignsInAgain() {
        val id = cachedEntry("Before reset")
        runBlocking {
            queue.update(id, EntryFields(description = "After reset"), start, stop)
            queue.create(account.workspaceId, EntryFields(description = "Waiting create"), start, stop)
        }
        val newToken = web.send("POST", web.server.api("me", "reset_token"), JsonObject(emptyMap()), String.serializer())
            .orFail("POST /me/reset_token")
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), sender.drain())
        assertTrue(sender.signInNeeded.value)
        assertEquals(2, count())
        // Even a valid token cannot lift the pause without the explicit successful sign-in.
        token = newToken
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), sender.drain())
        assertEquals(2, count())
        val signIn = SignIn(session = { _, accepted, _ -> token = accepted })
        assertEquals(SignInResult.SignedIn, signIn.attempt(TokklTestServer.url, SignInCredentials.ApiToken(newToken),
            SignInAgain(TokklTestServer.url, account.id)))
        runBlocking { sender.resume().join() }
        assertFalse(sender.signInNeeded.value)
        assertEquals(0, count())
        assertEquals(0, discarded)
        val accepted = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(newToken))
        assertEquals("After reset", accepted.get(accepted.server.api("me", "time_entries", id.toString()), JsonObject.serializer())
            .orFail("GET edited entry").getValue("description").toString().trim('"'))
    }

    private fun sender(http: OkHttpClient? = null): ChangeSender = ChangeSender(
        RoomChangeStore(database),
        client = { if (http == null) TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(token))
            else TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(token), httpClient = http) },
        scope = scope, io = Dispatchers.IO, onDiscarded = { discarded += it },
    )

    private fun cachedEntry(description: String): Long {
        val id = account.createEntry(description, start, stop)
        val entry = web.get(web.server.api("me", "time_entries", id.toString()), ServerTimeEntry.serializer())
            .orFail("GET cached entry").toCached()!!
        database.changes().upsertEntry(entry)
        return id
    }

    private fun count(): Int = runBlocking { queue.unsentCount.first() }
}
