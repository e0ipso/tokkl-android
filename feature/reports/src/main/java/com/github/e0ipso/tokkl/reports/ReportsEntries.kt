package com.github.e0ipso.tokkl.reports

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
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

/** Reports. Its view model is the signed-in session's, so the report keeps its filters after the page closes. */
internal object ReportsEntries : FeatureEntries {
    override val pages get() = listOf(reportsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(ReportsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(ReportsRoute) { ReportsPage(model = koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)) }
    }
}

/**
 * The Reports page from its view model: hands its state and events to [ReportsScreen].
 */
@Composable
fun ReportsPage(model: ReportsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.reports_share)
    val page by model.page.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The chooser may return RESULT_CANCELED even after a selection. Files stay bounded
        // and readable for receivers that open their stream after the chooser has closed.
        model.exportShareFinished()
    }
    LaunchedEffect(page.exportState, lifecycleState) {
        if (page.exportState is ReportExportState.Ready && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            model.launchExportShare { file ->
                try {
                    val send = ReportCsvShare.intent(context, file)
                    if (ReportCsvShare.hasTarget(context, send)) {
                        chooser.launch(Intent.createChooser(send, shareTitle))
                    } else {
                        model.exportNoShareTarget()
                    }
                } catch (_: ActivityNotFoundException) {
                    model.exportNoShareTarget()
                } catch (_: IllegalArgumentException) {
                    model.exportShareFailed()
                } catch (_: SecurityException) {
                    model.exportShareFailed()
                }
            }
        }
    }
    ReportsScreen(
        state = page.report,
        tab = page.tab,
        settings = page.settings,
        time = page.time,
        filters = page.filters,
        filterOptions = page.filterOptions,
        onTab = model::selectTab,
        onShift = model::shift,
        onPreset = model::select,
        onCustom = model::selectCustom,
        onFilters = model::applyFilters,
        onRetry = model::retry,
        onLoadMore = model::loadMore,
        exportSupported = page.exportSupported,
        exportState = page.exportState,
        onExport = model::exportCsv,
        modifier = modifier,
    )
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
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Reports")
data object ReportsRoute : Route
