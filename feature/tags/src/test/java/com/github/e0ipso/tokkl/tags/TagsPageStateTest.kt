package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.ServerProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the tags page shows, worked out from the server's list, the phone's tags and the network. */
class TagsPageStateTest {

    private val work = Tag(TagId(3), WorkspaceId(7), "Work")
    private val cached = Tag(TagId(4), WorkspaceId(7), "Cached")

    @Test
    fun theServersTagsShowOnceLoadedAndThePhonesUntilThen() {
        val loading = TagsPageState(TagsState(loading = true), cachedTags = listOf(cached))
        val loaded = loading.copy(catalog = TagsState(data = TagsData(listOf(work))))

        assertEquals(listOf(TagRow(cached, fromServer = false)), loading.rows)
        assertTrue("the load shows a notice", loading.hasNotices)
        assertEquals(listOf(TagRow(work, fromServer = true)), loaded.rows)
        assertFalse(loaded.hasNotices)
    }

    @Test
    fun offlineOrAnUnreachableServerNeedsTheServerAndChangesNothing() {
        val loaded = TagsState(data = TagsData(listOf(work)))

        assertTrue(TagsPageState(loaded).canChange)
        assertTrue(TagsPageState(loaded, offline = true).serverNeeded)
        assertFalse(TagsPageState(loaded, offline = true).canChange)
        assertTrue(TagsPageState(loaded.copy(loadProblem = ServerProblem.Unreachable)).serverNeeded)
    }
}
