package com.github.e0ipso.tokkl.ui.theme

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import org.junit.rules.ExternalResource

/**
 * Sets the device's font scale, waits until the app's configuration has it, and puts the
 * previous setting back afterwards, again waiting for the app to see it so the next test starts
 * at the usual scale. Order it before the rule that starts the activity, so the activity never
 * has to restart.
 */
class SystemFontScale(private val scale: Float) : ExternalResource() {
    private var previous: String = NOT_SET

    override fun before() {
        previous = DeviceShell.run("settings get system font_scale").trim()
        DeviceShell.run("settings put system font_scale $scale")
        awaitAppFontScale(scale)
    }

    override fun after() {
        if (previous == NOT_SET) {
            DeviceShell.run("settings delete system font_scale")
        } else {
            DeviceShell.run("settings put system font_scale $previous")
        }
        awaitAppFontScale(previous.toFloatOrNull() ?: 1f)
    }

    private fun awaitAppFontScale(expected: Float) {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val deadline = System.nanoTime() + CONFIGURATION_TIMEOUT_NS
        while (resources.configuration.fontScale != expected) {
            check(System.nanoTime() < deadline) {
                "the app's font scale is ${resources.configuration.fontScale}, not $expected"
            }
            Thread.sleep(POLL_MS)
        }
    }

    companion object {
        /** The largest step of Android's font size setting since Android 14. */
        const val LARGEST = 2f

        /** What `settings get` prints for a setting that was never written. */
        private const val NOT_SET = "null"
        private const val CONFIGURATION_TIMEOUT_NS = 10_000_000_000L
        private const val POLL_MS = 50L
    }
}
