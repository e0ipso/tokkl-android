package com.github.e0ipso.tokkl.model

/**
 * The Tokkl-only features the server offers (docs/PRD.md section 8.3). Features read these flags
 * and hide what the server lacks, as on Toggl Track or an older Tokkl. The app never compares
 * server versions. Each refresh of the account settings updates them (`updatedWith` in
 * core:session's capabilities).
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
 * @property timerState the server owns Pomodoro sessions and pause chains, and `GET /api/app/timer`
 *   answers with a timer state. False on Toggl Track and on a Tokkl without the timer API. Pomodoro,
 *   Pause and the timer event stream follow this flag.
 * @property allDay Tokkl's no-time entries (`all_day`). Null until a refresh. True when `/me` has
 *   Tokkl's `role`, until a Tokkl time entry answer lacks the `all_day` key (Tokkl before 1.11.0).
 *   False on Toggl Track and after that. The All day row, the No times switch and every `all_day`
 *   key in a request follow it.
 */
data class Capabilities(
    val trash: Boolean,
    val restore: Boolean,
    val favorites: Boolean,
    val timeResolution: Boolean,
    val negatedFilters: Boolean,
    val summaryCsv: Boolean? = null,
    val detailedCsv: Boolean? = null,
    val timerState: Boolean = false,
    val allDay: Boolean? = null,
) {

    companion object {
        /** Before the first refresh: every Tokkl-only feature hidden. */
        val NONE = Capabilities(
            trash = false,
            restore = false,
            favorites = false,
            timeResolution = false,
            negatedFilters = false,
        )
    }
}
