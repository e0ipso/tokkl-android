package com.github.e0ipso.tokkl.favorites

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.FavoriteBody
import com.github.e0ipso.tokkl.api.FavoritesChange
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.session.AccountContext
import com.github.e0ipso.tokkl.session.CurrentAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/** The most favorites Tokkl keeps: `MAX_FAVORITES` in ../tokkl/packages/api/src/models/users.ts. */
const val MAX_FAVORITES = 9

/**
 * Saves the account's favorites on the server, then in the cache. Editing the list needs the
 * server: nothing is queued, and a failure leaves the server and the cache as they were.
 *
 * @param client the signed-in account's API client, or null when signed out.
 * @param account the account each save belongs to: the one signed in when it starts.
 * @param replaceFavorites replaces the cached favorites while the save's account is current, as
 *   `AccountCache.replaceFavorites` does, and returns false once that account has ended.
 * @param scope the application's scope, where each save runs.
 */
class FavoritesServer(
    private val client: ApiClientProvider,
    private val account: CurrentAccount,
    private val replaceFavorites: suspend (AccountContext, List<Favorite>) -> Boolean,
    private val scope: CoroutineScope,
) : FavoritesStore {

    /**
     * Posts [favorites], in order, to `POST /api/v9/me/preferences`, and once the server has
     * stored them, puts them in the cache. The save runs in the application's scope, so it reaches
     * the server and the cache also when its caller goes meanwhile. It belongs to the account
     * signed in when it starts: once that account has ended it sends nothing and writes nothing,
     * and sign-out waits for a cache write on its way ([replaceFavorites]). A save
     * whose account ended fails as a signed-out one does.
     */
    override suspend fun save(favorites: List<EntryFields>): ApiResult<Unit> {
        val context = account.context.value
        return scope.async {
            val api = client().takeIf { context.isCurrent } ?: return@async SIGNED_OUT
            api.account.savePreferences(favoritesBody(favorites)).flatMap {
                val rows = favorites.mapIndexed { position, fields -> fields.toFavorite(position) }
                if (replaceFavorites(context, rows)) ApiResult.Success(Unit) else SIGNED_OUT
            }
        }.await()
    }

    private companion object {
        val SIGNED_OUT = ApiResult.Failure(ApiError.AuthFailed)
    }
}

/**
 * Stores the favorites, in order, once the server has them. [FavoritesServer] posts them; the
 * favorites page's unit tests answer for the server.
 */
fun interface FavoritesStore {
    suspend fun save(favorites: List<EntryFields>): ApiResult<Unit>
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
