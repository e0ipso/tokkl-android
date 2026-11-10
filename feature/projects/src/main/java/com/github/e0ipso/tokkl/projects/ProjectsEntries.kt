package com.github.e0ipso.tokkl.projects

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.projects.R
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

/** The Projects page, with a view model of its own that goes when the page closes. */
internal object ProjectsEntries : FeatureRoutes {
    override val pages get() = listOf(projectsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(ProjectsRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(ProjectsRoute) { ProjectsPage(model = koinViewModel()) }
    }
}

@Composable
fun ProjectsPage(model: ProjectsViewModel, modifier: Modifier = Modifier) {
    val page by model.page.collectAsStateWithLifecycle()
    val state = page.catalog
    BackHandler(enabled = state.overlay != null) { model.closeForm() }
    ProjectsScreen(
        page = page,
        actions = ProjectsActions(
            onRetry = model::retry,
            onNew = model::newProject,
            onEdit = model::edit,
            onSetActive = model::setActive,
            onSave = model::save,
            onDraftChange = model::editDraft,
            onCloseForm = model::closeForm,
            onDoneShow = model::doneShown,
        ),
        taskActions = TaskActions(
            onRetry = model.tasks::retry,
            onCreate = model.tasks::create,
            onAskToEdit = model.tasks::askToEdit,
            onSave = model.tasks::save,
            onAskToDelete = model.tasks::askToDelete,
            onDelete = model.tasks::delete,
            onDismiss = model.tasks::dismiss,
            onDoneShow = model.tasks::doneShown,
        ),
        modifier = modifier,
    )
}

/** The Projects page, in the bar after Pomodoro. */
val projectsPage = TopLevelDestination(
    route = ProjectsRoute,
    label = R.string.nav_projects,
    icon = UiR.drawable.ic_nav_projects,
    hue = SectionHues.projects,
    position = 30,
)

@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Projects")
data object ProjectsRoute : Route {
    override val contentKey: String get() = "ProjectsRoute"
}
