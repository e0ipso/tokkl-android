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
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.NetworkWatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every module's hooks in the order the app's composition root lists them, [inOrder]. Throws
 * [IllegalArgumentException] when a hook a module binds, one of [bound], is not in that list, or
 * the list names one twice, since nothing would order it.
 */
internal class AppHooks(val inOrder: List<AppHook>, bound: List<AppHook>) {
    init {
        val unordered = bound.filter { hook -> inOrder.none { it === hook } }
        require(unordered.isEmpty()) {
            "hooks missing from the startup order: ${unordered.map { it::class.simpleName }}"
        }
        require(inOrder.distinct().size == inOrder.size) { "a hook is listed twice in the startup order" }
    }
}

/** Each hook loads its component at its own step, so the steps keep their order and their threads. */
internal class AppStartup(private val hooks: AppHooks) {

    fun run() {
        hooks.inOrder.forEach { it.processStart() }
    }
}

/**
 * What MainActivity's starts and stops do, observed on that activity's lifecycle only, so the
 * Pomodoro alarm and the tile controls start nothing. Make one for each MainActivity.
 *
 * Coming to the foreground reads the account settings again (docs/PRD.md S7) and refreshes the
 * offline cache with the same `GET /api/v9/me` (O2), but a rotation's start does not. From a start
 * to the next stop the network is watched, so its return refreshes too. Nothing refreshes on a
 * timer. Every start, a rotation's too, runs each of [hooks]' [AppHook.foregroundStart], and a
 * grant of notifications their [AppHook.notificationsAllowed], in order. The screens' view models
 * follow the app's trips and the network themselves, through AppForeground.
 */
internal class MainActivityForeground(
    private val context: Context,
    private val refresh: ForegroundRefresh,
    hooks: AppHooks,
    private val http: SharedHttpClient,
    defaultNetwork: DefaultNetwork,
    private val scope: CoroutineScope,
    main: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
) : DefaultLifecycleObserver {

    private val hooks = hooks.inOrder
    private val network = NetworkWatch(defaultNetwork, scope, main, listener = ::networkChanged)
    private var notificationsAllowed = allowsNotifications()

    override fun onStart(owner: LifecycleOwner) {
        // A grant in notification settings makes the already-running timer visible too.
        val allowed = allowsNotifications()
        if (allowed && !notificationsAllowed) notificationsGranted()
        notificationsAllowed = allowed
        refresh.started(online = network.start())
        owner.lifecycleScope.launch(io) { hooks.forEach { it.foregroundStart() } }
    }

    /** Going to the background, which a configuration change is not. */
    override fun onStop(owner: LifecycleOwner) {
        val changingConfigurations = (owner as? Activity)?.isChangingConfigurations == true
        network.stop()
        refresh.stopped(changingConfigurations)
    }

    /** The user allowed notifications: the running timer and the Pomodoro session show again. */
    fun notificationsGranted() {
        notificationsAllowed = true
        scope.launch(io) { hooks.forEach { it.notificationsAllowed() } }
    }

    /** The network changed while the app is on screen: its return refreshes the account once. */
    private fun networkChanged(online: Boolean) {
        // A kept connection went with the network. A request on it would fail only after it was
        // written, so the queue could not tell whether a delete reached the server, and Undo
        // would need the server. Without one, the request fails before it leaves the phone.
        if (!online) http.dropIdleConnections()
        refresh.networkChanged(online)
    }

    private fun allowsNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
