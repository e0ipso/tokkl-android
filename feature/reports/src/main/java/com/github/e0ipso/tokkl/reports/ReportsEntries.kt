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
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.RequestOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Reports. Its view model is the route's own, and its tab and filters ([ReportChoices]) the
 * signed-in session's, so they stay after the page closes while the range starts over.
 */
internal object ReportsEntries : FeatureRoutes {
    override val pages get() = listOf(reportsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(ReportsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(ReportsRoute) {
            val choices = koinViewModel<ReportChoices>(viewModelStoreOwner = LocalSignedInSession.current)
            ReportsPage(model = koinViewModel { parametersOf(choices) })
        }
    }
}

/** The Timer widget's day report, which opens Reports on today's summary. */
internal object ReportsRequests : FeatureRequests {
    override val linkExtras get() = listOf(TODAY_REPORT_TAP)

    override fun readLinks(intent: LinkIntent): List<NavigationRequest> =
        listOfNotNull(TodayReportTap.takeIf { intent.flag(TODAY_REPORT_TAP) })

    override fun PolymorphicModuleBuilder<NavigationRequest>.registerRequests() {
        subclass(TodayReportTap::class)
    }

    /**
     * The widget's day report opens Reports on today's summary, with no filters, when somebody is
     * signed in, and sign-in otherwise.
     */
    @Composable
    override fun OpenRequest(request: NavigationRequest, opening: RequestOpening) {
        if (request != TodayReportTap) return
        val choices = koinViewModel<ReportChoices>(viewModelStoreOwner = LocalSignedInSession.current)
        LaunchedEffect(request) {
            opening.openSignedIn(ReportsRoute, before = choices::openToday)
            opening.handled(request)
        }
    }
}

/**
 * The extra of the Timer widget's day report tap, which the widget puts in its intent
 * (`WidgetNavigation.OPEN_TODAY` in the widget feature). The app's DeepLinksTest checks that the
 * two agree.
 */
internal const val TODAY_REPORT_TAP = "open_today_widget"

/** The Timer widget's day report tap. */
@Serializable
data object TodayReportTap : NavigationRequest

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
        model.onExportShare(ExportShareEvent.Finished)
    }
    LaunchedEffect(page.exportState, lifecycleState) {
        if (page.exportState is ReportExportState.Ready && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            val launch = ExportShareEvent.Launch { file ->
                try {
                    val send = ReportCsvShare.intent(context, file)
                    if (ReportCsvShare.hasTarget(context, send)) {
                        chooser.launch(Intent.createChooser(send, shareTitle))
                    } else {
                        model.onExportShare(ExportShareEvent.NoTarget)
                    }
                } catch (_: ActivityNotFoundException) {
                    model.onExportShare(ExportShareEvent.NoTarget)
                } catch (_: IllegalArgumentException) {
                    model.onExportShare(ExportShareEvent.Failed)
                } catch (_: SecurityException) {
                    model.onExportShare(ExportShareEvent.Failed)
                }
            }
            model.onExportShare(launch)
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
