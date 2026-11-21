package com.github.e0ipso.tokkl.entryfields

import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.picker.PickerSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/**
 * The description suggestions among the cached [entries], newest first, with the projects and
 * clients of [picker], made again each time either changes. It starts with
 * [DescriptionSuggestions.NONE], so a screen shows before the cache is read. The screen limits
 * them to its workspace with [DescriptionSuggestions.inWorkspace].
 */
fun descriptionSuggestions(entries: Flow<List<TimeEntry>>, picker: Flow<PickerSource>): Flow<DescriptionSuggestions> =
    combine(entries, picker) { rows, source -> DescriptionSuggestions.of(rows, source.projects, source.clients) }
        .onStart { emit(DescriptionSuggestions.NONE) }
