package com.github.e0ipso.tokkl.notification

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import org.junit.Assert.assertNull

/** Reads the running timer's notification as the system holds it, for the tests. */
object RunningNotifications {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /**
     * On Android 13 and later, allows the app's notifications, as the user would when TKA-0058
     * asks. The tests never take it back: revoking a runtime permission kills the app's process,
     * which is the test's own.
     */
    fun allow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The notification on show, or null. */
    fun shown(): StatusBarNotification? = manager.activeNotifications.firstOrNull { it.id == RunningNotifier.NOTIFICATION_ID }

    /** The notification once it is on show and matches [done]. It is posted off the main thread. */
    fun awaitShown(what: String, done: (StatusBarNotification) -> Boolean = { true }): StatusBarNotification {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (true) {
            shown()?.takeIf(done)?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("$what: shown ${shown()?.notification?.extras}")
            Thread.sleep(POLL_MS)
        }
    }

    fun awaitGone(what: String) {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (shown() != null) {
            if (System.nanoTime() > deadline) throw AssertionError("$what: still shown")
            Thread.sleep(POLL_MS)
        }
    }

    /** Fails if the notification shows at any time in the next [millis]. */
    fun assertStaysGone(what: String, millis: Long = STAYS_GONE_MS) {
        val deadline = System.nanoTime() + millis * 1_000_000
        while (System.nanoTime() < deadline) {
            assertNull(what, shown())
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * Takes the notification away as a swipe does on Android 14 and later. The app hears of
     * neither, since it sets no delete intent, so to the app the two are the same.
     */
    fun dismiss() {
        manager.cancel(RunningNotifier.NOTIFICATION_ID)
        awaitGone("the dismissed notification")
    }

    /** The app's services that run in the foreground. */
    @Suppress("DEPRECATION") // Still returns the caller's own services.
    fun foregroundServices(): List<String> =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .filter { it.service.packageName == context.packageName && it.foreground }
            .map { it.service.className }

    fun channelName(): CharSequence? = manager.getNotificationChannel(RunningNotifier.CHANNEL_ID)?.name

    fun channelDescription(): String? = manager.getNotificationChannel(RunningNotifier.CHANNEL_ID)?.description

    private const val TIMEOUT_NANOS = 10_000_000_000L
    private const val POLL_MS = 100L

    /** Longer than the app takes to post after a change, which is a few database reads. */
    const val STAYS_GONE_MS = 3_000L
}

/** The notification's action titled [title]: Pause or Resume, and Stop. */
fun Notification.action(@StringRes title: Int): Notification.Action {
    val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(title)
    return actions.single { it.title.toString() == label }
}

/** The notification's Stop action, which sits beside Pause or Resume. */
fun Notification.stopAction(): Notification.Action = action(R.string.notification_stop)
