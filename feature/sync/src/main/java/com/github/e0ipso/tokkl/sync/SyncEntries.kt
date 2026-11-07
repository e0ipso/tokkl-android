package com.github.e0ipso.tokkl.sync

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import org.koin.compose.viewmodel.koinViewModel

/** The changes the server refused. Editing one opens the entry form on it. */
internal object SyncEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val navigator = host.navigator
        route(Route.NeedsAttention) {
            NeedsAttentionScreen(
                model = koinViewModel(),
                onEdit = { entryId -> navigator.openEntry(EntryOpening.Edit(entryId)) },
                onBack = { navigator.close(Route.NeedsAttention) },
            )
        }
    }
}
