package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session notification of the held session: its content comes from the server's phase,
 * number and upcoming phase with the planned end corrected by the clock offset, it follows every
 * revision, yields the shared id to the ring, and its buttons send the revision they showed for
 * their own account and phase only.
 */
class PomodoroSessionNotificationTest {

    private val key = PomodoroPhaseKey(T0)

    @Test fun contentShowsTheServersPhaseNumberAndUpcomingPhaseWithTheCorrectedPlannedEnd() {
        val work = checkNotNull(heldState(revision = 3, pomodoro = work(number = 2), offset = Duration.ofSeconds(40)).notificationContent())
        assertEquals(key, work.phase)
        assertEquals(PhaseKind.WORK, work.kind)
        assertEquals(PhaseKind.SHORT_REST, work.upcoming)
        assertEquals(2 to 4, work.number to work.longRestEvery)
        assertEquals("Focused work", work.description)
        // The native clock counts down to this instant on the phone's clock, then into overtime.
        assertEquals(T0.plusSeconds(1_500 - 40), work.end)
        assertEquals(3L, work.revision)

        val rest = checkNotNull(heldState(revision = 4, pomodoro = rest()).notificationContent())
        assertEquals(PhaseKind.SHORT_REST, rest.kind)
        assertEquals(PhaseKind.WORK, rest.upcoming)
        assertEquals(1, rest.number)
        assertEquals(T0.plusSeconds(1_800), rest.end)

        assertNull(heldState(revision = 5, pomodoro = null).notificationContent())
        // The description is the open pomodoro's, not another running entry's.
        assertEquals("", heldState(revision = 6, entryId = 999).notificationContent()?.description)
    }

    @Test fun idleRemovesAndASessionPostsOnceIncludingAfterObserverRecreation() = runBlocking {
        val f = Fixture(held = null)
        f.sync.refresh()
        assertEquals(1, f.removes)

        f.held = heldState(revision = 3)
        f.sync.refresh()
        val shown = f.saved
        f.sync.refresh()
        f.newSync().refresh()

        assertEquals(1, f.posts.size)
        assertEquals(shown, f.saved)
        assertFalse(checkNotNull(shown).shown!!.contains("Focused work"))
    }

    @Test fun eachRevisionReplacesTheNotificationAndADenialCanBeRepostedOnGrant() = runBlocking {
        val f = Fixture()
        f.allowed = false
        f.sync.refresh()
        assertNull(f.saved?.shown)
        assertEquals(0, f.posts.size)

        f.allowed = true
        f.sync.refresh(force = true)
        f.held = heldState(revision = 4, description = "Renamed on the web")
        f.sync.refresh()
        f.held = heldState(revision = 5, pomodoro = rest())
        f.sync.refresh()

        assertEquals(listOf(3L, 4L, 5L), f.posts.map { it.revision })
        assertEquals(listOf("Focused work", "Renamed on the web"), f.posts.take(2).map { it.description })
        assertEquals(PhaseKind.SHORT_REST, f.posts.last().kind)
        assertNotEquals(f.posts[0].key, f.posts[1].key)
    }

    @Test fun aFailedDisplayWriteDoesNotRepostAndTheNextEmissionPersistsIt() = runBlocking {
        val f = Fixture()
        f.writeFailure = true
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertNull(f.saved)

        f.writeFailure = false
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertEquals(f.posts.single().key, f.saved?.shown)

        f.writeFailure = true
        assertTrue(runCatching { f.sync.forget() }.isFailure)
        f.sync.refresh(force = true)
        assertEquals(1, f.posts.size)
        assertTrue(f.removes > 0)
    }

    @Test fun theRingOwnsTheSharedIdUntilTheServiceHasStopped() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        val removals = f.removes

        f.alarm.ringNow()
        f.sync.refresh()
        assertEquals(1, f.posts.size)
        assertEquals(removals, f.removes)
        assertNull(f.saved?.shown)

        // Next phase elsewhere: the ring stops, but the service is still stopping.
        f.serviceRunning = true
        f.held = heldState(revision = 4, pomodoro = rest())
        f.alarm.alarm.follow()
        f.sync.refresh()
        assertEquals(1, f.posts.size)

