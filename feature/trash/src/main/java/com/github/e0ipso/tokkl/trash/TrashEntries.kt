package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.trash.R
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

/** The Trash page, with a view model of its own that goes when the page closes. */
internal object TrashEntries : FeatureRoutes {
    override val pages get() = listOf(trashPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(TrashRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(TrashRoute) { TrashPage(model = koinViewModel()) }
    }
}

@Composable
fun TrashPage(model: TrashViewModel, modifier: Modifier = Modifier) {
    val page by model.page.collectAsStateWithLifecycle()
    TrashScreen(
        page = page,
        actions = TrashActions(
            onRetry = model::retry,
            onRestore = model::restore,
            onPurge = model::purge,
            onAskToEmpty = model::askToEmpty,
            onEmpty = model::empty,
            onDismiss = model::dismiss,
            onDoneShow = model::doneShown,
        ),
        modifier = modifier,
    )
}

/** The Trash page, last on the More sheet, while the server has the trash API. */
val trashPage = TopLevelDestination(
    route = TrashRoute,
    label = R.string.nav_trash,
    icon = UiR.drawable.ic_nav_trash,
    hue = SectionHues.more,
    position = 70,
    inMore = true,
    isAvailable = { it.trash },
)

@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Trash")
data object TrashRoute : Route
