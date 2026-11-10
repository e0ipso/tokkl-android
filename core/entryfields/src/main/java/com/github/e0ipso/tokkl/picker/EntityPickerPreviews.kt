package com.github.e0ipso.tokkl.picker

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the picker, with a small sample of cached rows.

@PreviewLightDark
@Composable
private fun EntityPickerPreview() {
    PickerPreview(offline = false)
}

@PreviewLightDark
@Composable
private fun EntityPickerOfflinePreview() {
    PickerPreview(offline = true)
}

@Composable
private fun PickerPreview(offline: Boolean) {
    TokklTheme {
        Surface {
            EntityPicker(
                source = SAMPLE_SOURCE,
                offline = offline,
                workspaceId = SAMPLE_WORKSPACE,
                original = Picked(),
                picked = Picked(projectId = ProjectId(1), taskId = TaskId(11), tags = listOf("review")),
                onPickedChange = {},
                modifier = Modifier.height(480.dp).padding(TokklTheme.spacing.space4),
            )
        }
    }
}

private val SAMPLE_WORKSPACE = WorkspaceId(1)

private fun project(id: Long, clientId: Long?, name: String, color: String) =
    Project(ProjectId(id), SAMPLE_WORKSPACE, clientId?.let(::ClientId), name, color, active = true, billable = false)

private val SAMPLE_SOURCE = PickerSource(
    projects = listOf(
        project(1, clientId = 7, name = "Website", color = "#0b83d9"),
        project(2, clientId = 8, name = "Graffiti wall", color = "#d94182"),
        project(3, clientId = null, name = "Internal", color = "#2da608"),
    ),
    clients = listOf(
        Client(ClientId(7), SAMPLE_WORKSPACE, "Acme", archived = false),
        Client(ClientId(8), SAMPLE_WORKSPACE, "City council", archived = false),
    ),
    tasks = listOf(Task(TaskId(11), SAMPLE_WORKSPACE, projectId = ProjectId(1), name = "Design", active = true)),
    tags = listOf(Tag(TagId(21), SAMPLE_WORKSPACE, "review"), Tag(TagId(22), SAMPLE_WORKSPACE, "call")),
)
