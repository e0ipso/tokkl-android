package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import java.io.Closeable
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * A MockWebServer that answers the Reports API calls as a test sets them, and the
 * [ServerReportsRepository] that sends them to it. Each call that reaches the server is recorded
 * in [calls] with what its request asked for. A call the test set no answer for gets a 500.
 */
internal class TestReportsServer : Closeable {

    private val testApi = TestApi(null)
    private val server = MockWebServer()

    /** Every call, in the order the server got them. */
    val calls = CopyOnWriteArrayList<ReportCall>()

    /** The account's default workspace, with its rounding and whether the account has a Tokkl role. */
    @Volatile
    var workspace: ReportWorkspace? = null

    @Volatile
    var summaryAnswer: ((ReportCall.Summary) -> MockResponse)? = null

    @Volatile
    var detailedAnswer: ((ReportCall.Detailed) -> MockResponse)? = null

    @Volatile
    var weeklyAnswer: ((ReportCall.Weekly) -> MockResponse)? = null

    val workspaceCalls: List<ReportCall.Workspace> get() = calls.filterIsInstance<ReportCall.Workspace>()
    val summaries: List<ReportCall.Summary> get() = calls.filterIsInstance<ReportCall.Summary>()
    val detailedPages: List<ReportCall.Detailed> get() = calls.filterIsInstance<ReportCall.Detailed>()
    val weeklies: List<ReportCall.Weekly> get() = calls.filterIsInstance<ReportCall.Weekly>()

    val repository: ServerReportsRepository

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer(request) ?: NO_ANSWER
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        repository = ServerReportsRepository { testApi.api(url) }
    }

    override fun close() = server.close()

    private fun answer(request: RecordedRequest): MockResponse? {
        val path = request.url.encodedPath
        val body = request.body?.utf8()?.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it).jsonObject }
        return when {
            path == "/api/v9/me" -> {
                calls += ReportCall.Workspace
                // A Tokkl role makes the workspace a candidate for the app report.
                val role = if (workspace?.appSummaryCandidate == true) ",\"role\":\"user\"" else ""
                workspace?.let { json("{\"default_workspace_id\":${it.id}$role}") }
            }

            path == "/api/v9/workspaces/${workspace?.id}" ->
                workspace?.let { json("""{"rounding":${it.rounding},"rounding_minutes":${it.roundingMinutes}}""") }

            path == "/api/app/report" && body != null -> {
                val call = ReportCall.Summary(null, body.dates(), body.filters())
                calls += call
                summaryAnswer?.invoke(call)
            }

            path.endsWith("/search/time_entries") && body != null -> {
                val rounded = body.rounded(path)
                if (body.getValue("order_dir").jsonPrimitive.content == "ASC") {
                    val call = ReportCall.Summary(rounded, body.dates(), body.filters())
                    calls += call
                    summaryAnswer?.invoke(call)
                } else {
                    val firstRow = body.int("first_row_number")
                    val call = ReportCall.Detailed(rounded, body.dates(), firstRow, body.filters())
                    calls += call
                    detailedAnswer?.invoke(call)
                }
            }

            path.endsWith("/weekly/time_entries") && body != null -> {
                val call = ReportCall.Weekly(body.rounded(path), body.dates().from, body.filters())
                calls += call
                weeklyAnswer?.invoke(call)
            }

            else -> null
        }
    }

    private companion object {
        val NO_ANSWER: MockResponse = MockResponse.Builder().code(500).body("\"the test set no answer\"").build()
    }
}

/**
 * A call the view model's requests made to [TestReportsServer], with what they asked for.
 * [workspace] is the workspace in the path, with the body's rounding: null for Tokkl's app report,
 * which picks the default workspace and its rounding itself.
 */
internal sealed interface ReportCall {
    data object Workspace : ReportCall

    /** The summary, from Tokkl's app report or from the search of every entry, oldest first. */
    data class Summary(val workspace: ReportWorkspace?, val range: ReportRange, val filters: ReportFilters) : ReportCall

    data class Detailed(
        val workspace: ReportWorkspace,
        val range: ReportRange,
        val firstRow: Int,
        val filters: ReportFilters,
    ) : ReportCall

    data class Weekly(val workspace: ReportWorkspace, val weekStart: LocalDate, val filters: ReportFilters) : ReportCall
}

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

