package com.github.e0ipso.tokkl.tags

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.tags.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The Tags page, with a view model of its own that goes when the page closes. */
internal object TagsEntries : FeatureEntries {
    override val pages get() = listOf(tagsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(TagsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(TagsRoute) { TagsScreen(model = koinViewModel()) }
    }
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
data object TagsRoute : Route
