package com.github.e0ipso.tokkl.navhost

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.widget.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The taps from outside the app that wait for the navigation host. */
class NavigationRequestsTest {
    @Test
    fun aLaterTapOfTheSameKindReplacesTheWaitingOne() {
        val requests = NavigationRequests(SavedStateHandle())
        requests.request(NavigationRequest.Widget(WidgetPage.TIMER))
        requests.request(NavigationRequest.ReminderTimer)
        requests.request(NavigationRequest.Widget(WidgetPage.TODAY))
        assertEquals(
            listOf(NavigationRequest.ReminderTimer, NavigationRequest.Widget(WidgetPage.TODAY)),
            requests.pending,
        )
        requests.handled(NavigationRequest.Widget(WidgetPage.TIMER))
        assertEquals("a replaced request's answer leaves the new one", 2, requests.pending.size)
        requests.handled(NavigationRequest.ReminderTimer)
        assertEquals(listOf<NavigationRequest>(NavigationRequest.Widget(WidgetPage.TODAY)), requests.pending)
    }
}
