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
    }
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
    fun assertTextFitsVertically() = onMain {
        descendants().filterIsInstance<TextView>().filter { it.text.isNotEmpty() }.forEach { text ->
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
    }

    /** Observe native Chronometer ticks, with no widget updates in between. */
    fun assertClockTicksWithoutUpdates() {
        val ticks = CountDownLatch(2)
        var first: String? = null
        val count = updates.get()
        onMain {
            val clock = descendants().filterIsInstance<Chronometer>().single()
            first = clock.text.toString()
            clock.setOnChronometerTickListener { ticks.countDown() }
        }
        assertTrue("the launcher ticks the clock", ticks.await(4, TimeUnit.SECONDS))
        onMain {
            val clock = descendants().filterIsInstance<Chronometer>().single()
            assertTrue("elapsed time changed", first != clock.text.toString())
            clock.onChronometerTickListener = null
        }
        org.junit.Assert.assertEquals("no RemoteViews update was needed to tick", count, updates.get())
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
}
