package com.github.e0ipso.tokkl.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the tags page, with sample tags.

@PreviewLightDark
@Composable
private fun TagsPreview() {
    TokklTheme {
        TagsScreen(
            state = TagsState(data = TagsData(listOf(tag(1, "call"), tag(2, "review"), tag(3, "travel")))),
            offline = false,
            cachedTags = emptyList(),
            actions = TagsActions(),
        )
    }
}

private fun tag(id: Long, name: String) = Tag(TagId(id), SAMPLE_WORKSPACE, name)

private val SAMPLE_WORKSPACE = WorkspaceId(7)
