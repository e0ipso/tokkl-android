package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.cache.CachedClient
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
            clients = listOf(CachedClient(1, 7, "Acme", archived = false)),
            actions = ProjectsActions(),
        )
    }
}

private val SAMPLE_PROJECTS = listOf(
    Project(11, 7, "Tokkl for Android", "#0b83d9", clientId = 1, clientName = "Acme", billable = true),
    Project(12, 7, "Graffiti wall", "#d94182"),
    Project(14, 7, "Old site", "#525266", active = false),
)
