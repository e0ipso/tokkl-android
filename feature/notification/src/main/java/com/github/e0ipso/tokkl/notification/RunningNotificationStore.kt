package com.github.e0ipso.tokkl.notification

import android.content.Context
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The running entry the notification last followed, its key and id, in its own private
 * preferences file. The key is a hash, so the file holds no description or name. It stays on the
 * phone, because the backup and data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class RunningNotificationStore(context: Context, private val io: CoroutineContext) : ValueStore<FollowedEntry?> {

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): FollowedEntry? = file.read {
        val key = getString(KEY_SHOWN, null) ?: return@read null
        val entryId = if (contains(KEY_SHOWN_ENTRY)) getLong(KEY_SHOWN_ENTRY, 0) else null
        FollowedEntry(key, entryId?.let(::EntryId))
    }

    @Throws(IOException::class)
    override suspend fun save(value: FollowedEntry?) {
        file.commit {
            if (value == null) remove(KEY_SHOWN) else putString(KEY_SHOWN, value.key)
            if (value?.entryId == null) remove(KEY_SHOWN_ENTRY) else putLong(KEY_SHOWN_ENTRY, value.entryId.value)
        }
    }

    private companion object {
        const val FILE_NAME = "running_notification"
        const val KEY_SHOWN = "shown"
        const val KEY_SHOWN_ENTRY = "shown_entry"
    }
}
