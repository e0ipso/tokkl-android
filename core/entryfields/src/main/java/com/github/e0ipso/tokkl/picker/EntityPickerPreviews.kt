package com.github.e0ipso.tokkl.picker

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.cache.CachedTask
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
                picked = Picked(projectId = 1, taskId = 11, tags = listOf("review")),
                onPickedChange = {},
                modifier = Modifier.height(480.dp).padding(TokklTheme.spacing.space4),
            )
        }
    }
}

private const val SAMPLE_WORKSPACE = 1L

private fun project(id: Long, clientId: Long?, name: String, color: String) =
    CachedProject(id, SAMPLE_WORKSPACE, clientId, name, color, active = true, billable = false)

/** Two clients' projects, one without a client, a task and two tags. */
private val SAMPLE_SOURCE = PickerSource(
    projects = listOf(
        project(1, clientId = 7, name = "Website", color = "#0b83d9"),
        project(2, clientId = 8, name = "Graffiti wall", color = "#d94182"),
        project(3, clientId = null, name = "Internal", color = "#2da608"),
    ),
    clients = listOf(
        CachedClient(7, SAMPLE_WORKSPACE, "Acme", archived = false),
        CachedClient(8, SAMPLE_WORKSPACE, "City council", archived = false),
    ),
    tasks = listOf(CachedTask(11, SAMPLE_WORKSPACE, projectId = 1, name = "Design", active = true)),
    tags = listOf(CachedTag(21, SAMPLE_WORKSPACE, "review"), CachedTag(22, SAMPLE_WORKSPACE, "call")),
)
