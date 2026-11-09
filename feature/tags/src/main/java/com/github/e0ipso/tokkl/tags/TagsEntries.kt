package com.github.e0ipso.tokkl.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.tags.R
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
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
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The Tags page, with a view model of its own that goes when the page closes. */
internal object TagsEntries : FeatureRoutes {
    override val pages get() = listOf(tagsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(TagsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(TagsRoute) { TagsPage(model = koinViewModel()) }
    }
}

/**
 * The Tags page from its view model: hands its state and events to [TagsScreen].
 */
@Composable
fun TagsPage(model: TagsViewModel, modifier: Modifier = Modifier) {
    val page by model.page.collectAsStateWithLifecycle()
    TagsScreen(
        page = page,
        actions = TagsActions(
            onRetry = model::retry,
            onCreate = model::create,
            onAskToRename = model::askToRename,
            onRename = model::rename,
            onAskToDelete = model::askToDelete,
            onDelete = model::delete,
            onDismiss = model::dismiss,
            onDoneShow = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** The Tags page, on the More sheet after Clients. */
val tagsPage = TopLevelDestination(
    route = TagsRoute,
    label = R.string.nav_tags,
    icon = UiR.drawable.ic_nav_tags,
    hue = SectionHues.more,
    position = 60,
    inMore = true,
)

/** The Tags page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Tags")
data object TagsRoute : Route
