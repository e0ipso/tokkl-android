package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.WorkspaceId

/**
 * The server behind a page that manages one kind of the account's items: which workspace the page
 * manages, and what it lists there. Each feature's repository adds the changes its page sends.
 */
interface CatalogRepository<D> {

    /** The workspace the page manages: [stored] from the account settings, or the server's default. */
    suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId>

    /** What the page lists in [workspace]. */
    suspend fun load(workspace: WorkspaceId): ApiResult<D>
}
