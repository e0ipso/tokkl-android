package com.github.e0ipso.tokkl.cache

/** The fields of the cached [this] that the user picks. */
fun CachedTimeEntry.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)

/** What a tap on this favorite starts. */
fun CachedFavorite.fields(): EntryFields =
    EntryFields(description = description, projectId = projectId, taskId = taskId, tags = tags, billable = billable)