/** The dates a report request asked for. */
private fun JsonObject.dates() =
    range(getValue("start_date").jsonPrimitive.content, getValue("end_date").jsonPrimitive.content)

/** The workspace of a Reports API v3 [path] with the rounding this body asks for. */
private fun JsonObject.rounded(path: String) = ReportWorkspace(
    WorkspaceId(path.split("/")[WORKSPACE_SEGMENT].toLong()),
    int("rounding"),
    int("rounding_minutes"),
)

/** `/reports/api/v3/workspace/{id}/...` split at each `/` holds the id here. */
private const val WORKSPACE_SEGMENT = 5

/** The filters a report request sent, read back as the web app's fields hold them. */
private fun JsonObject.filters(): ReportFilters {
    fun ids(field: String): List<Long?>? = (this[field] as? JsonArray)?.map { it.jsonPrimitive.longOrNull }
    val excludedProjects = ids("exclude_project_ids")
    val excludedClients = ids("exclude_client_ids")
    val excludedTags = ids("exclude_tag_ids")
    return ReportFilters(
        projects = ids("project_ids") ?: excludedProjects.orEmpty(),
        clients = ids("client_ids") ?: excludedClients.orEmpty(),
        tags = ids("tag_ids") ?: excludedTags.orEmpty(),
        billable = when (this["billable"]?.jsonPrimitive?.booleanOrNull) {
            true -> BillableFilter.BILLABLE
            false -> BillableFilter.NON_BILLABLE
            null -> BillableFilter.ALL
        },
        projectsNot = excludedProjects != null,
        clientsNot = excludedClients != null,
        tagsNot = excludedTags != null,
    )
}

/** Workspace 7 with [rounding] and [roundingMinutes], as the account's default workspace. */
internal fun reportWorkspace(rounding: Int = 0, roundingMinutes: Int = 0, appSummaryCandidate: Boolean = false) =
    ReportWorkspace(WorkspaceId(7), rounding, roundingMinutes, appSummaryCandidate)

/** A summary of [range] whose only number is its total of [seconds]. */
internal fun summaryOf(range: ReportRange, seconds: Long) = SummaryReport(
    range = range,
    resolution = range.resolution,
    totals = ReportTotals(seconds, billableSeconds = 0, entryCount = 1, amounts = emptyList()),
    series = emptyList(),
    projects = emptyList(),
)

/** Tokkl's app report that reads as [summaryOf] [range] and [seconds]. */
internal fun appSummary(range: ReportRange, seconds: Long) = json(
    """{"start_date":"${range.from}","end_date":"${range.to}","resolution":"${range.resolution.name.lowercase()}",""" +
        """"totals":{"seconds":$seconds,"billable_seconds":0,"entry_count":1,"amounts":[]},"series":[],"groups":[]}""",
)

/**
 * A search page with one entry of [seconds] that starts at 11:00 UTC on Tuesday 20 October, which
 * is already Wednesday the 21st at UTC+14.
 */
internal fun searchPage(seconds: Long) = json(
    """[{"billable":false,"currency":"EUR","project_id":3,"project_name":"Web","time_entries":""" +
        """[{"id":1,"seconds":$seconds,"start":"2026-10-20T11:00:00+00:00","stop":null}]}]""",
)

/** A detailed search page of [rows], whose next page starts at [nextRow], or the last page with none. */
internal fun detailedPage(rows: List<String>, nextRow: Int?) = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "application/json")
    .apply { if (nextRow != null) addHeader("X-Next-Row-Number", nextRow.toString()) }
    .body(rows.joinToString(",", "[", "]"))
    .build()

/** An answer with [status] and no body. */
internal fun status(status: Int, vararg headers: Pair<String, String>): MockResponse = MockResponse.Builder()
    .code(status)
    .apply { for ((name, value) in headers) addHeader(name, value) }
    .build()

/** The connection drops before any answer, as when the phone has no network. */
internal fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

internal fun json(body: String): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "application/json")
    .body(body)
    .build()

/** The range from [from] to [to], both inclusive. */
internal fun range(from: String, to: String) = ReportRange(LocalDate.parse(from), LocalDate.parse(to))

/**
 * Holds the work the view model sends off the main thread until [runAll] runs it on the calling
 * thread, so a test sees what the screen shows while an answer is on its way.
 */
internal class QueuedDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
