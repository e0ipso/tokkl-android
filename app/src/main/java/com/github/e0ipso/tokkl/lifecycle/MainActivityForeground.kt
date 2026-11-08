package com.github.e0ipso.tokkl.lifecycle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.session.AppEvent
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.NetworkWatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What MainActivity's starts and stops do, observed on that activity's lifecycle only, so the
 * Pomodoro alarm and the tile controls start nothing. Make one for each MainActivity.
 *
 * Coming to the foreground reads the account settings again (docs/PRD.md S7) and refreshes the
 * offline cache with the same `GET /api/v9/me` (O2), but a rotation's start does not. From a start
 * to the next stop the network is watched, so its return refreshes too. Nothing refreshes on a
 * timer. Every start, a rotation's too, runs each of [hooks]' [AppHook.foregroundStart], and a
 * grant of notifications their [AppHook.notificationsAllowed], in order. [events] tells the
 * screens' view models about the starts, the trips to the background and the network.
 */
internal class MainActivityForeground(
    private val context: Context,
    private val refresh: ForegroundRefresh,
    private val events: AppEvents,
    hooks: List<AppHook>,
    private val http: SharedHttpClient,
    defaultNetwork: DefaultNetwork,
    private val scope: CoroutineScope,
    main: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
) : DefaultLifecycleObserver {

    private val hooks = hooks.sortedBy { it.order }
    private val network = NetworkWatch(defaultNetwork, scope, main, listener = ::networkChanged)
    private var notificationsAllowed = allowsNotifications()

    override fun onStart(owner: LifecycleOwner) {
        // A grant in notification settings makes the already-running timer visible too.
        val allowed = allowsNotifications()
        if (allowed && !notificationsAllowed) notificationsGranted()
        notificationsAllowed = allowed
        refresh.started(online = network.start())
        events.send(AppEvent.CAME_TO_FOREGROUND)
        owner.lifecycleScope.launch(io) { hooks.forEach { it.foregroundStart() } }
    }

    /** Going to the background, which a configuration change is not. */
    override fun onStop(owner: LifecycleOwner) {
        val changingConfigurations = (owner as? Activity)?.isChangingConfigurations == true
        network.stop()
        refresh.stopped(changingConfigurations)
        if (!changingConfigurations) events.send(AppEvent.WENT_TO_BACKGROUND)
    }

    /** The user allowed notifications: the running timer and the Pomodoro session show again. */
    fun notificationsGranted() {
        notificationsAllowed = true
        scope.launch(io) { hooks.forEach { it.notificationsAllowed() } }
    }

    /**
     * The network came back while the app is on screen: the account refreshes once, and the
     * history's older days, the calendar and the reports load again what they could not load
     * offline. The projects, clients and tags pages watch the network themselves.
     */
    private fun networkChanged(online: Boolean) {
        // A kept connection went with the network. A request on it would fail only after it was
        // written, so the queue could not tell whether a delete reached the server, and Undo
        // would need the server. Without one, the request fails before it leaves the phone.
        if (!online) http.dropIdleConnections()
        val returned = refresh.networkChanged(online) {
            // A cellular route can appear before it works, followed by Wi-Fi without an
            // offline interval. Retry failed history days and calendar weeks on each online
            // update, while keeping the account refresh and other foreground work debounced.
            events.send(AppEvent.ONLINE)
        }
        if (returned) events.send(AppEvent.RECONNECTED)
    }

    private fun allowsNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
