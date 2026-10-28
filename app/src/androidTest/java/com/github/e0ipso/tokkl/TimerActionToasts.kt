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

    /** Runs [action] and waits until the platform holds this app's long text toast [text]. */
    fun await(@StringRes text: Int, action: () -> Unit) {
        val expected = context.getString(text)
        action()
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var seen = emptyList<String>()
        while (System.nanoTime() < deadline) {
            seen = DeviceShell.run("dumpsys notification").lineSequence().filter { it.contains("TextToastRecord{") }.toList()
            if (seen.any { it.contains(":${context.packageName}/") && it.contains(" text=$expected duration=${Toast.LENGTH_LONG}}") }) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("Expected the toast '$expected'; platform toasts: $seen")
    }

    private const val TIMEOUT_NANOS = 15_000_000_000L
    private const val POLL_MS = 20L
}
