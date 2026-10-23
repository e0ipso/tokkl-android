package com.github.e0ipso.tokkl

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.Trace
import android.util.Base64
import android.view.FrameMetrics
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.WindowCompat
import com.github.e0ipso.tokkl.tags.Tag
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsContent
import com.github.e0ipso.tokkl.tags.TagsData
import com.github.e0ipso.tokkl.tags.TagsState
import com.github.e0ipso.tokkl.trash.TrashActions
import com.github.e0ipso.tokkl.trash.TrashContent
import com.github.e0ipso.tokkl.trash.TrashEntry
import com.github.e0ipso.tokkl.trash.TrashState
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

/** Source-only profiling overlay: never included in the debug or release APK. */
class CatalogProfileActivity : ComponentActivity() {
    private lateinit var nonce: String
    private lateinit var workload: String
    private lateinit var fixtureHash: String
    private lateinit var names: Map<String, Pair<Int, Long>>
    private val thread = HandlerThread("catalog-profile-frames")
    private lateinit var frameHandler: Handler
    private val lock = Any()
    private val frames = ArrayList<JSONObject>()
    private val touches = ArrayList<JSONObject>()
    private val phases = ArrayList<JSONObject>()
    private var active: JSONObject? = null
    private var collectorFailure: String? = null
    private var ready = false
    private var commitRequested = false
    private var drawSerial = 0L
    private var lastDrawNs = 0L
    private var listenerRemoved = false
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        // FrameMetrics is reused by Android. Copy on its callback thread, not after returning.
        synchronized(lock) {
            if (frames.size >= MAX_FRAMES) collectorFailure = "frame stream capacity exceeded"
            else frames += JSONObject().put("seq", frames.size).put("callback", clock())
                .put("intended_vsync_ns", metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                .put("vsync_ns", metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP))
                .put("total_ns", metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                .put("deadline_duration_ns", metrics.getMetric(FrameMetrics.DEADLINE))
                .put("first_draw", metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
                .put("dropped_reports", dropped)
        }
    }
    private val drawListener = ViewTreeObserver.OnDrawListener {
        drawSerial++
        lastDrawNs = System.nanoTime()
        if (!commitRequested) {
            commitRequested = true
            window.decorView.viewTreeObserver.registerFrameCommitCallback {
                runOnUiThread {
                    finishPhase()
                    ready = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nonce = requireNotNull(intent.getStringExtra("nonce")).also { require(it.matches(Regex("[a-f0-9]{64}"))) }
        workload = requireNotNull(intent.getStringExtra("workload")).also { require(it in listOf("tags", "trash")) }
        fixtureHash = requireNotNull(intent.getStringExtra("fixtureSha256"))
        val raw = decodeFixture(requireNotNull(intent.getStringExtra("fixture")), fixtureHash)
        val json = Json { ignoreUnknownKeys = true }
        val fixture = json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        require(fixture.getValue("schema").jsonPrimitive.content == "catalog-fixture-v1")
        require(fixture.getValue("timezone").jsonPrimitive.content == "Europe/Madrid")
        val tags = json.decodeFromJsonElement<List<Tag>>(fixture.getValue("tags"))
        val trash = json.decodeFromJsonElement<List<TrashEntry>>(fixture.getValue("trash"))
        require(tags.size == 500 && tags.map { it.id }.toSet().size == 500)
        require(tags.map { it.name }.toSet() == (0 until 500).map { "Measured tag %03d".format(java.util.Locale.ROOT, it) }.toSet())
        require(trash.size == 1000 && trash.map { it.id }.toSet().size == 1000)
        require(trash.map { it.description }.toSet() == (0 until 1000).map { "Measured trash $it" }.toSet())
        require(trash.all {
            java.time.Instant.parse(it.start) == java.time.Instant.parse("2026-10-19T08:00:00Z") &&
                java.time.Instant.parse(requireNotNull(it.stop)) == java.time.Instant.parse("2026-10-19T08:05:00Z") &&
                it.duration == 300L
        })
        names = if (workload == "tags") tags.mapIndexed { i, row -> row.name to (i to row.id) }.toMap()
        else trash.mapIndexed { i, row -> requireNotNull(row.description) to (i to row.id) }.toMap()
        WindowCompat.enableEdgeToEdge(window)
        thread.start()
        frameHandler = Handler(thread.looper)
        window.addOnFrameMetricsAvailableListener(listener, frameHandler)
        window.decorView.viewTreeObserver.addOnDrawListener(drawListener)
        current = WeakReference(this)
        beginPhase("opening")
        setContent {
            TokklTheme(darkTheme = false) {
                if (workload == "tags") TagsContent(TagsState(data = TagsData(tags)), false, emptyList(), TagsActions())
                else TrashContent(TrashState(entries = trash), false, null, TrashActions())
            }
        }
    }

    private fun beginPhase(id: String) {
        require(id.matches(Regex("[a-zA-Z0-9_.-]{1,64}")) && active == null)
        require(phases.none { it.getString("id") == id })
        active = JSONObject().put("id", id).put("start", clock())
        Trace.beginAsyncSection("catalog/$id", 263)
    }

    private fun finishPhase(): JSONObject {
        val phase = requireNotNull(active)
        Trace.endAsyncSection("catalog/${phase.getString("id")}", 263)
        phase.put("end", clock())
        phases += phase
        active = null
        return phase
    }

    private fun snapshot(): JSONObject {
        Trace.beginSection("catalog-observe")
        try {
            val roots = ArrayList<SemanticsNode>()
            fun visitView(view: View) {
                if (view is RootForTest) roots += view.semanticsOwner.unmergedRootSemanticsNode
                if (view is ViewGroup) repeat(view.childCount) { visitView(view.getChildAt(it)) }
            }
            visitView(window.decorView)
            val nodes = ArrayList<SemanticsNode>()
            fun visit(node: SemanticsNode) {
                nodes += node
                node.children.forEach(::visit)
            }
            roots.forEach(::visit)
            val scroll = nodes.single { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            val bounds = scroll.boundsInWindow
            val screen = IntArray(2).also { window.decorView.getLocationOnScreen(it) }
            val local = IntArray(2).also { window.decorView.getLocationInWindow(it) }
            val dx = screen[0] - local[0]
            val dy = screen[1] - local[1]
            val anchors = JSONArray()
            var exposed = 0
            nodes.forEach { node ->
                val label = node.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text
                val row = label?.let { names[it] }
                if (row != null) {
                    exposed++
                    val rect = node.boundsInWindow
                    val position = node.positionInWindow
                    if (rect.width > 0 && rect.height > 0 && rect.bottom > bounds.top && rect.top < bounds.bottom) {
                        anchors.put(JSONObject().put("id", row.second).put("index", row.first)
                            .put("left", position.x + dx).put("top", position.y + dy)
                            .put("right", position.x + node.size.width + dx).put("bottom", position.y + node.size.height + dy)
                            .put("clipped_top", rect.top + dy).put("clipped_bottom", rect.bottom + dy))
                    }
                }
            }
            val insets = requireNotNull(window.decorView.rootWindowInsets)
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val config = resources.configuration
            return JSONObject().put("clock", clock()).put("ready", ready)
                .put("workload", workload).put("fixture_sha256", fixtureHash)
                .put("draw_serial", drawSerial).put("last_draw_ns", lastDrawNs)
                .put("exposed_row_texts", exposed).put("anchors", anchors)
                .put("viewport", JSONArray(listOf(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy)))
                .put("environment", JSONObject().put("width", window.decorView.width).put("height", window.decorView.height)
                    .put("density_dpi", resources.displayMetrics.densityDpi).put("font_scale", config.fontScale)
                    .put("locale", config.locales.toLanguageTags()).put("orientation", config.orientation)
                    .put("refresh_hz", requireNotNull(window.decorView.display).refreshRate)
                    .put("bars", JSONArray(listOf(bars.left, bars.top, bars.right, bars.bottom)))
                    .put("ime_visible", insets.isVisible(WindowInsets.Type.ime())).put("theme", "light"))
        } finally {
            Trace.endSection()
        }
    }

    /** Only the DUMP-authorized shell identity can send; the per-launch nonce binds the owner. */
    class Control : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val activity = current?.get()
            val received = intent.getStringExtra("nonce") ?: ""
            if (activity == null || received.length != 64 || !MessageDigest.isEqual(received.toByteArray(), activity.nonce.toByteArray())) {
                resultCode = Activity.RESULT_CANCELED
                return
            }
            val pending = goAsync()
            fun reply(block: () -> JSONObject) {
                activity.runOnUiThread {
                    try {
                        synchronized(activity.lock) { check(activity.collectorFailure == null) { "collector overflow" } }
                        val text = block().toString()
                        check(text.toByteArray().size <= 128 * 1024) { "control reply too large" }
                        pending.setResultCode(Activity.RESULT_OK)
                        pending.setResultExtras(Bundle().apply { putString("json", text) })
                    } catch (_: Exception) {
                        pending.setResultCode(Activity.RESULT_CANCELED)
                    } finally {
                        pending.finish()
                    }
                }
            }
            when (intent.getStringExtra("op")) {
                "ready", "settle" -> {
                    val started = System.nanoTime()
                    Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                        override fun doFrame(frameTimeNanos: Long) {
                            val now = System.nanoTime()
                            val quiet = now - activity.lastDrawNs >= 50_000_000L && now - started >= 50_000_000L
                            if (activity.ready && (intent.getStringExtra("op") == "ready" || quiet)) {
                                reply { activity.snapshot().put("quiet_draw_ns", now - activity.lastDrawNs) }
                            } else if (now - started >= (if (intent.getStringExtra("op") == "ready") 4_000_000_000L else 2_000_000_000L)) {
                                reply { error("readiness/settling timeout") }
                            }
                            else Choreographer.getInstance().postFrameCallback(this)
                        }
                    })
                }
                "snapshot" -> reply { check(activity.ready); activity.snapshot() }
                "begin" -> activity.frameHandler.post {
                    reply { activity.beginPhase(requireNotNull(intent.getStringExtra("phase"))); requireNotNull(activity.active) }
                }
                "end" -> {
                    val phase = runCatching { activity.finishPhase() }.getOrNull()
                    activity.frameHandler.post { reply { requireNotNull(phase) } }
                }
                "seal" -> {
                    if (intent.getBooleanExtra("finalise", false)) {
                        activity.window.removeOnFrameMetricsAvailableListener(activity.listener)
                        activity.listenerRemoved = true
                    }
                    activity.frameHandler.post {
                        reply {
                            check(activity.active == null)
                            synchronized(activity.lock) {
                                JSONObject().put("frame_total", activity.frames.size).put("touch_total", activity.touches.size)
                                    .put("phases", JSONArray(activity.phases)).put("listener_removed", activity.listenerRemoved)
                                    .put("barrier_ack", clock()).put("dropped_boundary_attribution", "UNKNOWN")
                            }
                        }
                    }
                }
                "frames", "touches" -> activity.frameHandler.post {
                    reply {
                        val from = intent.getIntExtra("from", 0)
                        val until = intent.getIntExtra("until", 0)
                        val key = requireNotNull(intent.getStringExtra("op"))
                        synchronized(activity.lock) {
                            val rows = if (key == "frames") activity.frames else activity.touches
                            require(from in 0..until && until <= rows.size)
                            JSONObject().put(key, JSONArray(rows.subList(from, minOf(from + 128, until))))
                                .put("next", minOf(from + 128, until)).put("total", until)
                                .put("phases", JSONArray(activity.phases))
                        }
                    }
                }
                else -> reply { error("unknown operation") }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (active?.getString("id")?.let { it != "opening" } == true) {
            synchronized(lock) {
                repeat(event.historySize) { history ->
                    if (touches.size >= 4096) collectorFailure = "touch stream capacity exceeded"
                    else touches += JSONObject().put("seq", touches.size).put("clock", clock()).put("historical", true)
                        .put("action", MotionEvent.ACTION_MOVE).put("event_ms", event.getHistoricalEventTime(history))
                        .put("source", event.source).put("tool_type", event.getToolType(0))
                        .put("down_ms", event.downTime).put("x", event.getHistoricalX(history) + event.rawX - event.x)
                        .put("y", event.getHistoricalY(history) + event.rawY - event.y)
                }
                if (touches.size >= 4096) collectorFailure = "touch stream capacity exceeded"
                else touches += JSONObject().put("seq", touches.size).put("clock", clock())
                    .put("action", event.actionMasked).put("event_ms", event.eventTime).put("down_ms", event.downTime)
                    .put("source", event.source).put("tool_type", event.getToolType(0))
                    .put("x", event.rawX).put("y", event.rawY)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        if (::frameHandler.isInitialized) {
            window.removeOnFrameMetricsAvailableListener(listener)
            window.decorView.viewTreeObserver.removeOnDrawListener(drawListener)
            thread.quitSafely()
        }
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private var current: WeakReference<CatalogProfileActivity>? = null
        private const val MAX_FRAMES = 20_000

        private fun clock() = JSONObject().put("mono_ns", System.nanoTime())
            .put("boot_ns", SystemClock.elapsedRealtimeNanos()).put("uptime_ms", SystemClock.uptimeMillis())

        private fun decodeFixture(encoded: String, expected: String): ByteArray {
            require(encoded.length <= 64 * 1024 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
            require(expected.matches(Regex("[a-f0-9]{64}")))
            val output = ByteArrayOutputStream()
            GZIPInputStream(ByteArrayInputStream(Base64.decode(encoded, Base64.NO_WRAP))).use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= 512 * 1024)
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == expected)
            return bytes
        }
    }
}
