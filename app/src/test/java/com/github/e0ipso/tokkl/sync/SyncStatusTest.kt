package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

class SyncStatusTest {

    private val unsent = MutableStateFlow(0)
    private val answered = MutableStateFlow<Instant?>(EARLIER)
    private val settings = MutableStateFlow<AccountSettings?>(TOKYO)

    @Test
    fun theStatusShowsOnceChangesHaveWaitedAndThenFollowsTheQueueAtOnce() = runBlocking {
        val seen = Channel<SyncStatus?>(Channel.UNLIMITED)
        val collecting = launch { shownSyncStatus(unsent, answered, settings, grace = GRACE).collect { seen.send(it) } }
        assertNull("nothing waits", seen.next())

        val queued = System.nanoTime()
        unsent.value = 1
        val shown = seen.next()
        val waited = (System.nanoTime() - queued) / 1_000_000
        assertEquals(SyncStatus(unsent = 1, lastAnswered = EARLIER, settings = TOKYO), shown)
        assertTrue("showed after $waited ms, before the grace", waited >= GRACE.inWholeMilliseconds)

        unsent.value = 2
        assertEquals("the count follows the queue", 2, seen.nextWithin(GRACE.inWholeMilliseconds / 2)?.unsent)
        answered.value = LATER
        assertEquals("the time follows each answer", LATER, seen.nextWithin(GRACE.inWholeMilliseconds / 2)?.lastAnswered)

        unsent.value = 0
        assertNull("the queue emptied", seen.nextWithin(GRACE.inWholeMilliseconds / 2))
        collecting.cancel()
    }

    @Test
    fun aChangeSentWithinTheGraceNeverShowsTheStatus() = runBlocking {
        val seen = Channel<SyncStatus?>(Channel.UNLIMITED)
        val collecting = launch { shownSyncStatus(unsent, answered, settings, grace = GRACE).collect { seen.send(it) } }
        assertNull(seen.next())

        unsent.value = 1
        delay(GRACE / 4)
        answered.value = LATER
        unsent.value = 0
        delay(GRACE * 2)

        val shown = seen.tryReceive()
        assertTrue("the status showed: ${shown.getOrNull()}", shown.isFailure)
        collecting.cancel()
    }

    @Test
    fun theTimeIsInTheAccountsTimezoneAndClockFormat() {
        // 23:30 on the 15th in UTC is 08:30 on the 16th in Tokyo, where it is 10:00 now.
        val answer = Instant.parse("2026-10-15T23:30:00Z")

        assertEquals(LastAnsweredLabel.At("08:30", LocalDate.of(2026, 10, 16), daysAgo = 0), lastAnsweredLabel(answer, TOKYO, NOW))
        assertEquals(
            LastAnsweredLabel.At("8:30 AM", LocalDate.of(2026, 10, 16), daysAgo = 0),
            lastAnsweredLabel(answer, TOKYO.copy(twelveHour = true), NOW),
        )
        assertEquals(
            LastAnsweredLabel.At("23:30", LocalDate.of(2026, 10, 15), daysAgo = 1),
            lastAnsweredLabel(answer, TOKYO.copy(timeZone = "UTC"), NOW),
        )
    }

    @Test
    fun anOlderDayNoAnswerAndAnUnknownZoneSayEachTheirOwn() {
        assertEquals(
            LastAnsweredLabel.At("09:15", LocalDate.of(2026, 10, 12), daysAgo = 4),
            lastAnsweredLabel(Instant.parse("2026-10-12T00:15:00Z"), TOKYO, NOW),
        )
        assertEquals(LastAnsweredLabel.Never, lastAnsweredLabel(null, TOKYO, NOW))
        assertEquals(LastAnsweredLabel.NoAccountTime, lastAnsweredLabel(EARLIER, TOKYO.copy(timeZone = "Mars/Olympus_Mons"), NOW))
        assertEquals(LastAnsweredLabel.NoAccountTime, lastAnsweredLabel(EARLIER, null, NOW))
    }

    private suspend fun Channel<SyncStatus?>.next(): SyncStatus? = withTimeout(TIMEOUT_MS) { receive() }

    private suspend fun Channel<SyncStatus?>.nextWithin(millis: Long): SyncStatus? = withTimeout(millis) { receive() }

    private companion object {
        val GRACE = 400.milliseconds
        const val TIMEOUT_MS = 5_000L
        val EARLIER: Instant = Instant.parse("2026-10-16T00:00:00Z")
        val LATER: Instant = Instant.parse("2026-10-16T00:30:00Z")
        val NOW: Clock = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneOffset.UTC)
        val TOKYO = AccountSettings(timeZone = "Asia/Tokyo", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null)
    }
}
