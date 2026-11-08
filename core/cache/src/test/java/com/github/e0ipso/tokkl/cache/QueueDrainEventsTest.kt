package com.github.e0ipso.tokkl.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

/**
 * Whether a drain that emptied the queue may cancel the background send's waiting retry
 * ([QueueDrainEvents.emptied]): only when the queue reads empty, and never when the disk cannot
 * read it.
 */
class QueueDrainEventsTest {

    @Test
    fun anEmptyQueueReadsEmpty() {
        assertTrue(firstOnly { null }.readsEmpty())
    }

    @Test
    fun aQueuedChangeKeepsTheRetry() {
        assertFalse(firstOnly { CHANGE }.readsEmpty())
    }

    @Test
    fun aQueueTheDiskCannotReadKeepsTheRetry() {
        // Room reports a failed read as an unchecked exception.
        assertFalse(firstOnly { throw IllegalStateException("disk") }.readsEmpty())
    }

    /** A queue that answers only [ChangeStore.first], which is all [readsEmpty] reads. */
    private fun firstOnly(read: () -> PendingChange?): ChangeStore =
        Proxy.newProxyInstance(ChangeStore::class.java.classLoader, arrayOf(ChangeStore::class.java)) { _, method, _ ->
            check(method.name == "first") { "readsEmpty reads ${method.name}" }
            read()
        } as ChangeStore

    private companion object {
        val CHANGE = PendingChange(
            kind = ChangeKind.CREATE,
            entryId = -1,
            workspaceId = 1,
            projectId = null,
            taskId = null,
            description = "",
            start = Instant.parse("2026-11-08T09:00:00Z"),
            stop = null,
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        )
    }
}
