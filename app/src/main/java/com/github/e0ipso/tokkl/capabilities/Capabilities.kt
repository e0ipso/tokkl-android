package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.reports.ReportCsvKind
import com.github.e0ipso.tokkl.session.Me
import kotlinx.serialization.json.JsonArray

/**
 * The Tokkl-only features the server offers (docs/PRD.md section 8.3). Features read these flags
 * and hide what the server lacks, as on Toggl Track or an older Tokkl. The app never compares
 * server versions.
 *
 * @property trash `GET /api/app/trash` answers with a list.
 * @property restore undo of a delete, `POST /api/app/time_entries/{id}/restore`. It lives in the
 *   same router as the trash, so the trash probe decides it too, and nothing probes it.
 * @property favorites `GET /api/v9/me` carries Tokkl's `role`. On Tokkl a missing `favorites` key
 *   in `/me/preferences` means no timer templates yet, not missing support. Tokkl's other preference
 *   keys that the app reads and writes, such as `timerView`, follow this flag too.
 * @property timeResolution `GET /api/v9/me` carries `time_resolution_minutes`.
 * @property negatedFilters report requests take `exclude_project_ids`, `exclude_client_ids` and
 *   `exclude_tag_ids`, the web app's "Is not" filters. Toggl Track's Reports API v3 has no
 *   negation, and every Tokkl release takes them, so Tokkl's `role` in `GET /api/v9/me` decides it.
 * @property summaryCsv Share on Summary. Null until a refresh. True when `/me` has Tokkl's `role`,
 *   until `POST /api/app/report/summary.csv` returns 404 or 410. False on Toggl Track and after that.
 * @property detailedCsv the same for Detailed and `POST /api/app/report/detailed.csv`.
 */
data class Capabilities(
    val trash: Boolean,
    val restore: Boolean,
    val favorites: Boolean,
    val timeResolution: Boolean,
    val negatedFilters: Boolean,
    val summaryCsv: Boolean? = null,
    val detailedCsv: Boolean? = null,
) {

    /**
     * The flags from [me] and the [trash] probe. A probe that got no clear answer keeps the
     * trash and restore flags as they were. CSV routes are POST only, so refresh does not send
     * OPTIONS: that 404 is not absence. Tokkl offers Share until a POST proves one kind is gone,
     * and a recorded false stays false across later refreshes.
     */
    fun supports(kind: ReportCsvKind): Boolean = when (kind) {
        ReportCsvKind.SUMMARY -> summaryCsv == true
        ReportCsvKind.DETAILED -> detailedCsv == true
    }

    fun updatedWith(me: Me, trash: TrashProbe): Capabilities {
        val hasTrash = when (trash) {
            TrashProbe.PRESENT -> true
            TrashProbe.ABSENT -> false
            TrashProbe.UNKNOWN -> this.trash
        }
        val tokkl = me.role != null
        return Capabilities(
            trash = hasTrash,
            restore = if (trash == TrashProbe.UNKNOWN) restore else hasTrash,
            favorites = tokkl,
            timeResolution = me.timeResolutionMinutes != null,
            negatedFilters = tokkl,
            summaryCsv = csvOffered(tokkl, summaryCsv),
            detailedCsv = csvOffered(tokkl, detailedCsv),
        )
    }

    /**
     * [previous] false is a POST 404 or 410, or Toggl Track, and stays false. Null and true become
     * true on Tokkl. Sign-out forgets the flags, so the next account is judged again.
     */
    private fun csvOffered(tokkl: Boolean, previous: Boolean?): Boolean? = when {
        !tokkl -> false
        previous == false -> false
        else -> true
    }

    companion object {
        /** Before the first refresh: every Tokkl-only feature hidden. */
        val NONE = Capabilities(trash = false, restore = false, favorites = false, timeResolution = false, negatedFilters = false)
    }
}

/** What `GET /api/app/trash` said about the trash. */
enum class TrashProbe {
    /** It answered with a list. */
    PRESENT,

    /** 404, or a 2xx that is not a list, so the server has no trash the app can read. */
    ABSENT,

    /** No clear answer: no network, a refused trust check, 403, 429 or another status. */
    UNKNOWN,
}

/** Sends `GET /api/app/trash` once. Blocks, so call it off the main thread. */
fun probeTrash(api: TogglApiClient): TrashProbe =
    when (val result = api.get(api.server.app("trash"), JsonArray.serializer())) {
        is ApiResult.Success -> TrashProbe.PRESENT
        is ApiResult.Failure -> when (val error = result.error) {
            is ApiError.UnexpectedStatus -> if (error.status == 404) TrashProbe.ABSENT else TrashProbe.UNKNOWN
            is ApiError.InvalidResponse -> TrashProbe.ABSENT
            else -> TrashProbe.UNKNOWN
        }
    }
