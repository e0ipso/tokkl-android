package com.github.e0ipso.tokkl.catalogprofile

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.benchmark.TARGET_PACKAGE
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Fixture/network setup and target observation are distinct from the marked scroll phase. */
internal class CatalogProbe(private val workload: String, private val phase: String) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val automation = instrumentation.uiAutomation
    private val output = File(requireNotNull(arguments.getString("additionalTestOutputDir"))).apply { check(isDirectory || mkdirs()) }
    private val run = requireNotNull(arguments.getString("catalogRunId")).also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,32}"))) }
    private val revision = requireNotNull(arguments.getString("catalogRevision")).also { require(it.matches(Regex("[a-f0-9]{40}"))) }
    private val baseline = baseline(arguments, run, revision, output)
    private val viewport get() = baseline.viewport(workload)
    private val fixture = fixture(arguments, automation, File(output, "$run-$revision-$workload-$phase-seed-receipt.json"))
    private val nonce = randomHex(32)
    private val prefix = "$run-$revision-$workload-$phase"
    private val completed = HashSet<Int>()
    private var launched = false
    private var firstAnchor: JSONObject? = null
    private var frameCursor = 0
    private var touchCursor = 0
    private val record = JSONObject().put("schema", "catalog-window-v1").put("run", run).put("revision", revision)
        .put("workload", workload).put("phase", phase).put("fixture_sha256", fixture.hash)
        .put("compilation", "Full").put("absolute_pixel_travel", JSONObject.NULL)
    private var currentRecord: JSONObject? = null

    init {
        require(phase in listOf("first", "steady"))
        output.mkdirs()
        writeOnce("fixture.json", fixture.raw)
        writeOnce("fixture-transport.json", JSONObject().put("sha256", fixture.hash).put("encoded", fixture.encoded).toString().toByteArray())
    }

    fun prepare(scope: MacrobenchmarkScope, index: Int) {
        require(index in 0 until 10 && index !in completed)
        if (!launched || phase == "first") {
            scope.killProcess()
            frameCursor = 0
            touchCursor = 0
            scope.startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.CatalogProfileActivity"))
                .putExtra("nonce", nonce).putExtra("workload", workload).putExtra("fixture", fixture.encoded)
                .putExtra("fixtureSha256", fixture.hash))
            // RPC completion is bounded; the target waits for its actual first frame commit.
            val ready = rpc("ready")
            writeOnce("ready-$index.json", ready.toString().toByteArray())
            checkedSnapshot(ready, observe = true)
            baseline.retain(workload, phase, fixture.hash)
            val initial = snapshot()
            firstAnchor = initial
            writeOnce("opening-$index.json", JSONObject(record.toString()).put("generation", index).put("snapshot", initial)
                .put("stream", stream()).toString().toByteArray())
            launched = true
            if (phase == "steady") {
                val seen = HashSet<Int>()
                traverse(up = true, finalIndex = if (workload == "tags") 499 else 999, seen = seen)
                check(seen.size == if (workload == "tags") 500 else 1000) { "warmup did not expose every row" }
                reset()
            }
        } else reset()
        check(sameAnchors(requireNotNull(firstAnchor), snapshot())) { "initial anchors did not reset exactly" }
    }

    fun measure(index: Int) {
        val id = "$workload-$phase-$index"
        val result = JSONObject(record.toString()).put("index", index).put("generation", if (phase == "first") index else 0)
            .put("marker", "catalog/$id")
        currentRecord = result
        val gestures = JSONArray()
        result.put("gestures", gestures).put("before", snapshot())
        val wallStart = SystemClock.elapsedRealtimeNanos()
        var began = false
        try {
            result.put("begin", rpc("begin", phaseId = id))
            began = true
            repeat(5) {
                check(SystemClock.elapsedRealtimeNanos() - wallStart < 15_000_000_000L) { "window wall limit exceeded" }
                val gesture = inject(up = true, durationMs = 150)
                gestures.put(gesture)
                gesture.put("after", checkedSnapshot(rpc("settle")))
            }
            result.put("after", snapshot())
            check(SystemClock.elapsedRealtimeNanos() - wallStart < 15_000_000_000L) { "window wall limit exceeded" }
            result.put("complete", true)
            completed += index
        } catch (failure: Throwable) {
            result.put("complete", false).put("failure", failure.javaClass.simpleName)
            throw failure
        } finally {
            if (began) runCatching { result.put("end", rpc("end")) }.onFailure { result.put("complete", false).put("end_unknown", true) }
            val wallNs = SystemClock.elapsedRealtimeNanos() - wallStart
            result.put("wall_ns", wallNs)
            if (wallNs >= 15_000_000_000L) result.put("complete", false).put("wall_limit_exceeded", true)
            runCatching { result.put("stream", stream(finalise = phase == "first" || index == 9)) }
                .onFailure { result.put("complete", false).put("stream_unknown", true) }
            writeOnce("window-$index.json", result.toString().toByteArray())
            currentRecord = null
        }
        check(result.getBoolean("complete")) { "phase closure incomplete" }
    }

    private fun snapshot(): JSONObject = checkedSnapshot(rpc("snapshot"))

    private fun checkedSnapshot(value: JSONObject, observe: Boolean = false): JSONObject = value.also {
        check(value.getString("fixture_sha256") == fixture.hash)
        val environment = value.getJSONObject("environment")
        check(environment.getDouble("font_scale") == 1.0 && !environment.getBoolean("ime_visible"))
        check(kotlin.math.abs(environment.getDouble("refresh_hz") - 60.0) < 0.1)
        check(value.getJSONArray("anchors").length() > 0)
        baseline.validate(workload, value, observe)
    }

    private fun reset() {
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(600) {
            if (sameAnchors(requireNotNull(firstAnchor), snapshot())) return
            check(SystemClock.elapsedRealtimeNanos() - start < 180_000_000_000L) { "reset wall limit" }
            inject(up = false, durationMs = 500)
            checkedSnapshot(rpc("settle"))
        }
        error("reset drag limit")
    }

    private fun traverse(up: Boolean, finalIndex: Int, seen: MutableSet<Int>) {
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(600) {
            val rows = snapshot().getJSONArray("anchors")
            repeat(rows.length()) { seen += rows.getJSONObject(it).getInt("index") }
            if (finalIndex in seen) return
            check(SystemClock.elapsedRealtimeNanos() - start < 180_000_000_000L) { "warmup wall limit" }
            inject(up, 500)
            checkedSnapshot(rpc("settle"))
        }
        error("warmup drag limit")
    }

    private fun inject(up: Boolean, durationMs: Long): JSONObject {
        val events = JSONArray()
        val x = (viewport[0] + viewport[2]) / 2
        val bottom = viewport[1] + (viewport[3] - viewport[1]) * 0.8f
        val top = viewport[1] + (viewport[3] - viewport[1]) * 0.2f
        val startY = if (up) bottom else top
        val endY = if (up) top else bottom
        val down = SystemClock.uptimeMillis()
        try {
            for (offset in 0L..durationMs step 10L) {
                val wait = down + offset - SystemClock.uptimeMillis()
                if (wait > 0) CountDownLatch(1).await(wait, TimeUnit.MILLISECONDS)
                val action = when (offset) { 0L -> MotionEvent.ACTION_DOWN; durationMs -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val actual = SystemClock.uptimeMillis()
                val y = startY + (endY - startY) * offset.toFloat() / durationMs
                val event = touchEvent(down, actual, action, x, y)
                val before = clock()
                val accepted = try { automation.injectInputEvent(event, true) } finally { event.recycle() }
                events.put(JSONObject().put("offset_ms", offset).put("event_ms", actual).put("action", action)
                    .put("x", x).put("y", y).put("before", before).put("after", clock()).put("accepted", accepted))
                check(accepted) { "input injection refused" }
            }
        } catch (failure: Throwable) {
            val cancel = touchEvent(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, endY)
            runCatching { automation.injectInputEvent(cancel, true) }
            cancel.recycle()
            currentRecord?.put("failed_injection", events)
            throw failure
        }
        val elapsed = events.getJSONObject(events.length() - 1).getLong("event_ms") - events.getJSONObject(0).getLong("event_ms")
        check(elapsed > 0)
        return JSONObject().put("events", events).put("requested_ms", durationMs)
            .put("finger_pixels_per_second", (endY - startY) * 1000.0 / elapsed)
    }

    private fun touchEvent(down: Long, time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val pointer = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y
            pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else 1f
            size = 1f
        }
        return MotionEvent.obtain(down, time, action, 1, arrayOf(pointer), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun rpc(op: String, phaseId: String? = null, from: Int = 0, until: Int = 0, finalise: Boolean = false): JSONObject {
        val latch = CountDownLatch(1)
        var reply: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (resultCode == Activity.RESULT_OK) reply = getResultExtras(false)?.getString("json")
                latch.countDown()
            }
        }
        automation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            instrumentation.context.sendOrderedBroadcast(
                Intent().setComponent(ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.CatalogProfileActivity\$Control"))
                    .putExtra("nonce", nonce).putExtra("op", op).putExtra("phase", phaseId).putExtra("from", from)
                    .putExtra("until", until).putExtra("finalise", finalise),
                null, receiver, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null,
            )
            check(latch.await(5, TimeUnit.SECONDS)) { "target control timeout" }
            return JSONObject(checkNotNull(reply) { "target control rejected" })
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private fun stream(finalise: Boolean = false): JSONObject {
        val fence = rpc("seal", finalise = finalise)
        fence.put("frame_from", frameCursor).put("touch_from", touchCursor)
        val frames = JSONArray()
        var packet: JSONObject
        do {
            packet = rpc("frames", from = frameCursor, until = fence.getInt("frame_total"))
            val batch = packet.getJSONArray("frames")
            repeat(batch.length()) { frames.put(batch.getJSONObject(it)) }
            frameCursor = packet.getInt("next")
        } while (frameCursor < packet.getInt("total"))
        val touches = JSONArray()
        do {
            packet = rpc("touches", from = touchCursor, until = fence.getInt("touch_total"))
            val batch = packet.getJSONArray("touches")
            repeat(batch.length()) { touches.put(batch.getJSONObject(it)) }
            touchCursor = packet.getInt("next")
        } while (touchCursor < packet.getInt("total"))
        return fence.put("frames", frames).put("touches", touches)
    }

    fun requireComplete() {
        check(completed == (0 until 10).toSet()) { "not exactly ten windows" }
        val seen = HashSet<Int>()
        traverse(up = true, finalIndex = if (workload == "tags") 499 else 999, seen = seen)
        writeOnce("final-anchor.json", snapshot().toString().toByteArray())
    }
    fun close() { /* The script and guarded runtime own devices; no unrelated process is stopped. */ }

    private fun writeOnce(suffix: String, bytes: ByteArray) {
        val file = File(output, "$prefix-$suffix")
        check(file.createNewFile()) { "output already exists" }
        file.writeBytes(bytes)
    }

    companion object {
        private const val EAGER = "d16c97a5e98ab0747041b02b8dd9a058bc84c700"
        private const val LAZY = "4b3891e69f3d5290de73570aa759eb1c3337914a"
        private const val SERVER_IMAGE = "ghcr.io/e0ipso/tokkl:1.8.1@sha256:70106ba2a6bc5b23c11f443f33bf2c7fb76d3af75b0335461047c4d27bd52e9b"
        private val expectationKeys = setOf("catalogEnvironmentSha256", "catalogTagsViewport", "catalogTrashViewport")
        private var cachedBaseline: RunBaseline? = null

        @Synchronized private fun baseline(args: Bundle, run: String, revision: String, output: File): RunBaseline {
            val seed = args.getString("catalogSeed") == "true"
            val modeKeys = if (seed) setOf("catalogSeed", "catalogServerImage")
                else expectationKeys + setOf("catalogFixture", "catalogFixtureSha256")
            val keys = args.keySet().filter { it.startsWith("catalog") }.toSet()
            require(keys == modeKeys + setOf("catalogRunId", "catalogRevision")) { "partial, mixed or unknown catalog arguments" }
            require(revision == if (seed) EAGER else LAZY) { "seed requires eager; lazy must replay the accepted seed baseline" }
            if (seed) require(args.getString("catalogServerImage") == SERVER_IMAGE)
            else require(requireNotNull(args.getString("catalogEnvironmentSha256")).matches(Regex("[a-f0-9]{64}")))
            val identity = JSONObject().put("output", output.canonicalPath)
            keys.sorted().forEach { identity.put(it, requireNotNull(args.getString(it))) }
            val serialized = identity.toString()
            cachedBaseline?.let { check(it.identity == serialized) { "catalog run or baseline arguments changed" }; return it }
            val expectedViewports = if (seed) emptyMap() else mapOf(
                "tags" to parseViewport(requireNotNull(args.getString("catalogTagsViewport"))),
                "trash" to parseViewport(requireNotNull(args.getString("catalogTrashViewport"))),
            )
            return RunBaseline(serialized, run, revision, output, seed, args.getString("catalogEnvironmentSha256"), expectedViewports)
                .also { cachedBaseline = it }
        }

        private fun parseViewport(text: String) = text.split(':').map { it.toFloat() }.also { values ->
            require(values.size == 4 && values.all { it.isFinite() } && values[2] > values[0] && values[3] > values[1])
        }

        private class RunBaseline(
            val identity: String, private val run: String, private val revision: String, private val output: File,
            private val seed: Boolean, private val expectedEnvironment: String?, private val expectedViewports: Map<String, List<Float>>,
        ) {
            private var environment: String? = null
            private val geometry = mutableMapOf<String, String>()
            private val viewports = mutableMapOf<String, List<Float>>()
            private val retained = mutableMapOf<String, JSONObject>()

            fun viewport(workload: String): List<Float> = checkNotNull(viewports[workload]) { "viewport not observed" }

            fun validate(workload: String, value: JSONObject, observe: Boolean) {
                val actualEnvironment = value.getJSONObject("environment").toString()
                expectedEnvironment?.let { check(sha(actualEnvironment.toByteArray()) == it) { "environment differs from accepted seed baseline" } }
                check(environment == actualEnvironment || (environment == null && observe)) { "environment changed within run" }
                val actual = value.getJSONArray("viewport")
                val coordinates = parseViewport((0 until actual.length()).joinToString(":") { actual.getDouble(it).toString() })
                expectedViewports[workload]?.let { expected ->
                    check((0..3).all { kotlin.math.abs(actual.getDouble(it) - expected[it]) <= 0.01 }) { "viewport mismatch" }
                    check(coordinates == expected) { "viewport differs from accepted seed baseline" }
                }
                val serialized = actual.toString()
                check(geometry[workload] == serialized || (geometry[workload] == null && observe)) { "viewport changed within run" }
                if (environment == null) environment = actualEnvironment
                if (geometry[workload] == null) {
                    geometry[workload] = serialized
                    viewports[workload] = coordinates
                }
            }

            fun retain(workload: String, phase: String, fixtureHash: String) {
                if (workload in retained) return
                val serializedEnvironment = checkNotNull(environment)
                val serializedViewport = checkNotNull(geometry[workload])
                val record = JSONObject().put("schema", "catalog-workload-baseline-v1").put("run", run).put("revision", revision)
                    .put("mode", if (seed) "seed" else "replay").put("fixture_sha256", fixtureHash).put("workload", workload)
                    .put("source_phase", phase).put("environment", JSONObject(serializedEnvironment))
                    .put("environment_serialized", serializedEnvironment).put("environment_sha256", sha(serializedEnvironment.toByteArray()))
                    .put("viewport", JSONArray(serializedViewport)).put("viewport_serialized", serializedViewport)
                    .put("viewport_argument", viewport(workload).joinToString(":"))
                write("$run-$revision-$workload-baseline.json", record)
                retained[workload] = record
                if (retained.keys == setOf("tags", "trash")) {
                    val replay = JSONObject().put("catalogEnvironmentSha256", sha(serializedEnvironment.toByteArray()))
                        .put("catalogTagsViewport", retained.getValue("tags").getString("viewport_argument"))
                        .put("catalogTrashViewport", retained.getValue("trash").getString("viewport_argument"))
                    write("$run-$revision-baseline.json", JSONObject().put("schema", "catalog-baseline-v1").put("run", run)
                        .put("revision", revision).put("mode", if (seed) "seed" else "replay").put("fixture_sha256", fixtureHash)
                        .put("workloads", JSONObject().put("tags", retained.getValue("tags")).put("trash", retained.getValue("trash")))
                        .put("replay_arguments", replay))
                }
            }

            private fun write(name: String, value: JSONObject) {
                val file = File(output, name)
                check(file.createNewFile()) { "baseline output already exists" }
                file.writeText(value.toString())
            }
        }

        private data class Fixture(val raw: ByteArray, val encoded: String, val hash: String)
        private var cachedFixture: Fixture? = null
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun randomHex(count: Int) = ByteArray(count).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        private fun clock() = JSONObject().put("mono_ns", System.nanoTime()).put("boot_ns", SystemClock.elapsedRealtimeNanos())
            .put("uptime_ms", SystemClock.uptimeMillis())
        private fun sameAnchors(a: JSONObject, b: JSONObject) = a.getJSONArray("anchors").toString() == b.getJSONArray("anchors").toString()

        @Synchronized private fun fixture(args: Bundle, automation: android.app.UiAutomation, receiptFile: File): Fixture {
            cachedFixture?.let { return it }
            val supplied = args.getString("catalogFixture")
            val value = if (supplied != null) {
                require(supplied.length <= 64 * 1024 && supplied.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
                val bytes = java.util.zip.GZIPInputStream(Base64.decode(supplied, Base64.NO_WRAP).inputStream()).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(output.size() + count <= 512 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                Fixture(bytes, supplied, sha(bytes)).also { require(it.hash == args.getString("catalogFixtureSha256")) }
            } else {
                check(args.getString("catalogSeed") == "true") { "provide the retained real-server fixture or explicitly enable seeding" }
                val raw = seed(args, automation, receiptFile).toString().toByteArray()
                require(raw.size <= 512 * 1024)
                val compressed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(raw) } }.toByteArray()
                Fixture(raw, Base64.encodeToString(compressed, Base64.NO_WRAP), sha(raw)).also { require(it.encoded.length <= 64 * 1024) }
            }
            cachedFixture = value
            return value
        }

        /** Real-server fixture only. No requests originate in the target activity or a window. */
        private fun seed(args: Bundle, automation: android.app.UiAutomation, receiptFile: File): JSONObject {
            val base = URI(requireNotNull(args.getString("tokklBaseUrl")))
            require(base.scheme == "http" && base.host in listOf("10.0.2.2", "127.0.0.1") && base.userInfo == null && base.query == null && base.fragment == null)
            val image = requireNotNull(args.getString("catalogServerImage"))
            require(image == SERVER_IMAGE)
            check(receiptFile.createNewFile())
            val receipt = JSONObject().put("phase", "preparing").put("cleanup", "UNKNOWN")
                .put("declared_server_image", image).put("server", base.toString())
            fun saveReceipt() { receiptFile.writeText(receipt.toString()) }
            saveReceipt()
            val credentials = requireNotNull(args.getString("tokklCredentialsFile"))
            require(credentials.matches(Regex("/data/local/tmp/tokkl-test-credentials-[A-Za-z0-9_.-]+\\.properties")))
            val properties = automation.executeShellCommand("cat '$credentials'").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { Properties().apply { load(it) } }
            }
            val admin = requireNotNull(properties.getProperty("tokklApiToken")) + ":api_token"
            val deadline = SystemClock.elapsedRealtime() + 5 * 60 * 1000
            fun request(method: String, path: String, auth: String?, body: JSONObject? = null, csrf: Boolean = false, expected: Set<Int> = setOf(200, 201, 204)): String {
                check(SystemClock.elapsedRealtime() < deadline) { "fixture preparation deadline exceeded" }
                val connection = URI(base.toString().trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = method
                if (auth != null) connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP))
                if (csrf) connection.setRequestProperty("X-Tokkl-Csrf", "1")
                try {
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                    check(connection.responseCode in expected) { "fixture HTTP request failed: ${connection.responseCode}" }
                    if (connection.responseCode !in setOf(200, 201)) return ""
                    return connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            require(output.size() + count <= 512 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray().toString(Charsets.UTF_8)
                    }
                } catch (_: java.io.IOException) {
                    error("fixture transport failed")
                } finally { connection.disconnect() }
            }
            val invite = JSONObject(request("POST", "/api/app/admin/invites", admin, JSONObject().put("role", "user")))
            val inviteToken = invite.getString("url").substringAfterLast('/').also { require(it.matches(Regex("[A-Za-z0-9_-]+"))) }
            val email = "catalog-${randomHex(8)}@example.com"
            val password = randomHex(16)
            receipt.put("email", email).put("phase", "accepting-invite"); saveReceipt()
            request("POST", "/api/app/invites/$inviteToken", null, JSONObject().put("email", email).put("password", password)
                .put("fullname", "Catalog profile").put("timezone", "Europe/Madrid"), csrf = true)
            receipt.put("phase", "account-created-id-unknown"); saveReceipt()
            val me = JSONObject(request("GET", "/api/v9/me", "$email:$password"))
            val id = me.getLong("id")
            val auth = me.getString("api_token") + ":api_token"
            val workspace = me.getLong("default_workspace_id")
            receipt.put("user_id", id).put("workspace_id", workspace).put("phase", "seeding"); saveReceipt()
            var result: JSONObject? = null
            try {
                repeat(500) { index ->
                    request("POST", "/api/v9/workspaces/$workspace/tags", auth, JSONObject().put("name", "Measured tag %03d".format(java.util.Locale.ROOT, index)))
                }
                repeat(1000) { index ->
                    val entry = JSONObject(request("POST", "/api/v9/workspaces/$workspace/time_entries", auth,
                        JSONObject().put("workspace_id", workspace).put("created_with", "tokkl-android tests")
                            .put("description", "Measured trash $index").put("start", "2026-10-19T08:00:00Z").put("stop", "2026-10-19T08:05:00Z")))
                    request("DELETE", "/api/v9/workspaces/$workspace/time_entries/${entry.getLong("id")}", auth)
                }
                result = JSONObject().put("schema", "catalog-fixture-v1").put("timezone", "Europe/Madrid")
                    .put("tags", JSONArray(request("GET", "/api/v9/workspaces/$workspace/tags", auth)))
                    .put("trash", JSONArray(request("GET", "/api/app/trash", auth)))
                return requireNotNull(result)
            } finally {
                // Cleanup has its own bounded requests even if the preparation deadline expired.
                val cleanup = requestCleanup(base, id, email, admin, auth)
                receipt.put("phase", "closed").put("cleanup", cleanup); saveReceipt()
                result?.put("origin", JSONObject().put("kind", "real-server").put("declared_server_image", image).put("cleanup", cleanup))
            }
        }

        private fun requestCleanup(base: URI, id: Long, email: String, admin: String, deletedAuth: String): JSONObject {
            fun call(path: String, method: String, auth: String, body: JSONObject? = null): Int {
                val c = URI(base.toString().trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 10_000; c.readTimeout = 10_000; c.requestMethod = method
                c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP))
                return try {
                    if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
                    c.responseCode
                } finally { c.disconnect() }
            }
            val deletion = call("/api/app/admin/users/$id", "DELETE", admin, JSONObject().put("email", email))
            check(deletion == 204) { "fixture account deletion unconfirmed" }
            val rejectedAuth = call("/api/v9/me", "GET", deletedAuth)
            check(rejectedAuth in setOf(401, 403)) { "deleted fixture account still authenticates or cleanup unknown" }
            return JSONObject().put("user_delete_status", deletion).put("deleted_auth_status", rejectedAuth)
        }
    }
}
