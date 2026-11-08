package com.github.e0ipso.tokkl.reports

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

/** Reports. Its view model is the signed-in session's, so the report keeps its filters after the page closes. */
internal object ReportsEntries : FeatureEntries {
    override val pages get() = listOf(reportsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(ReportsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(ReportsRoute) { ReportsScreen(model = koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)) }
    }
}

/** The Reports page, first on the More sheet. It keeps its own hue there. */
val reportsPage = TopLevelDestination(
    route = ReportsRoute,
    label = R.string.reports,
    icon = R.drawable.ic_nav_reports,
    hue = SectionHues.reports,
    position = 20,
    inMore = true,
)

/** The Reports page. */
@Serializable
data object ReportsRoute : Route
