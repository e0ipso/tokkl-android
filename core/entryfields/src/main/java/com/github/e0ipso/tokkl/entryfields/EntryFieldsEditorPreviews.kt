package com.github.e0ipso.tokkl.entryfields

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the entry fields, with sample fields.

@PreviewLightDark
@Composable
private fun EntryFieldsEditorPreview() {
    EditorPreview(EntryFields(description = "Write the history list", projectId = 1, taskId = 11, billable = true))
}

@PreviewLightDark
@Composable
private fun EntryFieldsEditorEmptyPreview() {
    EditorPreview(EntryFields(), labels = EntryLabels())
}

@Composable
private fun EditorPreview(
    fields: EntryFields,
    labels: EntryLabels = EntryLabels(project = "Website", projectColor = "#0b83d9", task = "Design"),
) {
    TokklTheme {
        Surface {
            EntryFieldsEditor(
                fields = fields.copy(tags = if (fields.projectId == null) emptyList() else listOf("review")),
                labels = labels,
                original = Picked(),
                descriptionHint = "What are you working on?",
                onDescriptionChange = {},
                onDescriptionFinish = {},
                onPick = {},
                onBillableChange = {},
                picker = { _, _, _, _ -> },
                modifier = Modifier.padding(TokklTheme.spacing.space4),
            )
        }
    }
}
