package com.github.e0ipso.tokkl.ui.theme

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets the device's font scale before the activity starts and restores its exact previous
 * setting afterwards. Audit tests can also request AOSP Settings' largest display size.
 * Both settings are restored on partial setup failure, as well as test failure.
 */
class SystemFontScale(
    private val scale: Float,
    private val largestDisplaySize: Boolean = false,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        val font = object : Statement() {
            override fun evaluate() {
                require(scale.isFinite() && scale > 0f)
                val previous = DeviceShell.run("settings get system font_scale").trim()
                check(previous == NOT_SET || previous.toFloatOrNull()?.let { it.isFinite() && it > 0f } == true) {
                    "cannot safely restore the font-scale setting: $previous"
                }
                val previousScale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale
                withConfigurationRestored(
                    change = {
                        awaitDeviceConfiguration("font scale $scale", { it.fontScale == scale }) {
                            DeviceShell.run("settings put system font_scale $scale")
                        }
                        check(DeviceShell.run("settings get system font_scale").trim().toFloatOrNull() == scale)
                        Log.i("TokklAccessibility", "font scale=$scale; largest display=$largestDisplaySize")
                    },
                    restore = {
                        awaitDeviceConfiguration("original font scale $previousScale", { it.fontScale == previousScale }) {
                            if (previous == NOT_SET) DeviceShell.run("settings delete system font_scale")
                            else DeviceShell.run("settings put system font_scale $previous")
                        }
                        check(DeviceShell.run("settings get system font_scale").trim() == previous) {
                            "the font-scale setting was not restored"
                        }
                        Log.i("TokklAccessibility", "font scale restored to $previous")
                    },
                ) { base.evaluate() }
            }
        }
        return if (largestDisplaySize) SystemDisplaySize().apply(font, description) else font
    }

    companion object {
        /** The largest step of Android's font size setting since Android 14. */
        const val LARGEST = 2f
        private const val NOT_SET = "null"
    }
}
