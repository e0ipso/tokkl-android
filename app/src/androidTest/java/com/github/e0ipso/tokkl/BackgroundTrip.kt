package com.github.e0ipso.tokkl

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertSame

/**
 * Sends MainActivity to the background with the home key and brings the same activity back, as a
 * user leaving the app and returning does. The waits poll, since the compose rule's waits need an
 * activity in the foreground.
 */
object BackgroundTrip {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Presses the home key and waits until the activity has stopped. Returns that activity. */
    fun leave(): MainActivity {
        val activity = checkNotNull(resumedActivity()) { "no MainActivity in the foreground" }
        DeviceShell.run("input keyevent KEYCODE_HOME")
        await("the app is in the background") { activity.takeIf { inStage(it, Stage.STOPPED) } }
        return activity
    }

    /** Brings [activity]'s task to the front as it was, and checks that the same activity came back. */
    fun comeBack(activity: MainActivity) {
        // ActivityScenario matches lifecycle events against the original launch intent. Keep
        // its action and categories too, or it ignores RESUMED and close() fails during teardown.
        // Replace launch-only flags such as CLEAR_TASK so this returns the existing activity.
        val intent = Intent(activity.intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        // UiAutomation passes arguments directly; the URI already escapes its own values.
        val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
        val result = DeviceShell.run("am start -W $uri")
        check(!result.contains("Error:")) { "bringing the app back failed: $result" }
        val back = await("the app is in the foreground") { resumedActivity() }
        assertSame("the same activity came back, not a new one", activity, back)
    }

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    private fun inStage(activity: MainActivity, stage: Stage): Boolean {
        var found = false
        instrumentation.runOnMainSync { found = activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage) }
        return found
    }

    private fun <T : Any> await(what: String, value: () -> T?): T {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (true) {
            value()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private const val TIMEOUT_NANOS = 30_000_000_000L
    private const val POLL_MS = 50L
}
