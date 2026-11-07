package com.github.e0ipso.tokkl.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the tags page, with sample tags.

@PreviewLightDark
@Composable
private fun TagsPreview() {
    TokklTheme {
        TagsContent(
            state = TagsState(data = TagsData(listOf(Tag(1, 7, "call"), Tag(2, 7, "review"), Tag(3, 7, "travel")))),
            offline = false,
            cachedTags = emptyList(),
            actions = TagsActions(),
        )
    }
}
