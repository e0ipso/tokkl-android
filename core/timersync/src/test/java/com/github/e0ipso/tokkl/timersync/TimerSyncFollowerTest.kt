package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.timerstate.TimerStateIntake
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock

/**
 * When [TimerSyncFollower] starts, points and stops the timer sync service and its stream: it
 * holds the stream only for the service instance that runs now, and its stop requests reach only
 * a service that runs when they are made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerSyncFollowerTest {

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))

    /** The account each stream opened for, in order, and the stream's link. */
    private val opens = mutableListOf<TimerStateIntake>()
    private val links = mutableListOf<TimerSyncLink>()
    private val sync = TimerSync<TimerStateIntake>(
        scope = scope,
        now = { 0L },
        connections = { account, link ->
            opens += account
            links += link
        },
    )

    /** How many refused credentials the follower reported, and how many reports fail next. */
    private var refusals = 0
    private var failingRefusals = 0

    private var starts = 0
    private var timerApi = true
    private var account = source()
    private val follower = TimerSyncFollower(
        startService = {
            starts++
            true
        },
        signedIn = { true },
        timerState = { account },
        timerSync = sync,
        timerApi = { timerApi },
        signInNeeded = MutableStateFlow(false),
        syncWatcher = {},
        refusal = {
            if (failingRefusals-- > 0) throw IOException("queue unwritable")
            refusals++
        },
        scope = scope,
    )

    /** Every stop request a running service received. */
    private var stopsReceived = 0

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun withNoServiceRunningAFollowStartsTheServiceAndOpensNoStream() = runTest {
        follower.follow()

        assertEquals(1, starts)
        assertEquals(emptyList<TimerStateSource>(), opens)
        assertNull(follower.runningService.value)
    }

    @Test
    fun aRunningServiceHoldsTheStreamForTheAccountSignedInNow() = runTest {
        val instance = follower.serviceRuns()
        assertEquals(instance, follower.runningService.value)

        follower.follow()

        assertEquals("no second start", 0, starts)
        assertEquals(listOf(account), opens)
    }

    @Test
    fun aServiceThatEndedCannotOpenTheStream() = runTest {
        val ended = follower.serviceRuns()
        follower.serviceEnded(ended)

        follower.run(ended)

        assertNull(follower.runningService.value)
        assertEquals(emptyList<TimerStateSource>(), opens)
    }

    @Test
    fun onlyTheInstanceThatRunsNowHoldsTheStream() = runTest {
        val old = follower.serviceRuns()
        follower.serviceEnded(old)
        val current = follower.serviceRuns()
        // An old instance's end that arrives late leaves the current one marked.
        follower.serviceEnded(old)
        assertEquals(current, follower.runningService.value)

        follower.run(old)
        assertEquals("the old instance's startup opens nothing", emptyList<TimerStateSource>(), opens)

        follower.run(current)
        assertEquals(listOf(account), opens)
    }

    @Test
    fun aFollowThatNoLongerWantsTheStreamAsksTheRunningServiceToStop() = runTest {
        follower.serviceRuns()
        collectStopRequests()
        follower.follow()
        assertEquals(1, opens.size)

        timerApi = false
        follower.follow()

        assertEquals(1, stopsReceived)
        assertEquals("nothing starts", 0, starts)
        // The stream is stopped, so the same account opens it again once wanted.
        timerApi = true
        follower.follow()
        assertEquals(2, opens.size)
    }

    @Test
    fun aStopRequestedWhileNoServiceRunsReachesNoLaterService() = runTest {
        follower.stopService()

        follower.serviceRuns()
        collectStopRequests()

        assertEquals(0, stopsReceived)
        follower.stopService()
        assertEquals(1, stopsReceived)
    }

    @Test
    fun theStreamFollowsTheAccountSignedInNow() = runTest {
        follower.serviceRuns()
        follower.follow()
        val first = account

        account = source()
        follower.follow()

        assertEquals(listOf(first, account), opens)
        assertTrue(opens[0] !== opens[1])
    }

    @Test
    fun aRefusedCredentialOfTheAccountSignedInNowAsksToSignInAgainAndStopsTheService() = runTest {
        follower.serviceRuns()
        collectStopRequests()
        follower.follow()

        links.single().ended(StreamEnd.Refused)

        assertEquals(1, refusals)
        assertEquals(1, stopsReceived)
    }

    @Test
    fun aStreamOfAnAccountSignedOutSinceOnlyStopsTheService() = runTest {
        follower.serviceRuns()
        collectStopRequests()
        follower.follow()
        account = source()

        links.single().ended(StreamEnd.Refused)

        assertEquals("the account signed in now was not refused", 0, refusals)
        assertEquals(1, stopsReceived)
    }

    @Test
    fun aRefusalWhoseReportFailedIsReportedAgainAfterAWait() = runTest {
        follower.serviceRuns()
        collectStopRequests()
        follower.follow()
        failingRefusals = 1

        links.single().ended(StreamEnd.Refused)
        assertEquals(0, refusals)
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()

        assertEquals(1, refusals)
        assertEquals(1, stopsReceived)
    }

    /** Collects the stop requests as the running service does. */
    private fun collectStopRequests() {
        scope.launch { follower.stopRequests.collect { stopsReceived++ } }
    }

    private fun source() = TimerStateSource(
        initial = null,
        store = {},
        cache = object : CurrentEntryCache {
            override fun runningCheckStart(): RunningCheckStart = error("the follower reads no cache")

            override fun checkedRunning(before: RunningCheckStart, server: TimeEntry?) =
                error("the follower writes no cache")
        },
        clock = Clock.systemUTC(),
        runningEnded = {},
    )
}
