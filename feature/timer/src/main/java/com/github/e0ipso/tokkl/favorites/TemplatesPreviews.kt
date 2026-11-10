package com.github.e0ipso.tokkl.favorites

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

@PreviewLightDark
@Composable
private fun TemplatesPreview() {
    TemplatesPagePreview(offline = false)
}

@PreviewLightDark
@Composable
private fun TemplatesOfflinePreview() {
    TemplatesPagePreview(offline = true)
}

@Composable
private fun TemplatesPagePreview(offline: Boolean) {
    TokklTheme {
        TemplatesContent(
            state = SAMPLE_TEMPLATES,
            catalog = PickerSource(),
            offline = offline,
            done = null,
            onDoneShow = {},
            onAdd = { _, _ -> },
            onReplace = { _, _, _ -> },
            onRemove = {},
            onMove = { _, _ -> },
        )
    }
}

private val SAMPLE_TEMPLATES = FavoritesState(
    available = true,
    favorites = listOf(
        FavoriteItem(
            EntryFields(description = "Stand-up", projectId = ProjectId(1), billable = true),
            EntryLabels(project = "Website", projectColor = "#0b83d9"),
        ),
        FavoriteItem(EntryFields(description = "Email"), EntryLabels()),
    ),
)
