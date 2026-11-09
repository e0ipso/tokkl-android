package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.session.SignedInApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The Reports API calls behind the reports page, as [ReportsServer] makes them. */
class ReportsRepository(client: ApiClientProvider) {

    private val api = SignedInApi(client)

    /** The account's default workspace, with its rounding. */
    suspend fun workspace() = api.call { ReportsServer(it).workspace() }

    suspend fun summary(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        filters: ReportFilters,
    ) = api.call { ReportsServer(it).summary(workspace, range, zone, firstDayOfWeek, filters) }

    suspend fun detailedPage(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstRow: Int,
        filters: ReportFilters,
    ) = api.call { ReportsServer(it).detailedPage(workspace, range, zone, firstRow, filters) }

    suspend fun weekly(workspace: ReportWorkspace, weekStart: LocalDate, filters: ReportFilters) =
        api.call { ReportsServer(it).weekly(workspace, weekStart, filters) }
}
