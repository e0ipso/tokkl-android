package com.github.e0ipso.tokkl.session

/**
 * When the app reads the server again while it is in the foreground (docs/PRD.md O2): once each
 * time it comes to the foreground, and once each time the network comes back while it is there.
 * Nothing here runs on a timer, so an idle app on screen sends nothing between those events, and
 * one in the background has nothing that wakes it. The queue's own refresh after a send and the
 * background send when the network returns with the app closed live elsewhere.
 *
 * A rotation stops and starts the activity, but the app stays on screen, so that start reads
 * nothing. Call every method on the main thread.
 *
 * It lives as long as the process, so it knows across a rotation that the activity only changed
 * its configuration.
 *
 * @param refresh reads the running entry and the cached data again.
 * @param refreshOnReconnect does the same when the network comes back, and once more after a
 *   refresh that was running then ends without an answer.
 */
class ForegroundRefresh(private val refresh: () -> Unit, private val refreshOnReconnect: () -> Unit) {

    /** The activity stopped for a configuration change, so its next start is no trip back. */
    private var changingConfigurations = false

    /** Whether the phone has a network, while the app watches it in the foreground. */
    private var online: Boolean? = null

    /** The activity started, and the phone [online] or not then. */
    fun started(online: Boolean) {
        this.online = online
        if (changingConfigurations) {
            changingConfigurations = false
        } else {
            refresh()
        }
    }

    /**
     * The activity stopped. [changingConfigurations] is true for a rotation or another
     * configuration change, which is no trip to the background. The network is not watched until
     * the next start, which refreshes anyway.
     */
    fun stopped(changingConfigurations: Boolean) {
        this.changingConfigurations = changingConfigurations
        online = null
    }

    /**
     * The phone's default network changed while the app is in the foreground: [online] is whether
     * it reaches the internet now. Coming back online refreshes once and returns true, so the
     * caller can tell the screens on show to load what they could not. A change of network that
     * stays online returns false without refreshing the account. [onOnline] still hears each
     * online update while started, so a failed page can recover when a second network becomes
     * usable. A report while stopped does nothing.
     */
    fun networkChanged(online: Boolean, onOnline: () -> Unit = {}): Boolean {
        val before = this.online ?: return false
        this.online = online
        val back = online && !before
        if (back) refreshOnReconnect()
        if (online) onOnline()
        return back
    }
}
