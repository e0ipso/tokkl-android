package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the projects page, with sample projects.

@PreviewLightDark
@Composable
private fun ProjectsPreview() {
    TokklTheme {
        ProjectsContent(
            state = ProjectsState(data = ProjectsData(SAMPLE_PROJECTS, WorkspaceDefaults("EUR", 50.0))),
            offline = false,
            cachedProjects = emptyList(),
            clients = listOf(Client(ClientId(1), WorkspaceId(7), "Acme", archived = false)),
            actions = ProjectsActions(),
        )
    }
}

private val SAMPLE_PROJECTS = listOf(
    ProjectDetails(
        ProjectId(11),
        WorkspaceId(7),
        "Tokkl for Android",
        "#0b83d9",
        clientId = ClientId(1),
        clientName = "Acme",
        billable = true,
    ),
    ProjectDetails(ProjectId(12), WorkspaceId(7), "Graffiti wall", "#d94182"),
    ProjectDetails(ProjectId(14), WorkspaceId(7), "Old site", "#525266", active = false),
)
