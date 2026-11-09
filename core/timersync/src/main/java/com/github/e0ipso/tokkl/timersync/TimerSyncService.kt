package com.github.e0ipso.tokkl.timersync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.github.e0ipso.tokkl.core.timersync.R
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.NetworkReport
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * Keeps the timer event stream open while the phone is signed in to a Tokkl server with the timer
 * API, so a session or pause started on another device reaches the phone in the background, before
 * its phase ends. Android has no foreground service type for holding a server's event stream, and
 * an F-Droid app has no push service, so it is a `specialUse` service. It shows a silent,
 * low-importance notification on its own channel and holds no wake lock. The Pomodoro ringing
 * service stays a separate `mediaPlayback` service.
 *
 * [TimerSync] owns the stream. The service gives it the foreground state it needs in the
 * background, and the default network's changes. [TimerSyncFollower.follow] starts it at app
 * open, after sign-in and from the boot and update receiver, and stops it at sign-out, when the
 * server loses the timer API and when the server refuses the credential.
 *
 * Every start promotes the service before anything else, as `startForegroundService` requires, and
 * only then checks that the stream is still wanted. While it runs in the foreground, the follower
 * holds its number ([TimerSyncFollower.runningService]), and each stop request the follower sends
 * stops it unless a newer start has arrived, which then promotes and decides for itself.
 */
class TimerSyncService : Service() {

    /** Cancelled in [onDestroy]. */
    private val main: CoroutineDispatcher by inject(MainDispatcher)
    private val scope by lazy { CoroutineScope(SupervisorJob() + main) }
    private val follower: TimerSyncFollower by inject()
    private val timerSync: TimerSync<TimerStateSource> by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)
    private val defaultNetwork: DefaultNetwork by inject()
    private var network: Job? = null
    private var latestStart = 0

    /** This instance's number once it runs in the foreground, which the follower holds. */
    private var instance: Int? = null

    override fun onCreate() {
        super.onCreate()
        TimerSyncNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        try {
            promote()
        } catch (_: ServiceStartNotAllowedException) {
            // A restart the platform refuses in the background. The next app open starts it again.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val instance = instance ?: follower.serviceRuns().also {
            instance = it
            followStopRequests()
        }
        if (network == null) network = followDefaultNetwork()
        scope.launch {
            val running = withContext(io) {
                follower.wanted().also { wanted ->
                    if (wanted) follower.run(instance)
                }
            }
            if (!running) stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance?.let(follower::serviceEnded)
        network?.cancel()
        network = null
        timerSync.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Stops the service at each of the follower's stop requests, unless a newer start has arrived.
     * The scope runs on the main thread at once, so it collects before this returns.
     */
    private fun followStopRequests() {
        scope.launch {
            follower.stopRequests.collect { stopSelf(latestStart) }
        }
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
    private fun followDefaultNetwork(): Job {
        val sync = timerSync
        var current: Long? = null
        // The scope runs on the main thread at once, so the callback is registered before this returns.
        return scope.launch {
            defaultNetwork.reports.collect { report ->
                when (report) {
                    is NetworkReport.Available -> {
                        if (current != null && current != report.id) sync.networkLost()
                        current = report.id
                        sync.networkAvailable()
                    }

                    is NetworkReport.Lost -> if (report.id == current) {
                        current = null
                        sync.networkLost()
                    }

                    is NetworkReport.Capabilities -> Unit
                }
            }
        }
    }

    companion object {
        /**
         * Starts the service in the foreground. Returns false when the platform refuses, as it does
         * for an app in the background outside the boot and update broadcasts.
         */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, TimerSyncService::class.java))
            true
        } catch (_: ServiceStartNotAllowedException) {
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_sync_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.timer_sync_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context): Notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(UiR.drawable.ic_nav_timer)
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
        val intent = mainActivityIntent(context)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
