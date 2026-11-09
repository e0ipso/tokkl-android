package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.HttpMethod
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.api
import com.github.e0ipso.tokkl.appApiClient
import com.github.e0ipso.tokkl.appConfiguredServer
import com.github.e0ipso.tokkl.appHttp
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.capabilitiesOf
import com.github.e0ipso.tokkl.signin.ServerSignIn
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInCredentials
import com.github.e0ipso.tokkl.signin.SignInResult
import com.github.e0ipso.tokkl.tokklApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        queue = ChangeQueue(
            RoomChangeStore(database),
            Dispatchers.IO,
            onQueued = {},
            clock = Clock.systemUTC(),
            spans = ServerSpans.Unsnapped,
        )
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

    @Test fun a404DropsChangesToTheDeletedEntryAndAggregatesTheNotice() {
        val id = cachedEntry("Deleted elsewhere")
        runBlocking {
            queue.update(EntryId(id), EntryFields(description = "First edit"), EntryTiming.Stopped(start, stop))
            queue.update(EntryId(id), EntryFields(description = "Second edit"), EntryTiming.Stopped(start, stop))
        }
        account.deleteEntry(id)
        assertEquals(ChangeSender.Outcome.Empty(2), runBlocking { sender.sendAndWait() })
        assertEquals(0, count())
        assertEquals(2, discarded)
        assertNull(database.changes().entry(id))
    }

    @Test fun a400HoldsItsEntryWhileOthersSendAndAFixResendsIt() {
        val tooLong = start.plus(Duration.ofHours(1000))
        val id = runBlocking {
            queue.create(
                WorkspaceId(account.workspaceId),
                EntryFields(description = "Invalid span"),
                EntryTiming.Stopped(start, tooLong),
            )
        }
        runBlocking {
            queue.update(id, EntryFields(description = "Later change"), EntryTiming.Stopped(start, tooLong))
            queue.create(
                WorkspaceId(account.workspaceId),
                EntryFields(description = "Unrelated"),
                EntryTiming.Stopped(start, stop),
            )
        }
        assertEquals(ChangeSender.Outcome.Empty(1), runBlocking { sender.sendAndWait() })
        val held = runBlocking { queue.held.first() }.single()
        assertEquals("Time entries can not be longer than 999 hours", held.refusal)
        assertEquals(2, count())
        assertNull(RoomChangeStore(database).first())

        // The entry form uses the same update operation. It replaces the refused draft and
        // its dependent changes with the edited draft, keeping a local create a create.
        assertTrue(
            runBlocking { queue.update(id, EntryFields(description = "Fixed"), EntryTiming.Stopped(start, stop)) },
        )
        assertEquals(ChangeSender.Outcome.Empty(1), runBlocking { sender.sendAndWait() })
        assertEquals(0, count())
        val serverId = runBlocking { queue.currentId(id) }
        assertEquals("Fixed", account.entry(serverId.value).getValue("description").toString().trim('"'))
        assertEquals(0, discarded)
    }

    @Test fun editingAHeldCreateKeepsExistingEntriesOutOfALostPostLookup() {
        val fixedStart = start.minus(Duration.ofHours(4)).truncatedTo(ChronoUnit.HOURS)
        val fixedStop = fixedStart.plusSeconds(1800)
        val existing = account.createEntry("Fixed", fixedStart, fixedStop)
        database.changes().upsertEntry(
            cachedRowOf(
                runBlocking {
                    web.get(
                        web.server.api("me", "time_entries", existing.toString()),
                        ServerTimeEntry.serializer(),
                    )
                }
                    .orFail("GET existing entry").toTimeEntry()!!,
            ),
        )
        val local = runBlocking {
            queue.create(
                WorkspaceId(account.workspaceId),
                EntryFields(description = "Invalid"),
                EntryTiming.Stopped(start, start.plus(Duration.ofHours(1000))),
            )
        }
        assertEquals(ChangeSender.Outcome.Empty(0), runBlocking { sender.sendAndWait() })
        assertTrue(
            runBlocking {
                queue.update(
                    local,
                    EntryFields(description = "Fixed"),
                    EntryTiming.Stopped(fixedStart, fixedStop),
                )
            },
        )
        // A request that never reached the server can still lose its answer. Before retrying,
        // its lookup must exclude the entry that existed before this edit moved the start.
        val store = RoomChangeStore(database)
        assertTrue(store.maybeSent(checkNotNull(store.first()), true))
        assertEquals(ChangeSender.Outcome.Empty(1), runBlocking { sender.sendAndWait() })
        assertNotEquals("the retry must create its own entry", existing, runBlocking { queue.currentId(local) }.value)
        assertEquals(0, count())
    }

    @Test fun aHeldChangeCanBeSentAgainOrDiscardedWithItsCachedDraft() {
        val id = runBlocking {
            queue.create(
                WorkspaceId(account.workspaceId),
                EntryFields(description = "Discard me"),
                EntryTiming.Stopped(start, start.plus(Duration.ofHours(1000))),
            )
        }
        assertEquals(ChangeSender.Outcome.Empty(0), runBlocking { sender.sendAndWait() })
        val held = runBlocking { queue.held.first() }.single()
        assertTrue(runBlocking { queue.sendAgain(held.sequence) })
        assertEquals(ChangeSender.Outcome.Empty(0), runBlocking { sender.sendAndWait() })
        assertEquals(held.refusal, runBlocking { queue.held.first() }.single().refusal)
        assertTrue(runBlocking { queue.discard(held.sequence) })
        assertEquals(0, count())
        assertNull(database.changes().entry(id.value))
        assertFalse(runBlocking { queue.discard(held.sequence) })
    }

    @Test fun resetToken403PausesEverythingUntilTheSameAccountSignsInAgain() {
        val id = cachedEntry("Before reset")
        runBlocking {
            queue.update(EntryId(id), EntryFields(description = "After reset"), EntryTiming.Stopped(start, stop))
            queue.create(
                WorkspaceId(account.workspaceId),
                EntryFields(description = "Waiting create"),
                EntryTiming.Stopped(start, stop),
            )
        }
        val newToken = runBlocking {
            web.send(
                HttpMethod.POST,
                web.server.api("me", "reset_token"),
                JsonObject(emptyMap()),
                String.serializer(),
            )
        }
            .orFail("POST /me/reset_token")
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), runBlocking { sender.sendAndWait() })
        assertTrue(sender.signInNeeded.value)
        assertEquals(2, count())
        // Even a valid token cannot lift the pause without the explicit successful sign-in.
        token = newToken
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), runBlocking { sender.sendAndWait() })
        assertEquals(2, count())
        val signIn = ServerSignIn(
            session = { _, accepted, _ -> token = accepted },
            connector = appHttp,
            configured = appConfiguredServer,
            scope = scope,
            io = Dispatchers.IO,
            signedInAccount = { null },
        )
        assertEquals(
            SignInResult.SignedIn,
            runBlocking {
                signIn.attempt(
                    TokklTestServer.url,
                    SignInCredentials.ApiToken(newToken),
                    SignInAgain(TokklTestServer.url, account.id),
                )
            },
        )
        sender.resume()
        runBlocking { sender.sendAndWait() }
        assertFalse(sender.signInNeeded.value)
        assertEquals(0, count())
        assertEquals(0, discarded)
        val accepted = appApiClient(TokklTestServer.url, ApiCredentials.ApiToken(newToken))
        assertEquals(
            "After reset",
            runBlocking {
                accepted.get(
                    accepted.server.api("me", "time_entries", id.toString()),
                    JsonObject.serializer(),
                )
            }
                .orFail("GET edited entry").getValue("description").toString().trim('"'),
        )
    }

    private fun sender(http: OkHttpClient? = null): ChangeSender {
        val store = RoomChangeStore(database)
        val client = ApiClientProvider {
            if (http == null) {
                appApiClient(TokklTestServer.url, ApiCredentials.ApiToken(token)).tokklApi()
            } else {
                TogglApiClient(
                    TokklTestServer.url,
                    ApiCredentials.ApiToken(token),
                    httpClient = http,
                    io = Dispatchers.IO,
                ).tokklApi()
            }
        }
        return sender(store, client)
    }

    private fun sender(store: ChangeStore, client: ApiClientProvider): ChangeSender = ChangeSender(
        store,
        entries = { client()?.let(::ServerEntrySends) },
        scope = scope,
        io = Dispatchers.IO,
        events = object : ChangeSenderEvents {
            override fun sent() = Unit

            override fun emptied() = Unit

            override fun discarded(count: Int) {
                discarded += count
            }
        },
        clock = Clock.systemUTC(),
        spans = ServerSpans.Unsnapped,
        capabilities = capabilitiesOf(Capabilities.NONE),
        undo = DeleteUndoer(
            store,
            { client()?.let(::ServerEntrySends) },
            Dispatchers.IO,
            AccountRefreshRequests(),
            scope,
        ),
    )

    private fun cachedEntry(description: String): Long {
        val id = account.createEntry(description, start, stop)
        val entry = runBlocking {
            web.get(
                web.server.api("me", "time_entries", id.toString()),
                ServerTimeEntry.serializer(),
            )
        }
            .orFail("GET cached entry").toTimeEntry()!!
        database.changes().upsertEntry(cachedRowOf(entry))
        return id
    }

    private fun count(): Int = runBlocking { queue.unsentCount.first() }
}
