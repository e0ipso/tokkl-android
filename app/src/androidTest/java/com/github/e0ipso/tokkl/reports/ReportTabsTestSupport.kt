package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * An account of its own for the Detailed and Weekly tabs, in Kiritimati, UTC+14, where the date is
 * often a day ahead of the phone's UTC date. Its weeks start on Wednesday, its workspace rounds
 * report durations up to 15 minutes, and it writes clock times on the 12-hour clock.
 */
internal class ReportAccount private constructor(val account: TestAccounts.Account) {

    val zone: ZoneId = ZoneId.of(ZONE)
    val today: LocalDate = LocalDate.now(zone)
    val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))

    private val api get() = account.client

    /** A project of [client] or none, whose entries are billable by default when [billable]. */
    fun project(name: String, color: String, client: Long? = null, billable: Boolean = false): Long {
        val body = buildJsonObject {
            put("name", name)
            put("color", color)
            if (client != null) put("client_id", client)
            if (billable) put("billable", true)
        }
        return create("projects", body)
    }

    fun client(name: String): Long = create("clients", buildJsonObject { put("name", name) })

    fun tag(name: String): Long = create("tags", buildJsonObject { put("name", name) })

    /**
     * An entry of [seconds] from [start] in the account's zone, with [project] or none and [tags].
     * [billable] null leaves it to the project, as the web app does.
     */
    fun entry(
        start: LocalDateTime,
        seconds: Int,
        description: String,
        project: Long?,
        tags: List<Long> = emptyList(),
        billable: Boolean? = null,
    ) {
        val body = buildJsonObject {
            put("workspace_id", account.workspaceId)
            put("created_with", "tokkl-android tests")
            put("description", description)
            put("start", start.atZone(zone).toInstant().toString())
            put("duration", seconds)
            if (project != null) put("project_id", project)
            if (tags.isNotEmpty()) putJsonArray("tag_ids") { tags.forEach { add(it) } }
            if (billable != null) put("billable", billable)
        }
        api.send("POST", api.server.api("workspaces", account.workspaceId.toString(), "time_entries"), body, JsonObject.serializer())
            .orFail("POST /api/v9/workspaces/<id>/time_entries")
    }

    /** POSTs [body] to `/api/v9/workspaces/<id>/<what>` and returns the new item's id. */
    private fun create(what: String, body: JsonObject): Long =
        api.send("POST", api.server.api("workspaces", account.workspaceId.toString(), what), body, JsonObject.serializer())
            .orFail("POST /api/v9/workspaces/<id>/$what")
            .getValue("id").jsonPrimitive.long

    /** POSTs [body] to Tokkl's own report endpoint `/api/app/report/<path>`, which the web app reads. */
    fun webReport(path: String, body: JsonObject): JsonObject =
        api.send("POST", TokklTestServer.url.app("report", path), body, JsonObject.serializer()).orFail("POST /api/app/report/$path")

    companion object {
        const val ZONE = "Pacific/Kiritimati"

        /** Creates the account. A test that gets one deletes it with [TestAccounts.delete]. */
        fun create(): ReportAccount {
            val account = TestAccounts.create(timezone = ZONE)
            try {
                val api = account.client
                api.send("PUT", api.server.api("me"), buildJsonObject { put("beginning_of_week", 3) }, JsonObject.serializer())
                    .orFail("PUT /api/v9/me")
                val rounding = buildJsonObject {
                    put("rounding", 1)
                    put("rounding_minutes", 15)
                }
                api.send("PUT", api.server.api("workspaces", account.workspaceId.toString()), rounding, JsonObject.serializer())
                    .orFail("PUT /api/v9/workspaces/<id>")
                api.send("POST", api.server.api("me", "preferences"), buildJsonObject { put("timeofday_format", "h:mm A") }, JsonObject.serializer())
                    .orFail("POST /api/v9/me/preferences")
            } catch (e: Throwable) {
                TestAccounts.delete(account)
                throw e
            }
            return ReportAccount(account)
        }
    }
}

/** A test that runs at Android's largest font scale, with [LargestFontScaleWhenMarked]. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class AtLargestFontScale

/** Sets the largest font scale around a test marked [AtLargestFontScale]. Order it before the compose rule. */
internal class LargestFontScaleWhenMarked : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        if (description.getAnnotation(AtLargestFontScale::class.java) == null) base else SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true).apply(base, description)
}
