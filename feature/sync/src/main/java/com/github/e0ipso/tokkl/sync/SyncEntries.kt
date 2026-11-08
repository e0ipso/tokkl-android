package com.github.e0ipso.tokkl.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

/**
 * The changes the server refused, whose editing opens the entry form, and what the signed-in frame
 * shows around every page: the sync status band while changes wait to reach the server
 * (docs/PRD.md O5, O6), whose buttons open the refused changes and, after a refused token,
 * sign-in again for the account, and the notice that says how many changes were discarded.
 */
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

    @Composable
    override fun frameBand(host: NavigationHost): (@Composable () -> Unit)? {
        val model = koinViewModel<SyncFrameViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
        val frame by model.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val status = frame.status ?: return null
        val navigator = host.navigator
        return {
            SyncStatusBand(
                status,
                onOpenNeedsAttention = navigator::openNeedsAttention,
                onSignInAgain = { scope.launch { model.signInAgain()?.let(navigator::push) } },
            )
        }
    }

    @Composable
    override fun frameNotice(host: NavigationHost): (@Composable (Modifier) -> Unit)? {
        val model = koinViewModel<SyncFrameViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
        val frame by model.state.collectAsStateWithLifecycle()
        val count = frame.discardedCount
        if (count <= 0) return null
        return { modifier -> DiscardNotice(count, onDismiss = model::dismissDiscarded, modifier = modifier) }
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
