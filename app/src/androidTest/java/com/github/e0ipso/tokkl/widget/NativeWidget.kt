package com.github.e0ipso.tokkl.widget

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue

/** A real platform widget host. Glance renders RemoteViews and its real PendingIntents handle taps. */
class NativeWidget : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val host = object : AppWidgetHost(context, 6000) {
        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
            object : AppWidgetHostView(context) {
                override fun updateAppWidget(views: RemoteViews?) {
                    updates.incrementAndGet()
                    super.updateAppWidget(views)
                }
            }

        // Android 12 calls this when the provider's package changes. The view goes back to
        // the provider's initial layout until Glance draws it again.
        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo) {
            resets.incrementAndGet()
            super.onProviderChanged(appWidgetId, appWidget)
        }
    }
    /** Provider resets the host has applied, on the main thread like [updates]. */
    val resets = AtomicInteger()
    val updates = AtomicInteger()
    val provider: AppWidgetProviderInfo
    private val id: Int
    private val scenario: ActivityScenario<ComponentActivity>
    private lateinit var view: AppWidgetHostView

    init {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            id = host.allocateAppWidgetId()
            val component = ComponentName(context, TokklWidgetReceiver::class.java)
            assertTrue("the installed provider can be bound", manager.bindAppWidgetIdIfAllowed(id, component, options(360, 320)))
            provider = checkNotNull(manager.getAppWidgetInfo(id))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            host.startListening()
            view = host.createView(activity, id, provider)
            activity.setContentView(FrameLayout(activity).apply { addView(view) })
        }
        resize(360, 320)
    }

    /** Explicit ATF check for the RemoteViews host; performClick alone does not invoke Espresso. */
    fun checkAccessibility() {
        onMain { AccessibilityChecks.enable().setRunChecksFromRootView(true).checkAndReturnResults(view) }
    }

    fun resize(width: Int, height: Int) {
        scenario.onActivity {
            view.layoutParams = FrameLayout.LayoutParams(dp(width), dp(height))
            manager.updateAppWidgetOptions(id, options(width, height))
        }
    }

    fun awaitText(text: String, timeout: Long = 15_000) = await("widget text $text", timeout) {
        descendants().any { it is TextView && it.text.toString() == text }
    }

    fun awaitDescription(label: String, timeout: Long = 15_000) = await("widget control $label", timeout) {
        descendants().any { it.contentDescription?.toString() == label }
    }

    fun hasDescription(label: String): Boolean = onMain { descendants().any { it.contentDescription?.toString() == label } }

    /** Reads the rendered control without starting another timeout after a measured tap. */
    fun hasVisibleDescription(label: String): Boolean = onMain {
        descendants().any {
            it.contentDescription?.toString() == label && it.isShown &&
                it.getGlobalVisibleRect(android.graphics.Rect())
        }
    }

    fun hasText(text: String): Boolean = onMain { descendants().any { it is TextView && it.text.toString() == text } }

    fun click(label: String) {
        // RemoteViews may replace the control between two main-thread tasks. Find and click it
        // in the same task, waiting through an update without ever tapping a stale view.
        await("widget control $label") {
            val target = descendants().firstOrNull { it.contentDescription?.toString() == label }
                ?: return@await false
            val button = generateSequence(target) { it.parent as? View }.first { it.isClickable }
            assertTrue("the native widget control is enabled", button.isEnabled)
            assertTrue("the native widget control handles the tap", button.performClick())
            true
        }
    }

    /** Text must fit vertically even when Android enlarges the font. Ellipsis may shorten names. */
    fun assertTextFitsVertically() = await("the widget text is laid out") {
        val texts = descendants().filterIsInstance<TextView>().filter { it.text.isNotEmpty() }
        // RemoteViews can install text before Android's next measure/layout pass. Check
        // readiness and geometry in the same main-thread task so no update can replace it.
        if (texts.isEmpty() || texts.any { !it.isLaidOut || it.isLayoutRequested || it.layout == null }) return@await false
        texts.forEach { text ->
            val visible = android.graphics.Rect()
            assertTrue("${text.text} is visible", text.getGlobalVisibleRect(visible))
            assertTrue("${text.text} is not clipped by its row: ${visible.height()} / ${text.height}", visible.height() >= text.height - 1)
            assertTrue("${text.text} fits its TextView: layout ${text.layout?.height}, view ${text.height}, padding ${text.compoundPaddingTop + text.compoundPaddingBottom}", checkNotNull(text.layout).height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            if (text is Chronometer) {
                assertTrue("the elapsed clock is fully visible", visible.width() >= text.width - 1)
                assertTrue("the elapsed clock never wraps", text.layout.lineCount == 1)
                assertTrue("the elapsed clock fits horizontally", text.layout.getLineWidth(0) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
            }
        }
        true
    }

    /**
     * Observe two native Chronometer ticks, with no widget updates in between. Android resets
     * every bound view of a provider whenever a component of its package is enabled or
     * disabled, which replaces the clock. A test's fresh WorkManager does that to its
     * RescheduleReceiver about a second after Glance's first enqueue. A window such a reset
     * cut short starts again on the redrawn clock; a clock that stops without one fails.
     */
    fun assertClockTicksWithoutUpdates() {
        var window = observeClock()
        var restarts = 0
        while (!window.ticked && window.resets > 0 && restarts++ < MAX_RESET_RESTARTS) {
            android.util.Log.i("NativeWidget", "a provider reset ended the clock window, observing again: $window")
            await("the clock is redrawn after a provider reset") { descendants().any { it is Chronometer } }
            awaitUpdatesSettled()
            window = observeClock()
        }
        assertTrue("the launcher ticks the clock: $window", window.ticked)
        assertTrue("elapsed time changed", window.first != window.last)
        org.junit.Assert.assertEquals("no RemoteViews update was needed to tick", window.before, window.after)
    }

    private data class ClockWindow(
        val ticked: Boolean,
        val ticks: Int,
        val first: String?,
        val last: String?,
        val before: Int,
        val after: Int,
        val resets: Int,
        val attached: Boolean,
    )

    private fun observeClock(): ClockWindow {
        val done = CountDownLatch(1)
        var ticks = 0
        var first: String? = null
        var last: String? = null
        var before = -1
        var after = -1
        var resetsBefore = -1
        val clock = onMain {
            val observed = descendants().filterIsInstance<Chronometer>().single()
            // Counter reads and ticks share the main thread. An update before the listener
            // is installed, or after its last tick, is outside the interval under test.
            before = updates.get()
            resetsBefore = resets.get()
            first = observed.text.toString()
            observed.setOnChronometerTickListener { ticking ->
                if (++ticks == 2) {
                    last = ticking.text.toString()
                    after = updates.get()
                    ticking.onChronometerTickListener = null
                    done.countDown()
                }
            }
            observed
        }
        val ticked = done.await(4, TimeUnit.SECONDS)
        return onMain {
            clock.onChronometerTickListener = null
            ClockWindow(ticked, ticks, first, last, before, if (ticked) after else updates.get(), resets.get() - resetsBefore, clock.isAttachedToWindow)
        }
    }

    /** Binding, resizing and a cache change can each queue a one-off Glance redraw. */
    fun awaitUpdatesSettled() {
        var count = updates.get()
        var since = System.nanoTime()
        await("the fixture's widget updates settle") {
            val now = updates.get()
            if (now != count) {
                count = now
                since = System.nanoTime()
            }
            System.nanoTime() - since >= TimeUnit.SECONDS.toNanos(1)
        }
    }

    fun await(what: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)
        while (!onMain(condition)) {
            if (System.nanoTime() >= deadline) throw AssertionError("$what; native text: ${onMain { descendants().filterIsInstance<TextView>().map { it.text.toString() } }}")
            Thread.sleep(20)
        }
    }

    override fun close() {
        onMain {
            host.stopListening()
            host.deleteHost()
        }
        scenario.close()
        org.junit.Assert.assertEquals("the disposable host has no widgets", 0, manager.getAppWidgetIds(ComponentName(context, TokklWidgetReceiver::class.java)).size)
    }

    private fun descendants(): List<View> {
        fun walk(node: View): List<View> = listOf(node) + if (node is ViewGroup) (0 until node.childCount).flatMap { walk(node.getChildAt(it)) } else emptyList()
        return walk(view)
    }

    private fun <T> onMain(action: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return action()
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
    }

    private companion object {
        /** A test's fresh WorkManager flips its receiver once; a second reset leaves margin. */
        const val MAX_RESET_RESTARTS = 2
    }
}
