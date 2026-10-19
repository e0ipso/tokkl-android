package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
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
 *   in `/me/preferences` means no favorites yet, not missing support. Tokkl's other preference
 *   keys that the app reads and writes, such as `timerView`, follow this flag too.
 * @property timeResolution `GET /api/v9/me` carries `time_resolution_minutes`.
 */
data class Capabilities(
    val trash: Boolean,
    val restore: Boolean,
    val favorites: Boolean,
    val timeResolution: Boolean,
) {

    /**
     * The flags from [me] and the [trash] probe. A probe that got no clear answer keeps the
     * trash and restore flags as they were.
     */
    fun updatedWith(me: Me, trash: TrashProbe): Capabilities {
        val hasTrash = when (trash) {
            TrashProbe.PRESENT -> true
            TrashProbe.ABSENT -> false
            TrashProbe.UNKNOWN -> this.trash
        }
        return Capabilities(
            trash = hasTrash,
            restore = if (trash == TrashProbe.UNKNOWN) restore else hasTrash,
            favorites = me.role != null,
            timeResolution = me.timeResolutionMinutes != null,
        )
    }

    companion object {
        /** Before the first refresh: every Tokkl-only feature hidden. */
        val NONE = Capabilities(trash = false, restore = false, favorites = false, timeResolution = false)
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
