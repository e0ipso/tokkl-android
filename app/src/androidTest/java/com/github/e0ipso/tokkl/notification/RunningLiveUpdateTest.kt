package com.github.e0ipso.tokkl.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Promotion adds to the running notification; declining it retains the clock and Stop (N2). */
class RunningLiveUpdateTest {
    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = RunningNotifier(context)
    private val content = RunningNotificationContent(
        entryId = -1,
        description = "A Live Update",
        project = "Tokkl",
        task = "Notifications",
        start = Instant.now().minusSeconds(65),
        key = "live-update-test",
        pausable = true,
    )

    @Before
    fun allowNotifications() {
        RunningNotifications.allow()
        manager.createNotificationChannel(
            NotificationChannel(RunningNotifier.CHANNEL_ID, context.getString(R.string.notification_channel_running), NotificationManager.IMPORTANCE_LOW),
        )
    }

    @After
    fun removeTheFixture() {
        notifier.remove()
        RunningNotifications.awaitGone("the test notification was removed")
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    fun theRequestMeetsEveryPromotionRequirement() {
        // Exercise the request even on an Android 16 release that declines Live Updates.
        val notification = notifier.build(content, requestPromotion = true)
        assertTrue(notification.extras.getBoolean(RunningNotifier.REQUEST_PROMOTED_ONGOING))
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            assertTrue("current platform promotion characteristics", notification.hasPromotableCharacteristics())
        } else {
            // Android 16.0's implementation requires colorization for a stopwatch. N2 forbids
            // that, so this release must retain the ordinary notification instead.
            assertFalse("Android 16.0 declines a non-colorized stopwatch", notification.hasPromotableCharacteristics())
        }
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(content.description, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertNull(notification.headsUpContentView)
        assertNull("standard style", notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertFalse("not a group summary", notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertFalse("never colorized", notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(TokklLightExtraColors.primaryFill.toArgb(), notification.color)
        assertEquals(R.drawable.ic_nav_timer, notification.smallIcon.resId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(RunningNotifier.CHANNEL_ID).importance)
        assertRegularContent(notification)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    fun thePostedRequestChecksTheSystemsPermissionAndKeepsTheNotificationWhenDeclined() {
        val allowed = manager.canPostPromotedNotifications()
        notifier.post(content)
        val notification = RunningNotifications.awaitShown("the Live Update request or its regular fallback").notification
        assertEquals(allowed, notification.extras.getBoolean(RunningNotifier.REQUEST_PROMOTED_ONGOING))
        assertRegularContent(notification)
        assertFalse("never colorized", notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        val promoted = notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
        println("Live Update on API ${Build.VERSION.SDK_INT}: allowed=$allowed, requested=${notification.extras.getBoolean(RunningNotifier.REQUEST_PROMOTED_ONGOING)}, promoted=$promoted")
        if (promoted) assertTrue("only the system promotes it when allowed", allowed)
    }

    @Test
    fun aDeclinedRequestStillPostsTheRegularNotificationWithItsClockAndStop() {
        manager.notify(RunningNotifier.NOTIFICATION_ID, notifier.build(content, requestPromotion = false))
        val notification = RunningNotifications.awaitShown("the regular fallback").notification
        assertFalse(notification.extras.getBoolean(RunningNotifier.REQUEST_PROMOTED_ONGOING))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals("the fallback's accent is unchanged", Notification.COLOR_DEFAULT, notification.color)
        assertRegularContent(notification)
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun belowAndroid16NoPromotionRequestOrColorIsAdded() {
        val notification = notifier.build(content, requestPromotion = true)
        assertFalse(notification.extras.containsKey(RunningNotifier.REQUEST_PROMOTED_ONGOING))
        assertEquals(Notification.COLOR_DEFAULT, notification.color)
        assertRegularContent(notification)
    }

    @Test
    fun aPausedTimerIsQuietDismissibleAndAsksForNoPromotion() {
        val paused = content.copy(entryId = 0, accumulatedSeconds = 3_900, paused = true, key = "paused-test")
        val notification = notifier.build(paused, requestPromotion = true)

        assertFalse("it can be swiped away", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.containsKey(RunningNotifier.REQUEST_PROMOTED_ONGOING))
        assertFalse("no clock ticks", notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals(
            context.getString(R.string.notification_paused_project, formatDuration(3_900), context.getString(R.string.notification_project_task, "Tokkl", "Notifications")),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            listOf(context.getString(R.string.notification_resume), context.getString(R.string.notification_stop)),
            notification.actions.map { it.title.toString() },
        )
        assertTrue(notification.actions.all { it.actionIntent.isBroadcast })
    }

    @Test
    fun aResumedTimersClockCountsOnFromTheChainsStoredTime() {
        val resumed = content.copy(accumulatedSeconds = 600, key = "resumed-test")
        val notification = notifier.build(resumed, requestPromotion = false)

        assertEquals(resumed.start.toEpochMilli() - 600_000, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun aTimerWithoutPauseOffersStopAlone() {
        // Without the server's timer API, or during a Pomodoro session.
        val notification = notifier.build(content.copy(pausable = false, key = "no-pause-test"), requestPromotion = false)

        assertEquals(listOf(context.getString(R.string.notification_stop)), notification.actions.map { it.title.toString() })
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    private fun assertRegularContent(notification: Notification) {
        assertEquals(content.description, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(context.getString(R.string.notification_project_task, "Tokkl", "Notifications"), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(content.start.toEpochMilli(), notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.contentIntent.isActivity)
        assertEquals(
            listOf(context.getString(R.string.notification_pause), context.getString(R.string.notification_stop)),
            notification.actions.map { it.title.toString() },
        )
        assertTrue(notification.actions.all { it.actionIntent.isBroadcast })
    }
}
