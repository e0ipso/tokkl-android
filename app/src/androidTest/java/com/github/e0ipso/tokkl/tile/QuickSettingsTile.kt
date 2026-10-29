package com.github.e0ipso.tokkl.tile

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.BlackholeDiagnostics
import com.github.e0ipso.tokkl.DeviceShell

/**
 * The app's Quick Settings tile in the device's real SystemUI, for the tests. It adds, taps and
 * removes the tile through the shell's `cmd statusbar`, as a user would in the panel, and reads
 * what the panel shows through the accessibility tree. `add-tile` puts the tile first, so it shows
 * on the panel's first page.
 */
object QuickSettingsTile {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val component get() = ComponentName(instrumentation.targetContext, RunningTileService::class.java).flattenToString()

    fun add() {
        DeviceShell.run("cmd statusbar add-tile $component")
        awaitTileSpec(present = true)
    }

    fun remove() {
        DeviceShell.run("cmd statusbar remove-tile $component")
        awaitTileSpec(present = false)
    }

    /** SystemUI applies these shell commands asynchronously, including the previous test's cleanup. */
    private fun awaitTileSpec(present: Boolean) {
        val spec = "custom($component)"
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (true) {
            val specs = tileSpecs()
            if ((spec in specs) == present) return
            if (System.nanoTime() > deadline) {
                throw AssertionError("tile setting did not reach present=$present: $specs")
            }
            Thread.sleep(POLL_MS)
        }
    }

    private fun tileSpecs(): List<String> =
        DeviceShell.run("settings get secure sysui_qs_tiles").trim().split(',')

    /** Opens the full Quick Settings panel, where the system binds the tile and it listens. */
    fun expand() {
        BlackholeDiagnostics.mark("tile.expand.enter")
        DeviceShell.run("cmd statusbar expand-settings")
        BlackholeDiagnostics.mark("tile.expand.shell.return")
    }

    fun collapse() {
        BlackholeDiagnostics.mark("tile.collapse.enter")
        DeviceShell.run("cmd statusbar collapse")
        BlackholeDiagnostics.mark("tile.collapse.shell.return")
        // The command returns before SystemUI's animation ends. An expansion sent during that
        // animation can be lost, so wait for accessibility events to settle before reopening.
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        BlackholeDiagnostics.mark("tile.collapse.idle.return")
    }

    /** Taps the tile, as SystemUI does for a touch. */
    fun click() {
        DeviceShell.run("cmd statusbar click-tile $component")
    }

    /**
     * Waits until the panel shows [text] as a whole text, such as the tile's label or its second
     * line. SystemUI sometimes drops an expansion sent soon after a collapse, and then never binds
     * the tile, so ask for the expanded panel again every few seconds. On an open panel that
     * changes nothing.
     */
    fun awaitText(what: String, text: String) {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        var nextExpand = System.nanoTime() + REEXPAND_NANOS
        while (!shows(text)) {
            if (System.nanoTime() > deadline) throw AssertionError("$what: the panel does not show \"$text\"; tiles=${tileSpecs()}")
            if (System.nanoTime() > nextExpand) {
                expand()
                nextExpand = System.nanoTime() + REEXPAND_NANOS
            }
            Thread.sleep(POLL_MS)
        }
    }

    /** A single visible-state observation for the shared tap deadline. */
    fun showsVisibleText(text: String): Boolean = shows(text, visibleOnly = true)

    private fun shows(text: String, visibleOnly: Boolean = false): Boolean {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            automation.serviceInfo = info.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
        val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
        return roots.any { root -> root.findAccessibilityNodeInfosByText(text).any { it.says(text) && (!visibleOnly || it.isVisibleToUser) } }
    }

    /** The search matches part of a text, in any case: this checks the whole of it. */
    private fun AccessibilityNodeInfo.says(text: String): Boolean =
        this.text?.toString() == text || contentDescription?.toString() == text

    private const val TIMEOUT_NANOS = 15_000_000_000L
    private const val POLL_MS = 200L
    private const val REEXPAND_NANOS = 4_000_000_000L
}
