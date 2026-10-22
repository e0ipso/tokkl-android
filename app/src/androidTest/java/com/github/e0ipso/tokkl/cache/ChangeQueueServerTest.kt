package com.github.e0ipso.tokkl.cache

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.SnappedSpan
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The queue against the real Tokkl container, through the app's [ChangeQueue] and [ChangeSender],
 * since no timer screen exists yet. The app talks to the server through a [HangingRelay], so a
 * test can make the network hang or hold one answer. The test's own requests, which stand in for the web app, go
 * straight to the server. Each test seeds its own account and deletes it after.
 */
class ChangeQueueServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private lateinit var relay: HangingRelay
    private var workspace = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInThroughTheRelay() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        relay = HangingRelay(TokklTestServer.url)
        signIn(relay.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        assertNotNull("the refresh through the relay stored the settings", app.accountSettings.state.value)
    }

    @After
    fun cleanUp() {
        try {
            if (::relay.isInitialized) relay.close()
        } finally {
            try {
                // TestAccounts talks to the server through the app's guard, which follows the session.
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun startAndStopShowAtOnceWhileTheNetworkHangsAndTheServerGetsTheTapTimes() {
        relay.hang()
        // The cache keeps times in milliseconds.
        val startTap = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val (id, startTook) = timed { runBlocking { app.changes.start(workspace, EntryFields(description = "Offline timer")) } }
        val running = runBlocking { app.cache.running.first() }
        val afterStart = Instant.now()

        assertTrue("Start returned without waiting for the network: $startTook", startTook < AT_ONCE)
        assertNotNull("the timer runs at once", running)
        assertEquals(id, running!!.entry.id)
        assertTrue("the start is marked unsent", running.unsent)
        assertTrue("the start is the tap time", running.entry.start in startTap..afterStart)
        waitUntil("the sender's request hangs in the network") { relay.hungConnections > 0 }
        assertEquals(1, unsentCount())

        relay.release()
        drainUntilEmpty()
        val sent = serverEntry(serverId(id))
        assertEquals("Offline timer", sent.string("description"))
        assertEquals(
            "the server keeps the tap time as the start",
            running.entry.start.truncatedTo(ChronoUnit.SECONDS),
            sent.instant("start"),
        )
        assertTrue("a running entry has no stop", sent["stop"] is JsonNull)
        assertFalse("sent", runBlocking { app.cache.running.first() }!!.unsent)

        relay.hang()
        val stopTap = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val (stopped, stopTook) = timed { runBlocking { app.changes.stop(id) } }
        val afterStop = Instant.now()
        assertTrue("Stop took the entry's local id after the server mapped it", stopped)
        assertTrue("Stop returned without waiting for the network: $stopTook", stopTook < AT_ONCE)
        assertNull("nothing runs at once", runBlocking { app.cache.running.first() })
        assertNull("an immediate Stop predicts Trash", time().snapCapturedStop(running.entry.start, stopTap))
        assertNull("the same prediction holds after the call", time().snapCapturedStop(running.entry.start, afterStop))
        assertTrue("the short timer leaves history offline", runBlocking { app.cache.entries.first() }.none { it.entry.id == serverId(id) })
        assertEquals("the captured Stop waits durably", 1, unsentCount())
        assertEquals(serverId(id), runBlocking { app.changes.shortStopUndo.first() }?.entryId)

        relay.release()
        drainUntilEmpty()
        val trashed = web.get(web.server.app("trash"), ListSerializer(JsonObject.serializer())).orFail("GET /api/app/trash")
            .single { it.getValue("id").jsonPrimitive.long == serverId(id) }
        assertEquals("the server stores zero duration", 0L, trashed.getValue("duration").jsonPrimitive.long)
        assertEquals(trashed.instant("start"), trashed.instant("stop"))
        assertNull("the server no longer runs it", runningOnServer())

        relay.hang()
        assertTrue("Undo is queued offline", runBlocking { app.changes.undoShortStop(id) })
        val restoredLocally = runBlocking { app.cache.entries.first() }.single { it.entry.id == serverId(id) }
        assertEquals(restoredLocally.entry.start, restoredLocally.entry.stop)
        assertTrue(restoredLocally.unsent)
        relay.release()
        drainUntilEmpty()
        val restored = serverEntry(serverId(id))
        assertEquals("Undo restores the stopped zero-length row", restored.instant("start"), restored.instant("stop"))
    }

    @Test
    fun createEditStopAndDeleteOfAnOfflineEntryReachTheServerInOrder() {
        relay.hang()
        val timer = runBlocking { app.changes.start(workspace, EntryFields(description = "Started offline")) }
        assertTrue(
            runBlocking {
                app.changes.update(timer, EntryFields(description = "Edited offline"), app.cache.running.first()!!.entry.start, stop = null)
            },
        )
        assertTrue(runBlocking { app.changes.stop(timer) })
        assertTrue("a short Stop can be undone before sending", runBlocking { app.changes.undoShortStop(timer) })
        val manualStart = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        val manual = runBlocking {
            app.changes.create(
                workspace,
                EntryFields(description = "Made by hand", tags = listOf("typed offline"), billable = true),
                start = manualStart,
                stop = manualStart.plus(Duration.ofMinutes(30)),
            )
        }
        assertTrue(runBlocking { app.changes.delete(timer) })
        assertEquals(6, unsentCount())
        assertEquals(
            "the deleted timer is gone from the phone and the manual entry shows, unsent",
            listOf(manual to true),
            runBlocking { app.cache.entries.first() }.map { it.entry.id to it.unsent },
        )

        relay.release()
        drainUntilEmpty()

        val made = serverEntry(serverId(manual))
        assertEquals("Made by hand", made.string("description"))
        assertEquals(listOf("typed offline"), made.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(manualStart, made.instant("start"))
        val tags = web.get(web.server.api("me", "tags"), ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/tags")
        assertEquals("Tokkl made the tag typed offline", listOf("typed offline"), tags.map { it.string("name") })

        val trash = web.get(web.server.app("trash"), ListSerializer(JsonObject.serializer())).orFail("GET /api/app/trash")
        val deleted = trash.single()
        assertEquals("the timer was created, edited, stopped and then deleted", serverId(timer), deleted.getValue("id").jsonPrimitive.long)
        assertEquals("Edited offline", deleted.string("description"))
        assertTrue("stopped before the delete", deleted["stop"] !is JsonNull)
        assertEquals(
            "after the refresh that follows the drain, the phone shows the server's entry",
            listOf(serverId(manual) to false),
            runBlocking { app.cache.entries.first() }.map { it.entry.id to it.unsent },
        )
    }

    @Test
    fun theChangeThatReachesTheServerLastWinsWithoutAMerge() {
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        val entry = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Seeded")
                put("start", start.toString())
                put("duration", 1800)
            },
            JsonObject.serializer(),
        ).orFail("POST a time entry").getValue("id").jsonPrimitive.long
        runBlocking { app.accountSettings.refresh().join() }

        relay.hang()
        val phone = EntryFields(description = "From the phone")
        assertTrue(runBlocking { app.changes.update(entry, phone, start, start.plus(Duration.ofMinutes(30))) })
        editOnTheWeb(entry, "From the web", billable = true)
        relay.release()
        drainUntilEmpty()

        val afterPhone = serverEntry(entry)
        assertEquals("the phone's change came last and wins", "From the phone", afterPhone.string("description"))
        assertEquals("with every field it sent, not merged", false, afterPhone.getValue("billable").jsonPrimitive.content.toBoolean())

        editOnTheWeb(entry, "From the web, later", billable = true)
        runBlocking { app.accountSettings.refresh().join() }
        val row = runBlocking { app.cache.entries.first() }.single { it.entry.id == entry }
        assertEquals("the web app's change came last and wins on the phone too", "From the web, later", row.entry.description)
        assertFalse(row.unsent)
        assertEquals(0, unsentCount())
    }

    @Test
    fun aRefreshThatReadTheTimerBeforeItsStopWasSentDoesNotLeaveItRunning() {
        val start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        val entry = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Started on the web")
                put("start", start.toString())
                put("duration", -1)
            },
            JsonObject.serializer(),
        ).orFail("POST a running time entry").getValue("id").jsonPrimitive.long
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals("the phone shows the web's timer", entry, runBlocking { app.cache.running.first() }?.entry?.id)

        // The server answers a refresh with the running entry, and the answer is slow to reach the phone.
        relay.holdAnswerTo(TIME_ENTRIES)
        val stale = app.accountSettings.refresh()
        waitUntil("the server answered the refresh's entries") { relay.holdsAnswer }
        assertTrue(runBlocking { app.changes.stop(entry) })
        waitUntil("the sender sent the stop") { unsentCount() == 0 }
        val stopped = serverEntry(entry)
        assertTrue("the server has the stop", stopped["stop"] !is JsonNull)

        relay.releaseAnswer()
        runBlocking { stale.join() }
        waitUntil("the cache shows the timer stopped, as the server has it") {
            runBlocking { app.cache.running.first() } == null
        }
        val row = runBlocking { app.cache.entries.first() }.single { it.entry.id == entry }
        assertEquals(stopped.instant("stop"), row.entry.stop)
        assertFalse(row.unsent)
    }

    @Test
    fun aQueuedStartEndsWhereALaterTimerOnTheServerStartsAndThatTimerKeepsRunning() {
        // A phone start in the second before a half step would make the laptop's start below snap
        // a step later, where a span that rounded its duration up ends too.
        awayFromAHalfStep()
        val id = queueWithNoRoute { app.changes.start(workspace, EntryFields(description = "Started on the phone")) }
        val queued = runBlocking { app.cache.running.first() }!!.entry
        // Half an hour and a second after the phone's start, which still waits in the queue, the
        // laptop starts a timer. A span that rounded its duration up would end a step after where
        // that start snaps.
        val laterStart = queued.start.truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(30)).plusSeconds(1)
        val laptop = startOnTheWeb("Started on the laptop", laterStart)

        val reads = drainCountingRunningEntryReads()

        val later = serverEntry(laptop)
        assertTrue("the later timer still runs: $later", later["stop"] is JsonNull)
        assertEquals(laterStart, later.instant("start"))
        val phone = serverEntry(serverId(id))
        assertEquals(time().snapInstant(queued.start), phone.instant("start"))
        assertEquals(
            "the phone shows the server's running entry without waiting for a refresh",
            laptop,
            runBlocking { app.cache.running.first() }?.entry?.id,
        )
        assertEquals("one GET of the running entry for the queued start, and no more", 1, reads)

        // Once the laptop stops its timer, its start snaps to the grid point where the phone's entry ends.
        val laterStopped = stopOnTheWeb(laptop)
        assertEquals("the phone's entry ends where the later timer's start snaps: $phone", laterStopped.instant("start"), phone.instant("stop"))
        assertNotEquals(
            "a span that rounded its duration up ends elsewhere, so this test tells the two apart",
            legacyRoundedStop(queued.start, laterStart),
            phone.instant("stop"),
        )
    }

    @Test
    fun aTimerStartedOnThePhoneEndsTheRunningOneWhereItsOwnStartSnaps() {
        // A span from this start that rounded its duration up would end a step after the new
        // timer's start snaps, as long as that start keeps clear of a half step.
        val start = startPastAHalfStep(Duration.ofHours(1))
        val earlier = startOnTheWeb("Started on the web", start)
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals("the phone shows the timer", earlier, runBlocking { app.cache.running.first() }?.entry?.id)

        awayFromAHalfStep()
        val before = Instant.now()
        val id = runBlocking { app.changes.start(workspace, EntryFields(description = "Started on the phone")) }
        val started = Instant.now()
        drainUntilEmpty()

        val stopped = serverEntry(earlier)
        assertEquals(time().snapInstant(start), stopped.instant("start"))
        // Once the phone's timer stops, its start snaps to the grid point where the earlier one ends.
        val later = stopOnTheWeb(serverId(id))
        assertEquals("the two entries are back to back: $stopped, $later", later.instant("start"), stopped.instant("stop"))
        assertRoundingUpEndsElsewhere(start, before..started, stopped.instant("stop"))
    }

    @Test
    fun aStopTappedOnThePhoneIsStoredAsTheWebAppStoresAStopAtTheSameMoment() {
        // The timers run for no whole number of steps, and a span from this start that rounded its
        // duration up would end a step after the web app's stop, as long as the stops keep clear
        // of a half step.
        val start = startPastAHalfStep(Duration.ofHours(1))
        val onPhone = startOnTheWeb("Stopped on the phone", start)
        runBlocking { app.accountSettings.refresh().join() }
        assertEquals("the phone shows the timer", onPhone, runBlocking { app.cache.running.first() }?.entry?.id)

        relay.hang()
        awayFromAHalfStep()
        val before = Instant.now()
        assertTrue(runBlocking { app.changes.stop(onPhone) })
        val tapped = Instant.now()
        val after = Instant.now()
        relay.release()
        drainUntilEmpty()

        val stoppedOnPhone = serverEntry(onPhone)
        // PATCH refuses an entry another client already stopped, so leave this entry running on
        // the server until the queued captured Stop arrives and compare it to that tap window.
        val nearest = { at: Instant -> time().snapInstant(at) }
        val earliest = nearest(before.minus(CLOCK_MARGIN))
        val latest = nearest(after.plus(CLOCK_MARGIN))
        assertTrue("a stop on the grid point nearest the tap: $stoppedOnPhone", stoppedOnPhone.instant("stop") in earliest..latest)
        assertRoundingUpEndsElsewhere(start, before..tapped, stoppedOnPhone.instant("stop"))
        assertEquals(
            "after the refresh that follows the drain, the phone shows the span the server stored",
            stoppedOnPhone.instant("start") to stoppedOnPhone.instant("stop"),
            runBlocking { app.cache.entries.first() }.single { it.entry.id == onPhone }.entry.let { it.start to it.stop },
        )
    }

    @Test
    fun aQueuedStartRunsWhenNoTimerRunsOnTheServer() {
        val id = queueWithNoRoute { app.changes.start(workspace, EntryFields(description = "Started on the phone")) }
        val queued = runBlocking { app.cache.running.first() }!!.entry

        val reads = drainCountingRunningEntryReads()

        val phone = serverEntry(serverId(id))
        assertTrue("the queued start runs", phone["stop"] is JsonNull)
        assertEquals(queued.start.truncatedTo(ChronoUnit.SECONDS), phone.instant("start"))
        assertEquals(serverId(id), runningOnServer()?.getValue("id")?.jsonPrimitive?.long)
        assertEquals("one GET of the running entry for the queued start, and no more", 1, reads)
    }

    @Test
    fun aQueuedStartRunsAndTheServerStopsAnEarlierTimer() {
        // A second over the hour before the queued start, so the earlier timer runs for no whole
        // number of steps, and a stop that rounded its duration up would end a step later.
        val earlierStart = Instant.now().minus(1, ChronoUnit.HOURS).minusSeconds(1).truncatedTo(ChronoUnit.SECONDS)
        val laptop = startOnTheWeb("Started on the laptop", earlierStart)
        // The phone has not seen the laptop's timer, so its start queues no stop for it.
        val id = queueWithNoRoute { app.changes.start(workspace, EntryFields(description = "Started on the phone")) }
        val queued = runBlocking { app.cache.running.first() }!!.entry

        val reads = drainCountingRunningEntryReads()

        val phone = serverEntry(serverId(id))
        assertTrue("the queued start runs", phone["stop"] is JsonNull)
        assertEquals(serverId(id), runningOnServer()?.getValue("id")?.jsonPrimitive?.long)
        // Tokkl 1.5.0 stops a running timer on the grid point nearest the moment it stops, where
        // the queued start snaps once it stops too, so the two entries are stored back to back.
        val earlier = serverEntry(laptop)
        assertEquals(
            "the server stopped the earlier timer at the queued start: $earlier",
            time().snapInstant(queued.start),
            earlier.instant("stop"),
        )
        assertEquals("one GET of the running entry for the queued start, and no more", 1, reads)
    }

    @Test
    fun aCreateWhoseAnswerWasLostIsStoredOnceAndLaterChangesApplyToIt() {
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        relay.holdAnswerTo(CREATE)
        val id = runBlocking {
            app.changes.create(workspace, EntryFields(description = "Answer lost"), start, start.plus(Duration.ofMinutes(30)))
        }
        waitUntil("the server stored the entry and its answer is held") { relay.holdsAnswer }
        // The connection drops before the answer reaches the phone.
        relay.hang()
        val edited = EntryFields(description = "Edited after the lost answer")
        assertTrue(runBlocking { app.changes.update(id, edited, start, start.plus(Duration.ofMinutes(45))) })
        waitUntil("the sender's next request hangs in the network") { relay.hungConnections > 0 }
        relay.releaseAnswer()
        relay.release()
        drainUntilEmpty()

        val copies = serverEntriesAround(start)
        assertEquals("one copy on the server: $copies", 1, copies.size)
        val copy = copies.single()
        assertEquals(serverId(id), copy.getValue("id").jsonPrimitive.long)
        assertEquals("the queued edit reached that copy", "Edited after the lost answer", copy.string("description"))
        assertEquals(start.plus(Duration.ofMinutes(45)), copy.instant("stop"))
    }

    /**
     * Tokkl matches the tag names a create sends to its tags with no regard to case, so an entry
     * queued with "work" comes back tagged "Work" when the server has that tag (TKA-0206). The
     * lookup for a create whose answer was lost matches tags the same way, finds that entry and
     * posts no second copy.
     */
    @Test
    fun aCreateWhoseAnswerWasLostIsStoredOnceWhenTheServerHasItsTagInAnotherCase() {
        // Made on the web after the phone's refresh, so the phone does not know it.
        tagOnTheWeb("Work")
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        relay.holdAnswerTo(CREATE)
        val id = runBlocking {
            app.changes.create(workspace, EntryFields(description = "Tagged", tags = listOf("work")), start, start.plus(Duration.ofMinutes(30)))
        }
        waitUntil("the server stored the entry and its answer is held") { relay.holdsAnswer }
        // The connection drops before the answer reaches the phone, and the network stays quiet.
        relay.hang()
        runBlocking { app.sender.sendAndWait() }
        assertEquals("the create stays queued", 1, unsentCount())
        relay.releaseAnswer()
        relay.release()
        drainUntilEmpty()

        val copies = serverEntriesAround(start)
        assertEquals("one copy on the server: $copies", 1, copies.size)
        assertEquals(serverId(id), copies.single().getValue("id").jsonPrimitive.long)
        assertEquals("tagged with the server's name for the tag", listOf("Work"), copies.single().tags())
    }

    /**
     * An entry the web app made after the phone's refresh, with the description and times of a
     * queued create and one of its tags in another case but a tag more, is not the one the create
     * made. So the create, which may have reached the server and did not, is posted beside it.
     */
    @Test
    fun aCreateThatNeverReachedTheServerIsNotTakenForAnEntryWithOtherTags() {
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        val stop = start.plus(Duration.ofMinutes(30))
        tagOnTheWeb("Work")
        tagOnTheWeb("meeting")
        val other = entryOnTheWeb("Tagged", start, stop, tags = listOf("Work", "meeting"))
        relay.hang()
        val id = runBlocking { app.changes.create(workspace, EntryFields(description = "Tagged", tags = listOf("work")), start, stop) }
        // The relay drops the create on a connection it cut, or holds it on a new one until the
        // read times out. Either way the phone cannot tell whether the server stored it.
        runBlocking { app.sender.send().join() }
        assertEquals("the create stays queued", 1, unsentCount())
        relay.release()
        drainUntilEmpty()

        val entries = serverEntriesAround(start)
        assertEquals("the web app's entry and the create's: $entries", 2, entries.size)
        assertTrue("the create made an entry of its own", serverId(id) != other)
        assertEquals(setOf(other, serverId(id)), entries.map { it.getValue("id").jsonPrimitive.long }.toSet())
        assertEquals(listOf("Work"), serverEntry(serverId(id)).tags())
    }

    @Test
    fun aCreateThatNeverReachedTheServerIsPostedOnce() {
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        relay.hang()
        val id = runBlocking {
            app.changes.create(workspace, EntryFields(description = "Never reached"), start, start.plus(Duration.ofMinutes(30)))
        }
        // The relay drops the create on a connection it cut, or holds it on a new one until the
        // read times out. Either way the phone cannot tell whether the server stored it.
        runBlocking { app.sender.send().join() }
        assertEquals("the create stays queued", 1, unsentCount())
        relay.release()
        drainUntilEmpty()

        val copies = serverEntriesAround(start)
        assertEquals("one copy on the server: $copies", 1, copies.size)
        assertEquals(serverId(id), copies.single().getValue("id").jsonPrimitive.long)
        assertEquals("Never reached", copies.single().string("description"))
    }

    /**
     * A duplicate has every field and time of its source, so the lookup for a create that may
     * have reached the server must leave out the source, which the server held when the phone
     * queued the copy. Otherwise the copy would never be posted.
     */
    @Test
    fun aDuplicateThatNeverReachedTheServerIsPostedBesideItsSource() {
        val start = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
        val stop = start.plus(Duration.ofMinutes(30))
        val source = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Copied")
                put("start", start.toString())
                put("stop", stop.toString())
            },
            JsonObject.serializer(),
        ).orFail("POST a time entry").getValue("id").jsonPrimitive.long
        runBlocking { app.accountSettings.refresh().join() }
        relay.hang()
        val copy = runBlocking { app.changes.create(workspace, EntryFields(description = "Copied"), start, stop) }
        runBlocking { app.sender.send().join() }
        assertEquals("the copy stays queued", 1, unsentCount())
        relay.release()
        drainUntilEmpty()

        val entries = serverEntriesAround(start)
        assertEquals("the source and its copy: $entries", 2, entries.size)
        assertTrue("the copy is an entry of its own", serverId(copy) != source)
        assertEquals(setOf(source, serverId(copy)), entries.map { it.getValue("id").jsonPrimitive.long }.toSet())
    }

    /**
     * Queues [change] with no route to the server, as with no network, and waits until the drain
     * it starts has failed. The app is signed in through no relay afterwards: sign it in again.
     */
    private fun <T> queueWithNoRoute(change: suspend () -> T): T {
        signIn(NO_ROUTE, account.apiToken)
        val result = runBlocking { change() }
        runBlocking { app.sender.send().join() }
        assertTrue("the change stays queued", unsentCount() > 0)
        return result
    }

    /**
     * Sends the queue once, with no refresh after, through a counter of the app's reads of the
     * running entry, and returns how many it made.
     */
    private fun drainCountingRunningEntryReads(): Int =
        RequestCounter(TokklTestServer.url, RUNNING_ENTRY).use { counter ->
            signIn(counter.url, account.apiToken)
            val outcome = app.sender.drain()
            assertTrue("the queue is sent: $outcome", outcome is ChangeSender.Outcome.Empty && outcome.sent > 0)
            assertEquals(emptyList<String>(), counter.failures)
            counter.count
        }

    /** A timer the web app starts at [start], which may be in the future. */
    private fun startOnTheWeb(description: String, start: Instant): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", description)
                put("start", start.toString())
                put("duration", -1)
            },
            JsonObject.serializer(),
        ).orFail("POST a running time entry").getValue("id").jsonPrimitive.long

    /** The web app's Stop: Tokkl's `PATCH .../stop`, which stops [entry] at the server's clock. */
    private fun stopOnTheWeb(entry: Long): JsonObject =
        web.send(
            "PATCH",
            web.server.api("workspaces", workspace.toString(), "time_entries", entry.toString(), "stop"),
            // OkHttp sends a PATCH only with a body. Tokkl reads none.
            JsonObject(emptyMap()),
            JsonObject.serializer(),
        ).orFail("PATCH .../stop")

    private fun tagOnTheWeb(name: String) {
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "tags"),
            buildJsonObject { put("name", name) },
            JsonObject.serializer(),
        ).orFail("POST a tag")
    }

    /** A stopped entry the web app makes, tagged with the names in [tags]. */
    private fun entryOnTheWeb(description: String, start: Instant, stop: Instant, tags: List<String>): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", description)
                put("start", start.toString())
                put("stop", stop.toString())
                put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
            },
            JsonObject.serializer(),
        ).orFail("POST a time entry").getValue("id").jsonPrimitive.long

    private fun runningOnServer(): JsonObject? =
        web.get(web.server.api("me", "time_entries", "current"), JsonObject.serializer().nullable).orFail("GET the running entry")

    /** The server's entries that start within a day of [start]. */
    private fun serverEntriesAround(start: Instant): List<JsonObject> {
        val url = web.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", start.minus(Duration.ofDays(1)).toString())
            .addQueryParameter("end_date", start.plus(Duration.ofDays(1)).toString())
            .build()
        return web.get(url, ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/time_entries")
    }

    private fun time(): AccountTime = checkNotNull(app.accountSettings.state.value?.time()) { "no account time" }

    /** One step of the account's grid: a second's span rounds up to it. */
    private fun step(): Duration {
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val span = time().snapStop(at, at.plusSeconds(1), oneStep = true)
        return Duration.between(span.start, span.stop).also { check(it >= Duration.ofMinutes(1)) { "the account snaps to a grid: $it" } }
    }

    /** The grid point nearest [at], where Tokkl puts a start. */
    private fun gridPoint(at: Instant): Instant = time().snapInstant(at)

    /**
     * A start about [ago] before now, a second after a half step, so its grid point is almost half
     * a step later. A span from it that rounded its duration up ends a step after the grid point
     * nearest its stop, unless the stop falls in the two seconds from a half step on, which
     * [awayFromAHalfStep] keeps it out of.
     */
    private fun startPastAHalfStep(ago: Duration): Instant =
        gridPoint(Instant.now().minus(ago)).minus(step().dividedBy(2)).plusSeconds(1)

    /**
     * Waits until the next tap is at least [HALF_STEP_MARGIN] from the two seconds that start at a
     * half step of the grid, which takes a few seconds at most. A tap there could round to either
     * grid point, so a test could not tell where a span that rounded its duration up ends from
     * where the nearest grid point is.
     */
    private fun awayFromAHalfStep() {
        val half = step().dividedBy(2)
        while (true) {
            val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            val sinceHalfStep = Duration.between(gridPoint(now).minus(half), now)
            val clear = sinceHalfStep > HALF_STEP_MARGIN.plusSeconds(1) && sinceHalfStep < half.multipliedBy(2).minus(HALF_STEP_MARGIN)
            if (clear) return
            Thread.sleep(200)
        }
    }

    /**
     * Fails unless [stop], stored for a timer from [start] stopped within [tapped], differs from
     * where Tokkl puts a stop tapped then with the duration rounded up, as the phone's rule was
     * before its Stop went on the grid point nearest the tap.
     */
    private fun assertRoundingUpEndsElsewhere(start: Instant, tapped: ClosedRange<Instant>, stop: Instant) {
        for (at in listOf(tapped.start, tapped.endInclusive)) {
            assertNotEquals("the old duration-ceiling prediction ends elsewhere", legacyRoundedStop(start, at), stop)
        }
    }

    /** The old typed-span rule, retained only to prove these fixtures distinguish the rules. */
    private fun legacyRoundedStop(start: Instant, stop: Instant): Instant {
        val seconds = Duration.between(start.truncatedTo(ChronoUnit.SECONDS), stop.truncatedTo(ChronoUnit.SECONDS)).seconds
        val stepSeconds = step().seconds
        return gridPoint(start).plusSeconds(((seconds + stepSeconds - 1) / stepSeconds) * stepSeconds)
    }

    private fun editOnTheWeb(entry: Long, description: String, billable: Boolean) {
        web.send(
            "PUT",
            web.server.api("workspaces", workspace.toString(), "time_entries", entry.toString()),
            buildJsonObject {
                put("description", description)
                put("billable", billable)
                put("tags", JsonArray(listOf(JsonPrimitive("web"))))
            },
            JsonObject.serializer(),
        ).orFail("PUT a time entry")
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    /** Sends the queue until it is empty, as each change and refresh would. */
    private fun drainUntilEmpty() {
        val deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos()
        while (unsentCount() > 0) {
            if (System.nanoTime() > deadline) throw AssertionError("the queue still holds ${unsentCount()} changes")
            runBlocking { app.sender.send().join() }
        }
        // The drain's own refresh, so the cache shows the server's rows.
        runBlocking { app.accountSettings.refresh().join() }
    }

    private fun serverId(localId: Long): Long =
        runBlocking { app.changes.currentId(localId) }.also { check(it > 0) { "no server id for $localId" } }

    private fun serverEntry(id: Long): JsonObject =
        web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET a time entry")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.tags(): List<String> = getValue("tags").jsonArray.map { it.jsonPrimitive.content }

    /**
     * Tokkl writes times with an offset, such as +00:00. On API 30 Instant.parse refused that and
     * took only a Z, so the offset goes through OffsetDateTime, as in the app's ServerReader.
     */
    private fun JsonObject.instant(key: String): Instant = OffsetDateTime.parse(string(key)).toInstant()

    private fun <T> timed(block: () -> T): Pair<T, Duration> {
        val started = System.nanoTime()
        val result = block()
        return result to Duration.ofNanos(System.nanoTime() - started)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos()
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(50)
        }
    }

    private companion object {
        /** Far below OkHttp's 10-second timeouts, which a call that waited for the network would hit. */
        val AT_ONCE: Duration = Duration.ofSeconds(2)
        val DRAIN_TIMEOUT: Duration = Duration.ofSeconds(60)

        /** How far apart the phone's clock and the server's may be. */
        val CLOCK_MARGIN: Duration = Duration.ofSeconds(2)

        /** How far [awayFromAHalfStep] keeps a tap from a half step, for the calls between the wait and the tap. */
        val HALF_STEP_MARGIN: Duration = Duration.ofSeconds(3)

        /** The start of the cache refresh's request for the history window's entries. */
        const val TIME_ENTRIES = "GET /api/v9/me/time_entries?"

        /** The start of a create's request. */
        const val CREATE = "POST /api/v9/workspaces/"

        /** The end of the request line of a read of the running entry. */
        const val RUNNING_ENTRY = "/api/v9/me/time_entries/current HTTP/1.1"

        /** Nothing listens on this port, so every request fails for want of a route. */
        val NO_ROUTE: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
