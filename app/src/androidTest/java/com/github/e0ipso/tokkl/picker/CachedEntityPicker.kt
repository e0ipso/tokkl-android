package com.github.e0ipso.tokkl.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.offlineIn

/**
 * [EntityPicker] over [cache]'s rows, saying so while [network] is offline, wired as the screens'
 * view models wire it.
 */
@Composable
fun CachedEntityPicker(
    cache: AccountCache,
    network: DefaultNetwork,
    workspaceId: Long,
    original: Picked,
    picked: Picked,
    onPickedChange: (Picked) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val source by remember(cache) {
        pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags)
    }.collectAsStateWithLifecycle(PickerSource())
    val offline by remember(network, scope) { network.offlineIn(scope) }.collectAsStateWithLifecycle()
    EntityPicker(source, offline, WorkspaceId(workspaceId), original, picked, onPickedChange, modifier)
}
