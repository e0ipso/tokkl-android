package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.catalog.PageVisitEffect
import com.github.e0ipso.tokkl.feature.trash.R
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
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The Trash page, with a view model of its own that goes when the page closes. */
internal object TrashEntries : FeatureEntries {
    override val pages get() = listOf(trashPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(TrashRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(TrashRoute) { TrashPage(model = koinViewModel()) }
    }
}

/**
 * The Trash page from its view model: tells the model when the page opens and closes, and hands
 * its state and events to [TrashScreen].
 */
@Composable
fun TrashPage(model: TrashViewModel, modifier: Modifier = Modifier) {
    PageVisitEffect(onOpen = model::open, onClose = model::close)
    val page by model.page.collectAsStateWithLifecycle()
    TrashScreen(
        state = page.trash,
        offline = page.offline,
        settings = page.settings,
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

/** The Trash page, last on the More sheet. */
val trashPage = TopLevelDestination(
    route = TrashRoute,
    label = R.string.nav_trash,
    icon = UiR.drawable.ic_nav_trash,
    hue = SectionHues.more,
    position = 70,
    inMore = true,
)

/** The Trash page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Trash")
data object TrashRoute : Route
