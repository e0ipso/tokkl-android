package com.github.e0ipso.tokkl.ui.theme

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets the default display to the largest step offered by AOSP Settings, before an activity
 * starts. Uses the device's Settings fractions, its effective pixel size and its default density,
 * following SettingsLib DisplayDensityUtils. A window-size override alone is not this check.
 * Restores an existing density override, or the absence of one, even if setup or the test fails.
 */
class SystemDisplaySize : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val original = readDensity()
            val size = DeviceShell.run("wm size -d 0")
            val dimensions = Regex("(?:Physical|Override) size: (\\d+)x(\\d+)").findAll(size).lastOrNull()
                ?: error("cannot read the default display size: $size")
            val minDimension = minOf(dimensions.groupValues[1].toInt(), dimensions.groupValues[2].toInt())
            val settings = context.packageManager.getResourcesForApplication("com.android.settings")
            fun fraction(name: String): Float {
                val id = settings.getIdentifier(name, "fraction", "com.android.settings")
                check(id != 0) { "AOSP Settings has no $name; verify its display-size steps before running this audit" }
                return settings.getFraction(id, 1, 1)
            }
            val maxScale = minOf(fraction("display_density_max_scale"), (160 * minDimension / 320).toFloat() / original.physical)
            val interval = fraction("display_density_min_scale_interval")
            check(interval > 0f && maxScale >= 1f) { "unexpected AOSP display-size bounds: $maxScale / $interval" }
            // Settings has up to three larger steps. Its final step is rounded down to an even dpi.
            val largerSteps = ((maxScale - 1f) / interval).toInt().coerceIn(0, 3)
            val largest = if (largerSteps == 0) original.physical else {
                val stepInterval = (maxScale - 1f) / largerSteps
                (original.physical * (1f + largerSteps * stepInterval)).toInt() and -2
            }
            withConfigurationRestored(
                change = {
                    awaitDeviceConfiguration("display density $largest", { it.densityDpi == largest }) {
                        DeviceShell.run("wm density $largest -d 0")
                    }
                    check(readDensity().effective == largest) { "wm density did not keep the largest display size" }
                    Log.i("TokklAccessibility", "largest display density=$largest; physical=${original.physical}; size=$size")
                },
                restore = {
                    awaitDeviceConfiguration("original display density ${original.effective}", { it.densityDpi == original.effective }) {
                        DeviceShell.run("wm density ${original.overrideDpi ?: "reset"} -d 0")
                    }
                    check(readDensity() == original) { "the display density override was not restored" }
                    Log.i("TokklAccessibility", "display density restored to $original")
                },
            ) { base.evaluate() }
        }
    }

    private data class Density(val physical: Int, val overrideDpi: Int?) {
        val effective get() = overrideDpi ?: physical
    }

    private fun readDensity(): Density {
        val output = DeviceShell.run("wm density -d 0")
        val physical = Regex("Physical density: (\\d+)").find(output)?.groupValues?.get(1)?.toInt()
            ?: error("cannot read the default display density: $output")
        val overrideDpi = Regex("Override density: (\\d+)").find(output)?.groupValues?.get(1)?.toInt()
        return Density(physical, overrideDpi)
    }
}

private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Wait for Android's configuration callback, with readback before and after to avoid a race. */
internal fun awaitDeviceConfiguration(name: String, matches: (Configuration) -> Boolean, change: () -> Unit) {
    val application = context.applicationContext
    val changed = CountDownLatch(1)
    val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (matches(context.resources.configuration)) changed.countDown()
        }
        override fun onLowMemory() = Unit
    }
    application.registerComponentCallbacks(callback)
    try {
        change()
        if (!matches(context.resources.configuration)) {
            check(changed.await(10, TimeUnit.SECONDS)) { "timed out waiting for $name" }
        }
        check(matches(context.resources.configuration)) { "the app's configuration did not retain $name" }
    } finally {
        application.unregisterComponentCallbacks(callback)
    }
}

/** Restore partial setup too, retaining the primary failure when cleanup also fails. */
internal fun withConfigurationRestored(change: () -> Unit, restore: () -> Unit, body: () -> Unit) {
    var failure: Throwable? = null
    try {
        change()
        body()
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            restore()
        } catch (cleanup: Throwable) {
            if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
        }
    }
}
