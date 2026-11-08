package com.github.e0ipso.tokkl.navhost

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.requestSerializers
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionTap
import com.github.e0ipso.tokkl.pomodoro.PomodoroWidgetTap
import com.github.e0ipso.tokkl.reports.TodayReportTap
import com.github.e0ipso.tokkl.timer.ReminderTap
import com.github.e0ipso.tokkl.timer.TimerWidgetTap
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The taps from outside the app that wait for the navigation host. */
class NavigationRequestsTest {
    private val serializers = requestSerializers(appFeatureEntries)

    @Test
    fun aLaterTapOfTheSameKindReplacesTheWaitingOne() {
        val requests = NavigationRequests(SavedStateHandle(), serializers)
        requests.request(PomodoroSessionTap("account-a", 1))
        requests.request(ReminderTap)
        requests.request(PomodoroSessionTap("account-a", 2))
        assertEquals(listOf(ReminderTap, PomodoroSessionTap("account-a", 2)), requests.pending)
        requests.handled(PomodoroSessionTap("account-a", 1))
        assertEquals("a replaced request's answer leaves the new one", 2, requests.pending.size)
        requests.handled(ReminderTap)
        assertEquals(listOf<NavigationRequest>(PomodoroSessionTap("account-a", 2)), requests.pending)
    }

    /** Every feature registers the requests it reads, so a waiting one outlives the process. */
    @Test
    fun everyRequestComesBackFromTheSavedRequests() {
        val waiting = listOf(
            TimerWidgetTap,
            ReminderTap,
            TodayReportTap,
            PomodoroWidgetTap,
            PomodoroSessionTap("account-a", 1_700_000_000_000L),
        )
        val json = Json { serializersModule = serializers }
        val serializer = ListSerializer(PolymorphicSerializer(NavigationRequest::class))

        assertEquals(waiting, json.decodeFromString(serializer, json.encodeToString(serializer, waiting)))
    }
}
