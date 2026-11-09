package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** How a row of `GET /api/app/trash` reaches the trash page. */
class DeletedEntryTest {

    @Test
    fun aRowReadsWithTypedTimesAndIds() {
        val row = TrashEntry(
            id = EntryId(1),
            description = "Write",
            start = "2026-10-19T09:00:00+00:00",
            stop = "2026-10-19T10:30:00+00:00",
            duration = 5400,
            projectId = 3,
            projectName = "Website",
            deletedAt = "2026-10-20T08:00:00Z",
        )

        assertEquals(
            DeletedEntry(
                id = EntryId(1),
                description = "Write",
                projectId = ProjectId(3),
                projectName = "Website",
                start = Instant.parse("2026-10-19T09:00:00Z"),
                duration = Duration.ofSeconds(5400),
                deletedAt = Instant.parse("2026-10-20T08:00:00Z"),
            ),
            row.deleted(),
        )
    }

    /**
     * A row whose start or deletion time does not read stays in the list without that time, as
     * the page showed it when it read the times while drawing: the rest of the row shows, and it
     * can still be restored or deleted.
     */
    @Test
    fun aRowWhoseTimesDoNotReadStaysWithoutThem() {
        val row = TrashEntry(
            id = EntryId(2),
            start = "yesterday",
            stop = "2026-10-19T10:30:00+00:00",
            duration = 60,
            deletedAt = "2026-10-20 08:00",
        )

        assertEquals(DeletedEntry(EntryId(2), duration = Duration.ofSeconds(60)), row.deleted())
    }

    /** A running entry, without a stop or with API v9's negative duration, has no duration to show. */
    @Test
    fun aRunningRowHasNoDuration() {
        val noStop = TrashEntry(id = EntryId(3), start = "2026-10-19T09:00:00Z", duration = 5400)
        val negative = noStop.copy(stop = "2026-10-19T10:30:00Z", duration = -1_760_864_400)

        assertEquals(null, noStop.deleted().duration)
        assertEquals(null, negative.deleted().duration)
    }
}
