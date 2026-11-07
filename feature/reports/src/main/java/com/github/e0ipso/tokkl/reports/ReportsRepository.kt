package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.catalog.SignedInApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The Reports API calls behind the reports page, as [ReportsServer] makes them. */
interface ReportsRepository {

    /** The account's default workspace, with its rounding. */
    suspend fun workspace(): ApiResult<ReportWorkspace>

    suspend fun summary(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        filters: ReportFilters,
    ): ApiResult<SummaryReport>

    suspend fun detailedPage(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstRow: Int,
        filters: ReportFilters,
    ): ApiResult<DetailedPage>

    suspend fun weekly(
        workspace: ReportWorkspace,
        weekStart: LocalDate,
        filters: ReportFilters,
    ): ApiResult<List<WeeklyRow>>
}

/** [ReportsRepository] through the signed-in account's [client] and [ReportsServer]. */
class ServerReportsRepository(client: ApiClientProvider) : ReportsRepository {

    private val api = SignedInApi(client)

    override suspend fun workspace() = api.call { ReportsServer(it).workspace() }

    override suspend fun summary(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        filters: ReportFilters,
    ) = api.call { ReportsServer(it).summary(workspace, range, zone, firstDayOfWeek, filters) }

    override suspend fun detailedPage(
        workspace: ReportWorkspace,
        range: ReportRange,
        zone: ZoneId,
        firstRow: Int,
        filters: ReportFilters,
    ) = api.call { ReportsServer(it).detailedPage(workspace, range, zone, firstRow, filters) }

    override suspend fun weekly(workspace: ReportWorkspace, weekStart: LocalDate, filters: ReportFilters) =
        api.call { ReportsServer(it).weekly(workspace, weekStart, filters) }
}
