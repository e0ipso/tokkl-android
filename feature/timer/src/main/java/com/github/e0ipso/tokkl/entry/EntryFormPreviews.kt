package com.github.e0ipso.tokkl.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.LocalDate

// Design-time previews of the entry form, with a sample entry.

@PreviewLightDark
@Composable
private fun EntryFormPreview() {
    FormPreview(SAMPLE_ENTRY)
}

@PreviewLightDark
@Composable
private fun EntryFormNoTimesPreview() {
    FormPreview(SAMPLE_ENTRY.copy(editing = false, noTimes = true, start = "", end = "", duration = "2:00:00"))
}

@Composable
private fun FormPreview(state: EntryFormState.Open) {
    TokklTheme {
        EntryFormContent(state = state, actions = EntryFormActions(), picker = { _, _, _, _, _ -> })
    }
}

private val SAMPLE_ENTRY = EntryFormState.Open(
    editing = true,
    workspaceId = 1,
    fields = EntryFields(description = "Sprint planning", projectId = 1),
    labels = EntryLabels(project = "Website", projectColor = "#0b83d9"),
    original = Picked(projectId = 1),
    date = LocalDate.of(2026, 10, 14),
    start = "08:30",
    end = "12:00",
    duration = "3:30:00",
    mistakes = emptyMap(),
    endsOn = null,
    saving = false,
    noTimesOffered = true,
)
