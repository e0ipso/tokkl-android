package com.github.e0ipso.tokkl.favorites

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.FavoriteBody
import com.github.e0ipso.tokkl.api.FavoritesChange
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Favorite
import kotlinx.serialization.json.JsonElement

/** The most favorites Tokkl keeps: `MAX_FAVORITES` in ../tokkl/packages/api/src/models/users.ts. */
const val MAX_FAVORITES = 9

/**
 * Saves the account's favorites on the server, then in [cache]. Editing the list needs the
 * server: nothing is queued, and a failure leaves the server and the cache as they were.
 *
 * @param client the signed-in account's API client, or null when signed out.
 */
class FavoritesServer(private val client: ApiClientProvider, private val cache: AccountCache) {

    /**
     * Posts [favorites], in order, to `POST /api/v9/me/preferences`, and once the server has
     * stored them, puts them in the cache. Blocks on the disk, so call it on the IO dispatcher.
     */
    suspend fun save(favorites: List<EntryFields>): ApiResult<Unit> {
        val api = client() ?: return ApiResult.Failure(ApiError.AuthFailed)
        return when (val answer = api.account.savePreferences(favoritesBody(favorites), JsonElement.serializer())) {
            is ApiResult.Failure -> answer

            is ApiResult.Success -> {
                cache.replaceFavorites(favorites.mapIndexed { position, fields -> fields.toFavorite(position) })
                ApiResult.Success(Unit)
            }
        }
    }
}

/**
 * The preferences change that stores [favorites]: the `favorites` key alone, in the web app's
 * format, the `Favorite` type in ../tokkl/packages/types/index.ts that TimerPage.tsx and
 * EntryList.tsx in ../tokkl/packages/web/src save through `useSavePreferences`. Each favorite has
 * `description`, `project_id` and `task_id`, null when unset, `tags` as names and `billable`.
 * Tokkl merges the keys it gets into the stored preferences (`savePreferences` in
 * ../tokkl/packages/api/src/routes/togglV9.ts), so every other key stays as it was.
 */
internal fun favoritesBody(favorites: List<EntryFields>) = FavoritesChange(
    favorites.map { FavoriteBody(it.description, it.projectId?.value, it.taskId?.value, it.tags, it.billable) },
)

private fun EntryFields.toFavorite(position: Int) = Favorite(
    position = position,
    description = description,
    projectId = projectId,
    taskId = taskId,
    tags = tags,
    billable = billable,
)
