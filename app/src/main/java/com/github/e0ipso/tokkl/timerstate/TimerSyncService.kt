package com.github.e0ipso.tokkl.timerstate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the timer event stream open while the phone is signed in to a Tokkl server with the timer
 * API, so a session or pause started on another device reaches the phone in the background, before
 * its phase ends. Android has no foreground service type for holding a server's event stream, and
 * an F-Droid app has no push service, so it is a `specialUse` service. It shows a silent,
 * low-importance notification on its own channel and holds no wake lock. The Pomodoro ringing
 * service stays a separate `mediaPlayback` service.
 *
 * [TimerSync] owns the stream. The service gives it the foreground state it needs in the
 * background, and the default network's changes. [TokklApplication.followTimerSync] starts it at app
 * open, after sign-in and from the boot and update receiver, and stops it at sign-out, when the
 * server loses the timer API and when the server refuses the credential.
 *
 * Every start promotes the service before anything else, as `startForegroundService` requires, and
 * only then checks that the stream is still wanted. A stop goes through [stopLatest], which leaves a
 * start that is still on its way to promote and decide for itself.
 */
class TimerSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as TokklApplication
    private var network: ConnectivityManager.NetworkCallback? = null
    private var latestStart = 0

    override fun onCreate() {
        super.onCreate()
        TimerSyncNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        try {
            promote()
        } catch (e: ServiceStartNotAllowedException) {
            // A restart the platform refuses in the background. The next app open starts it again.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        app.timerSyncService = this
        if (network == null) network = followDefaultNetwork()
        scope.launch {
            val running = withContext(Dispatchers.IO) {
                app.timerSyncWanted().also { wanted ->
                    if (wanted) app.timerSync.run(app.timerState) {
                        app.timerSyncService === this@TimerSyncService &&
                            app.capabilities.state.value.timerState && !app.sender.signInNeeded.value
                    }
                }
            }
            if (!running) stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (app.timerSyncService === this) app.timerSyncService = null
        network?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        network = null
        app.timerSync.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Stops the service unless a newer start has arrived. Call it on the main thread. */
    internal fun stopLatest() {
        stopSelf(latestStart)
    }

    private fun promote() {
        val notification = TimerSyncNotification.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TimerSyncNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API 31 to 33 take the manifest's type.
            startForeground(TimerSyncNotification.ID, notification)
        }
    }

    /**
     * Tells the stream when the default network comes or goes. A switch to another network, as
     * from Wi-Fi to mobile data, counts as both: the open call belongs to the old one.
     */
    private fun followDefaultNetwork(): ConnectivityManager.NetworkCallback {
        val sync = app.timerSync
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onAvailable(network: Network) {
                if (current != null && current != network) sync.networkLost()
                current = network
                sync.networkAvailable()
            }

            override fun onLost(network: Network) {
                if (network != current) return
                current = null
                sync.networkLost()
            }
        }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    companion object {
        /**
         * Starts the service in the foreground. Returns false when the platform refuses, as it does
         * for an app in the background outside the boot and update broadcasts.
         */
        fun start(context: Context): Boolean =
            try {
                context.startForegroundService(Intent(context, TimerSyncService::class.java))
                true
            } catch (e: ServiceStartNotAllowedException) {
                false
            }
    }
}

/** The timer sync service's notification: silent, low importance, on its own channel. */
internal object TimerSyncNotification {

    /** After the running timer (1), the reminder (2) and the Pomodoro session and alarm (3). */
    const val ID = 4
    const val CHANNEL_ID = "timer_sync"

    /** Low importance and no sound, so it sits in the shade without a sound, a vibration or a pop-up. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.timer_sync_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.timer_sync_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_timer)
            .setContentTitle(context.getString(R.string.timer_sync_title))
            .setContentText(context.getString(R.string.timer_sync_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp(context))
            .build()

    /** Brings the app's task to the front as the launcher does, or starts it. */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * [TimerSync]'s clock on the phone: [SystemClock.elapsedRealtime], which counts deep sleep too, so
 * a stream that went quiet while the phone slept counts as silent for all of it once the phone
 * wakes. Delays run in [scope].
 */
internal class ElapsedTimerSyncClock(private val scope: CoroutineScope) : TimerSyncClock {

    override fun now(): Long = SystemClock.elapsedRealtime()

    override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
        val job = scope.launch {
            delay(delayMillis)
            task()
        }
        return { job.cancel() }
    }
}
