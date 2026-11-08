package com.github.e0ipso.tokkl.catalog

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.github.e0ipso.tokkl.session.AppEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * What a page that shows what it loads from the server tells its model: the catalog pages, the
 * trash and the reports. [open] and [close] come from the page's route, through [PageVisitEffect].
 * The trips to the background and back come from MainActivity's lifecycle, as AppEvents carries
 * them ([followTrips]). [reconnected] comes from the network, as each model follows it, such as
 * with [followNetwork].
 */
interface PageEvents {
    /** The page opened. */
    fun open()

    /** The page went, not for a configuration change. */
    fun close()

    /** The network came back. */
    fun reconnected()

    /** The app went to the background with the page open, not for a configuration change. */
    fun wentToBackground()

    /** The app came back to the foreground with the page open. */
    fun cameToForeground()
}

/**
 * When such a page loads: [open] loads once when it opens, so a rotation loads nothing, and after
 * a trip to the background with the page open, coming back to the foreground [reload]s, since
 * what the page shows may have changed on the server meanwhile. Call every function on the main
 * thread.
 *
 * @param open loads the page as it opens.
 * @param reload loads the page again after a trip to the background, or after a failure.
 */
class PageReloads(private val open: () -> Unit, private val reload: () -> Unit = open) {

    /** Whether the page is open. */
    var isOpen: Boolean = false
        private set

    /** The app went to the background with the page open, so what it shows may be old. */
    private var backgrounded = false

    /** The page opened: loads, unless it is open already, as after a rotation. */
    fun open() {
        if (isOpen) return
        isOpen = true
        open.invoke()
    }

    /** The page opens with its own load on the way, as a tap on a widget's day opens the reports. */
    fun openLoading() {
        isOpen = true
        backgrounded = false
    }

    /** The page went: nothing loads until it opens again. */
    fun close() {
        isOpen = false
        backgrounded = false
    }

    /** Loads again while the page is open, as after a failure. */
    fun retry() {
        if (isOpen) reload()
    }

    /** The app went to the background with the page open, not for a configuration change. */
    fun wentToBackground() {
        if (isOpen) backgrounded = true
    }

    /** The app came back to the foreground. After [wentToBackground], loads again. */
    fun cameToForeground() {
        if (!backgrounded) return
        backgrounded = false
        reload()
    }
}

/**
 * Calls [PageEvents.wentToBackground] and [PageEvents.cameToForeground] for each of MainActivity's
 * trips to the background and back in [events], AppEvents' events, while [scope] runs. A
 * configuration change is no trip: it sends a start but no stop, and a page ignores a start that
 * follows no stop.
 */
fun PageEvents.followTrips(events: Flow<AppEvent>, scope: CoroutineScope): Job = scope.launch {
    events.collect { event ->
        when (event) {
            AppEvent.WENT_TO_BACKGROUND -> wentToBackground()
            AppEvent.CAME_TO_FOREGROUND -> cameToForeground()
            AppEvent.ONLINE, AppEvent.RECONNECTED -> Unit
        }
    }
}

/**
 * Calls [PageEvents.reconnected] each time [offline] says the phone is online, its current value
 * first, while [scope] runs. A model that is not open ignores it.
 */
fun PageEvents.followNetwork(offline: Flow<Boolean>, scope: CoroutineScope): Job = scope.launch {
    offline.collect { isOffline -> if (!isOffline) reconnected() }
}

/**
 * Tells a page's model when the page is on screen: [onOpen] when it enters the composition, and
 * [onClose] when it leaves it, but not for a configuration change, so turning the phone sends no
 * request and keeps what the page shows.
 */
@Composable
fun PageVisitEffect(onOpen: () -> Unit, onClose: () -> Unit) {
    val activity = LocalActivity.current
    val opened by rememberUpdatedState(onOpen)
    val closed by rememberUpdatedState(onClose)
    DisposableEffect(Unit) {
        opened()
        onDispose { if (activity?.isChangingConfigurations != true) closed() }
    }
}
