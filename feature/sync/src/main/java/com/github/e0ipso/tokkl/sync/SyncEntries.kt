package com.github.e0ipso.tokkl.sync

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

/** The changes the server refused. Editing one opens the entry form on it. */
internal object SyncEntries : FeatureEntries {
    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(NeedsAttentionRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val navigator = host.navigator
        route(NeedsAttentionRoute) {
            NeedsAttentionScreen(
                model = koinViewModel(),
                onEdit = { entryId -> navigator.openEntry(EntryOpening.Edit(entryId)) },
                onBack = { navigator.close(NeedsAttentionRoute) },
            )
        }
    }
}

/** The changes the server refused. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.NeedsAttention")
data object NeedsAttentionRoute : Route

/** Opens the changes the server refused above the screen on top, unless they show already. */
fun Navigator.openNeedsAttention() {
    if (top != NeedsAttentionRoute) push(NeedsAttentionRoute)
}
