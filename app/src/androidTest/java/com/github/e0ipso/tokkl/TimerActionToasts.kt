package com.github.e0ipso.tokkl

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.timerstate.TimerActionResult

/**
 * Reads the toast in which the native timer surfaces say that a server action did not go through.
 * System text toasts are not UiAutomator nodes on these AOSP images, so this reads the platform's
 * toast records from `dumpsys notification`, as FavoriteShortcutsServerTest does.
 */
object TimerActionToasts {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The offline message every timer action uses. */
    @get:StringRes
    val offline: Int get() = TimerActionResult.Offline.message

    /**
     * Runs [action] and waits until the platform holds this app's long text toast [text]. A toast
     * with that text from an earlier action is waited out first, so it cannot stand in for this
     * action's.
     */
    fun await(@StringRes text: Int, action: () -> Unit) {
        val expected = context.getString(text)
        val earlier = System.nanoTime() + TIMEOUT_NANOS
        while (shows(expected)) {
            if (System.nanoTime() > earlier) throw AssertionError("An earlier toast '$expected' did not go")
            Thread.sleep(POLL_MS)
        }
        action()
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            if (shows(expected)) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("Expected the toast '$expected'; platform toasts: ${toasts()}")
    }

    private fun shows(expected: String): Boolean =
        toasts().any { it.contains(":${context.packageName}/") && it.contains(" text=$expected duration=${Toast.LENGTH_LONG}}") }

    private fun toasts(): List<String> =
        DeviceShell.run("dumpsys notification").lineSequence().filter { it.contains("TextToastRecord{") }.toList()

    private const val TIMEOUT_NANOS = 15_000_000_000L
    private const val POLL_MS = 20L
}