        f.serviceRunning = false
        f.sync.refresh()
        assertEquals(2, f.posts.size)
        assertEquals(PhaseKind.SHORT_REST, f.posts.last().kind)
        assertEquals(3, PomodoroSessionNotification.ID)
    }

    @Test fun aPhasePastItsPlannedEndLeavesTheSharedIdToItsAlarm() = runBlocking {
        // The ring must add its notification, not replace this one, or Android opens no
        // full-screen alarm. The alarm of a phase past its end goes off at once.
        val f = Fixture()
        f.alarm.clock.now = T0.plusSeconds(1_500)
        f.sync.refresh()
        assertEquals(0, f.posts.size)
        assertNull(f.saved?.shown)

        f.held = heldState(revision = 4, pomodoro = rest())
        f.sync.refresh()
        assertEquals(PhaseKind.SHORT_REST, f.posts.single().kind)
    }

    @Test fun withoutTheTimerApiOrSignedOutNothingIsPostedAndNothingIsSent() = runBlocking {
        val f = Fixture()
        f.alarm.available = false
        f.sync.refresh()
        assertEquals(0, f.posts.size)
        assertTrue(f.removes > 0)
        val account = checkNotNull(f.saved).account
        assertNull(f.sync.act(account, key, revision = 3, end = false))
        assertFalse(f.sync.acceptsTap(account, key))

        f.alarm.available = true
        f.signedIn = false
        f.sync.refresh(force = true)
        assertEquals(0, f.posts.size)
        assertNull(f.sync.act(account, key, revision = 3, end = true))
        assertFalse(f.sync.acceptsTap(account, key))
        assertTrue(f.server.calls.isEmpty())
    }

    @Test fun buttonsSendTheirShownRevisionOnlyForTheirAccountAndTheHeldPhase() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account

        assertNull(f.sync.act("another account", key, revision = 3, end = false))
        assertFalse(f.sync.acceptsTap("another account", key))
        assertTrue(f.sync.acceptsTap(account, key))
        assertEquals(TimerActionResult.Done, f.sync.act(account, key, revision = 3, end = false))

        // The server moved to the rest. The old phase's buttons and tap do nothing.
        f.held = f.server.held.value
        assertNull(f.sync.act(account, key, revision = 3, end = false))
        assertNull(f.sync.act(account, key, revision = 3, end = true))
        assertFalse(f.sync.acceptsTap(account, key))

        val rest = PomodoroPhaseKey(T0.plusSeconds(1_500))
        assertTrue(f.sync.acceptsTap(account, rest))
        assertEquals(TimerActionResult.Done, f.sync.act(account, rest, revision = 4, end = true))
        assertEquals(listOf("next 3", "end 4"), f.server.calls)
    }

    @Test fun aStaleRevisionGetsTheServersRefusalAndOfflineChangesNothing() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account

        // Another device renamed the pomodoro: the notification still shows revision 3.
        f.server.held.value = heldState(revision = 4, description = "Renamed")
        assertEquals(TimerActionResult.Refused, f.sync.act(account, key, revision = 3, end = false))

        f.server.answer = TimerActionResult.Offline
        assertEquals(TimerActionResult.Offline, f.sync.act(account, key, revision = 3, end = true))
        assertEquals(3L, f.held?.state?.revision)
        f.sync.refresh()
        assertEquals(1, f.posts.size)
    }

    @Test fun forgetCancelsARefreshWaitingForTheAlarmBeforeErasingTheDisplay() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        val account = checkNotNull(f.saved).account
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val locked = launch(start = CoroutineStart.UNDISPATCHED) {
            f.alarm.alarm.withState { _, _ -> entered.complete(Unit); release.await() }
        }
        entered.await()
        f.sync.start()
        // A new revision arrives while the alarm is busy; its refresh waits for the alarm.
        f.held = heldState(revision = 4)
        f.changes.emit(Unit)
        f.sync.forget()
        assertNull(f.saved)
        release.complete(Unit)
        locked.join()

        f.sync.refresh(force = true)
        assertEquals(1, f.posts.size)
        assertNull(f.sync.act(account, key, revision = 4, end = true))
        assertTrue(f.server.calls.isEmpty())
        assertNull(f.saved)
    }

    @Test fun aNewAccountRejectsAnOldIdentityEvenForTheSamePhase() = runBlocking {
        val f = Fixture()
        f.sync.refresh()
        val oldAccount = checkNotNull(f.saved).account
        f.sync.forget()

        val next = Fixture()
        next.sync.refresh()
        assertNotEquals(oldAccount, next.saved?.account)
        assertNull(next.sync.act(oldAccount, key, revision = 3, end = true))
        assertFalse(next.sync.acceptsTap(oldAccount, key))
        assertTrue(next.server.calls.isEmpty())
    }

    private class Fixture(held: HeldTimerState? = heldState(revision = 3)) {
        val alarm = AlarmFixture(held)
        val server = FakeServer(MutableStateFlow(held))
        var held: HeldTimerState?
            get() = alarm.held
            set(value) {
                alarm.held = value
                server.held.value = value
            }
        var saved: PomodoroNotificationDisplay? = null
        var writeFailure = false
        var allowed = true
        var signedIn = true
        var serviceRunning = false
        var removes = 0
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val posts = mutableListOf<PomodoroSessionNotificationContent>()
        val sync = newSync()

        init {
            // The server's answers become the held state, as the timer actions give them to it.
            server.advance = { current -> heldState((current?.state?.revision ?: 0) + 1, rest()).also { alarm.held = it } }
        }

        fun newSync() = PomodoroSessionNotificationSync(
            alarm.alarm, server, changes, { signedIn }, { serviceRunning },
            { saved }, { if (writeFailure) throw IOException("fixture write failed"); saved = it },
            { content, _ -> if (allowed) posts += content; allowed }, { removes++ },
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Dispatchers.Unconfined,
        )
    }
}
