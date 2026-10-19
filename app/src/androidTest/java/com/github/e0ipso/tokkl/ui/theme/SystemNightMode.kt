package com.github.e0ipso.tokkl.ui.theme

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import org.junit.rules.ExternalResource

/**
 * Sets the system's night mode to light or dark, waits until the app's configuration has it, and
 * puts the previous mode back afterwards, a schedule included. A test that depends on the
 * system's mode uses this instead of skipping on a device whose mode follows a schedule, because
 * the Android Gradle plugin reports a failed assumption as a failed test. Order it after the rule
 * that hands the app's night mode back to the system, so the app follows the mode set here, and
 * before the rule that starts the activity.
 */
class SystemNightMode(val dark: Boolean) : ExternalResource() {
    private var previous: String? = null

    override fun before() {
        val current = nightMode(DeviceShell.run("cmd uimode night"))
        check(current != UNKNOWN) { "the system's night mode is $current, which cannot be put back afterwards" }
        previous = current
        set(if (dark) YES else NO)
        awaitAppNightMode()
    }

    override fun after() {
        previous?.let(::set)
    }

    /** `cmd uimode night <mode>` prints the mode it ends in, in the words it takes. */
    private fun set(mode: String) {
        val now = nightMode(DeviceShell.run("cmd uimode night $mode"))
        check(now == mode) { "the system's night mode is $now after setting it to $mode" }
    }

    private fun awaitAppNightMode() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val expected = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val deadline = System.nanoTime() + CONFIGURATION_TIMEOUT_NS
        while ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != expected) {
            check(System.nanoTime() < deadline) { "the app's configuration is not in night mode $dark" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val YES = "yes"
        const val NO = "no"

        /** What `cmd uimode night` prints for a mode it has no word for. */
        const val UNKNOWN = "unknown"
        const val CONFIGURATION_TIMEOUT_NS = 10_000_000_000L
        const val POLL_MS = 50L

        /**
         * The mode in `cmd uimode night`'s "Night mode: <mode>" line. UiModeManagerService's shell
         * command prints yes, no or auto, custom on Android 11 and 12, and custom_schedule or
         * custom_bedtime from Android 13 on.
         */
        fun nightMode(output: String): String = output.trim().substringAfter("Night mode: ")
    }
}
