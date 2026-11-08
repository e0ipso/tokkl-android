package com.github.e0ipso.tokkl.clients

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.clients.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

/** The Clients page, with a view model of its own that goes when the page closes. */
internal object ClientsEntries : FeatureEntries {
    override val pages get() = listOf(clientsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(ClientsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(ClientsRoute) { ClientsPage(model = koinViewModel()) }
    }
}

/**
 * The Clients page from its view model: hands its state and events to [ClientsScreen].
 */
@Composable
fun ClientsPage(model: ClientsViewModel, modifier: Modifier = Modifier) {
    val page by model.page.collectAsStateWithLifecycle()
    val state = page.catalog
    BackHandler(
        enabled = state.overlay is ClientsOverlay.New || state.overlay is ClientsOverlay.Edit,
    ) { model.dismiss() }
    ClientsScreen(
        state = state,
        offline = page.offline,
        cachedClients = page.cachedClients,
        actions = ClientsActions(
            onRetry = model::retry,
            onNew = model::newClient,
            onEdit = model::edit,
            onAskToArchive = model::askToArchive,
            onArchive = model::archive,
            onRestore = model::restore,
            onSave = model::save,
            onDismiss = model::dismiss,
            onDoneShow = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** The Clients page, on the More sheet after Timer templates. */
val clientsPage = TopLevelDestination(
    route = ClientsRoute,
    label = R.string.nav_clients,
    icon = R.drawable.ic_nav_clients,
    hue = SectionHues.more,
    position = 50,
    inMore = true,
)

/** The Clients page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Clients")
data object ClientsRoute : Route
