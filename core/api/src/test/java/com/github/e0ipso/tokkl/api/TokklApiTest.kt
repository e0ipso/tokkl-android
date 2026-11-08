package com.github.e0ipso.tokkl.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The exact request of each [TokklApi] method, byte for byte: method, path with query under the
 * server's base path, and body. The server answers every request with an empty object, so only
 * the requests matter here.
 */
class TokklApiTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, target and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()

    private lateinit var api: TokklApi

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.target} ${request.body?.utf8().orEmpty()}".trim()
                return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("{}").build()
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/tokkl").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        api = testApi.api(url)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun accountRequests() = runTest {
        api.account.me(JsonElement.serializer())
        api.account.meWithRelatedData()
        api.account.preferences()
        api.account.workspace(7, JsonElement.serializer())
        api.account.savePreferences(PomodoroDefaultsChange(40, 8, 20, 5), JsonElement.serializer())
        api.account.savePreferences(ReminderChange(enabled = false, hours = 4), JsonElement.serializer())
        api.account.savePreferences(TimerViewChange("calendar"), JsonElement.serializer())
        api.account.savePreferences(
            FavoritesChange(
                listOf(
                    FavoriteBody("Write the report", 1, 5, listOf("call", "deep work"), billable = true),
                    FavoriteBody("Website", null, null, emptyList(), billable = false),
                ),
            ),
            JsonElement.serializer(),
        )

        assertEquals(
            listOf(
                "GET /tokkl/api/v9/me",
                "GET /tokkl/api/v9/me?with_related_data=true",
                "GET /tokkl/api/v9/me/preferences",
                "GET /tokkl/api/v9/workspaces/7",
                """POST /tokkl/api/v9/me/preferences {"pomodoro_focus_interval_in_minutes":40,""" +
                    """"pomodoro_break_interval_in_minutes":8,"pomodoro_long_break_interval_in_minutes":20,""" +
                    """"pomodoro_long_break_every":5}""",
                """POST /tokkl/api/v9/me/preferences {"long_timer_reminder_enabled":false,""" +
                    """"long_timer_reminder_hours":4}""",
                """POST /tokkl/api/v9/me/preferences {"timerView":"calendar"}""",
                """POST /tokkl/api/v9/me/preferences {"favorites":[""" +
                    """{"description":"Write the report","project_id":1,"task_id":5,"tags":["call","deep work"],""" +
                    """"billable":true},""" +
                    """{"description":"Website","project_id":null,"task_id":null,"tags":[],"billable":false}]}""",
            ),
            requests,
        )
    }

    @Test
    fun timeEntryRequests() = runTest {
        val start = Instant.parse("2026-10-19T08:00:00Z")
        val stop = Instant.parse("2026-10-19T09:30:00Z")
        api.entries.list(start, stop)
        api.entries.current()
        api.entries.get(55)
        api.entries.bounds()
        api.entries.create(
            7,
            TimedEntryBody(7, "Tokkl for Android", "Report", null, null, listOf("call"), false, "$start", null, -1),
        )
        api.entries.update(
            7,
            55,
            TimedEntryBody(7, null, "Report", 3, 4, emptyList(), true, "$start", "$stop", 5400, allDay = false),
        )
        val leave = NoTimeEntryBody(7, null, "Leave", null, null, emptyList(), false, true, duration = 3600)
        api.entries.update(7, 56, leave.copy(startDate = "2026-10-19"))
        api.entries.create(7, leave.copy(createdWith = "Tokkl for Android", start = "$start", duration = 60))
        api.entries.stop(7, 55, stop)
        api.entries.delete(7, 55)
        api.entries.restore(55, resend = true)

        assertEquals(
            listOf(
                "GET /tokkl/api/v9/me/time_entries?start_date=2026-10-19T08%3A00%3A00Z" +
                    "&end_date=2026-10-19T09%3A30%3A00Z",
                "GET /tokkl/api/v9/me/time_entries/current",
                "GET /tokkl/api/v9/me/time_entries/55",
                "GET /tokkl/api/app/bounds",
                """POST /tokkl/api/v9/workspaces/7/time_entries {"workspace_id":7,""" +
                    """"created_with":"Tokkl for Android",""" +
                    """"description":"Report","project_id":null,"task_id":null,"tags":["call"],"billable":false,""" +
                    """"start":"2026-10-19T08:00:00Z","stop":null,"duration":-1}""",
                """PUT /tokkl/api/v9/workspaces/7/time_entries/55 {"workspace_id":7,"description":"Report",""" +
                    """"project_id":3,"task_id":4,"tags":[],"billable":true,"start":"2026-10-19T08:00:00Z",""" +
                    """"stop":"2026-10-19T09:30:00Z","duration":5400,"all_day":false}""",
                """PUT /tokkl/api/v9/workspaces/7/time_entries/56 {"workspace_id":7,"description":"Leave",""" +
                    """"project_id":null,"task_id":null,"tags":[],"billable":false,"all_day":true,""" +
                    """"start_date":"2026-10-19","duration":3600}""",
                """POST /tokkl/api/v9/workspaces/7/time_entries {"workspace_id":7,""" +
                    """"created_with":"Tokkl for Android",""" +
                    """"description":"Leave","project_id":null,"task_id":null,"tags":[],"billable":false,""" +
                    """"all_day":true,"start":"2026-10-19T08:00:00Z","duration":60}""",
                """PATCH /tokkl/api/v9/workspaces/7/time_entries/55/stop {"stop":"2026-10-19T09:30:00Z"}""",
                "DELETE /tokkl/api/v9/workspaces/7/time_entries/55",
                "POST /tokkl/api/app/time_entries/55/restore {}",
            ),
            requests,
        )
    }

    @Test
    fun trashRequests() = runTest {
        api.trash.list(JsonElement.serializer())
        api.trash.restore(55)
        api.trash.purge(55, JsonElement.serializer())
        api.trash.purgeAll(JsonElement.serializer())

        assertEquals(
            listOf(
                "GET /tokkl/api/app/trash",
                "POST /tokkl/api/app/time_entries/55/restore {}",
                "DELETE /tokkl/api/app/trash/55",
                "DELETE /tokkl/api/app/trash",
            ),
            requests,
        )
    }

    @Test
    fun catalogRequests() = runTest {
        val any = JsonElement.serializer()
        api.projects.list(7, any)
        api.projects.list(7, any, activeBoth = true)
        api.projects.create(7, ProjectBody("Shop", "#2da608", 0, false, null, null, null), any)
        api.projects.update(7, 5, ProjectBody("Website", "#0b83d9", 9, true, 12.5, "USD", 40.0), any)
        api.projects.setActive(7, 5, active = false, any)
        api.projects.tasks(7, 5, any)
        api.projects.createTask(7, 5, "Copy", any)
        api.projects.updateTask(7, 5, 1, TaskBody("Wireframes", active = false), any)
        api.projects.deleteTask(7, 5, 1)
        api.clients.list(7, any)
        api.clients.create(7, ClientBody("Surf school", null), any)
        api.clients.update(7, 1, ClientBody("Acme", "Pays late"), any)
        api.clients.archive(7, 1)
        api.clients.restore(7, 1, any)
        api.tags.list(7, any)
        api.tags.create(7, "Deep work", any)
        api.tags.rename(7, 3, "Focus", any)
        api.tags.delete(7, 3)

        assertEquals(
            listOf(
                "GET /tokkl/api/v9/workspaces/7/projects",
                "GET /tokkl/api/v9/workspaces/7/projects?active=both",
                """POST /tokkl/api/v9/workspaces/7/projects {"name":"Shop","color":"#2da608","client_id":0,""" +
                    """"billable":false,"rate":null,"currency":null,"estimated_hours":null}""",
                """PUT /tokkl/api/v9/workspaces/7/projects/5 {"name":"Website","color":"#0b83d9","client_id":9,""" +
                    """"billable":true,"rate":12.5,"currency":"USD","estimated_hours":40.0}""",
                """PUT /tokkl/api/v9/workspaces/7/projects/5 {"active":false}""",
                "GET /tokkl/api/v9/workspaces/7/projects/5/tasks",
                """POST /tokkl/api/v9/workspaces/7/projects/5/tasks {"name":"Copy"}""",
                """PUT /tokkl/api/v9/workspaces/7/projects/5/tasks/1 {"name":"Wireframes","active":false}""",
                "DELETE /tokkl/api/v9/workspaces/7/projects/5/tasks/1",
                "GET /tokkl/api/v9/workspaces/7/clients?status=both",
                """POST /tokkl/api/v9/workspaces/7/clients {"name":"Surf school","notes":null}""",
                """PUT /tokkl/api/v9/workspaces/7/clients/1 {"name":"Acme","notes":"Pays late"}""",
                "POST /tokkl/api/v9/workspaces/7/clients/1/archive {}",
                """POST /tokkl/api/v9/workspaces/7/clients/1/restore {"restore_all_projects":true}""",
                "GET /tokkl/api/v9/workspaces/7/tags",
                """POST /tokkl/api/v9/workspaces/7/tags {"name":"Deep work"}""",
                """PUT /tokkl/api/v9/workspaces/7/tags/3 {"name":"Focus"}""",
                "DELETE /tokkl/api/v9/workspaces/7/tags/3",
            ),
            requests,
        )
    }

    @Test
    fun reportRequests() = runTest {
        val from = LocalDate.parse("2026-10-21")
        val to = LocalDate.parse("2026-10-27")
        val filter = ReportFilter(
            projectIds = listOf(null, 8),
            excludeProjects = true,
            clientIds = listOf(9, null),
            tagIds = listOf(10, null),
            excludeTags = true,
            billable = true,
        )
        api.reports.appSummary(AppSummaryBody(from, to, "projects", "projects", filter))
        api.reports.search(7, DetailedSearchBody(from, to, 1, 15, "date", "DESC", 50, 1, ReportFilter()), JSON)
        val clients = ReportFilter(clientIds = listOf(5), billable = false)
        api.reports.weekly(7, WeeklyReportBody(from, to, 0, 0, clients), JSON)
        val csv = ReportCsvBody(from, to, filter, 1, 15, "classic", grouping = "projects", subGrouping = "projects")
        api.reports.exportCsv("summary", csv, ByteArrayOutputStream(), MAX_BYTES, ApiCallCancellation())
        val detailed =
            csv.copy(filter = ReportFilter(), grouping = null, subGrouping = null, sort = "date", dir = "desc")
        api.reports.exportCsv("detailed", detailed, ByteArrayOutputStream(), MAX_BYTES, ApiCallCancellation())

        val dates = """"start_date":"2026-10-21","end_date":"2026-10-27""""
        val filtered = """"exclude_project_ids":[null,8],"client_ids":[9,null],"exclude_tag_ids":[10,null],""" +
            """"billable":true"""
        val rounded = """"rounding":1,"rounding_minutes":15"""
        assertEquals(
            listOf(
                """POST /tokkl/api/app/report {$dates,"grouping":"projects","sub_grouping":"projects",$filtered}""",
                """POST /tokkl/reports/api/v3/workspace/7/search/time_entries {$dates,$rounded,"order_by":"date",""" +
                    """"order_dir":"DESC","page_size":50,"first_row_number":1}""",
                """POST /tokkl/reports/api/v3/workspace/7/weekly/time_entries {$dates,"rounding":0,""" +
                    """"rounding_minutes":0,"client_ids":[5],"billable":false}""",
                """POST /tokkl/api/app/report/summary.csv {$dates,$filtered,$rounded,"duration_format":"classic",""" +
                    """"grouping":"projects","sub_grouping":"projects"}""",
                """POST /tokkl/api/app/report/detailed.csv {$dates,$rounded,"duration_format":"classic",""" +
                    """"sort":"date","dir":"desc"}""",
            ),
            requests,
        )
    }

    /** An "Is not" with nothing picked sends nothing, as in the web app; "Is" lists keep their order and nulls. */
    @Test
    fun reportFiltersSendOnlyTheListsInUse() = runTest {
        val from = LocalDate.parse("2026-10-21")
        val week = { filter: ReportFilter -> WeeklyReportBody(from, from.plusDays(6), 0, 0, filter) }
        val nothingPicked = ReportFilter(excludeProjects = true, excludeClients = true, excludeTags = true)
        api.reports.weekly(7, week(nothingPicked), JSON)
        val picked = ReportFilter(projectIds = listOf(null, 9), tagIds = listOf(4, 2), billable = false)
        api.reports.weekly(7, week(picked), JSON)
        api.reports.weekly(
            7,
            week(ReportFilter(listOf(9), excludeProjects = true, listOf(null, 5), excludeClients = true, listOf(4))),
            JSON,
        )

        val head = """POST /tokkl/reports/api/v3/workspace/7/weekly/time_entries {"start_date":"2026-10-21",""" +
            """"end_date":"2026-10-27","rounding":0,"rounding_minutes":0"""
        assertEquals(
            listOf(
                "$head}",
                """$head,"project_ids":[null,9],"tag_ids":[4,2],"billable":false}""",
                """$head,"exclude_project_ids":[9],"exclude_client_ids":[null,5],"tag_ids":[4]}""",
            ),
            requests,
        )
    }

    @Test
    fun timerRequests() = runTest {
        api.timer.state()
        api.timer.events(ApiCallCancellation()) { it.readBytes() }
        api.timer.pause()
        api.timer.resume(7)
        api.timer.discard(7)
        api.timer.startSession(
            SessionStartBody(7, "Tokkl for Android", "Deep work", 3, null, listOf("focus"), true, 40, 8, 20, 5),
        )
        api.timer.nextPhase(8)
        api.timer.endSession(9)

        assertEquals(
            listOf(
                "GET /tokkl/api/app/timer",
                "GET /tokkl/api/app/timer/events",
                "POST /tokkl/api/app/timer/pause {}",
                """POST /tokkl/api/app/timer/resume {"revision":7}""",
                """POST /tokkl/api/app/timer/discard {"revision":7}""",
                """POST /tokkl/api/app/pomodoro {"workspace_id":7,"created_with":"Tokkl for Android",""" +
                    """"description":"Deep work","project_id":3,"task_id":null,"tags":["focus"],"billable":true,""" +
                    """"work_minutes":40,"short_rest_minutes":8,"long_rest_minutes":20,"long_rest_every":5}""",
                """POST /tokkl/api/app/pomodoro/next {"revision":8}""",
                """POST /tokkl/api/app/pomodoro/end {"revision":9}""",
            ),
            requests,
        )
    }

    private companion object {
        val JSON = JsonElement.serializer()

        const val MAX_BYTES = 1_000L
    }
}
