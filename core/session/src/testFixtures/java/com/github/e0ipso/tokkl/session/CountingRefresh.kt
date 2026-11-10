package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.time.DayOfWeek

/** Counts the refreshes asked for, each done at once. */
class CountingRefresh : CacheRefresh {
    var refreshes = 0
        private set
    var afterChanges = 0
        private set

    override fun refresh(): Job = done().also { refreshes++ }

    override fun refreshAfterChange(): Job = done().also { afterChanges++ }

    private fun done(): CompletableJob = Job().apply { complete() }
}

/** The server's [capabilities], loaded at once from memory. */
fun capabilitiesOf(capabilities: Capabilities): CapabilitiesSource =
    CapabilitiesSource(InMemoryValueStore(capabilities), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

/** The account [settings], loaded at once from memory, or none. */
fun accountSettingsOf(settings: AccountSettings?): AccountSettingsSource =
    AccountSettingsSource(InMemoryValueStore(settings), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

/** Account settings in UTC from Monday whose default workspace is [workspace], loaded at once. */
fun accountSettingsIn(workspace: WorkspaceId): AccountSettingsSource =
    accountSettingsOf(AccountSettings("UTC", DayOfWeek.MONDAY, null, defaultWorkspaceId = workspace))
