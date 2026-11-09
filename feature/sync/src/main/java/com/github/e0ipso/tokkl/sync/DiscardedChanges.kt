package com.github.e0ipso.tokkl.sync

import android.content.Context
import com.github.e0ipso.tokkl.cache.ChangeDiscards
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.LoadedState
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.StartupLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * How many queued changes left the queue because the server no longer had their entry, since the
 * user last dismissed the notice that says so (docs/PRD.md O6): "1 change discarded: the entry was
 * deleted elsewhere". Each discard adds to the count, so one notice covers them all. The count
 * outlives the process, so a discard by the background send with the app closed shows when the
 * app opens. The stored count loads from [store] as this is made, in [scope] on [io].
 */
class DiscardedChanges(
    private val store: ClearableStore<Int>,
    private val scope: CoroutineScope,
    io: CoroutineContext,
) : ChangeDiscards,
    AccountDataOwner,
    StartupLoad {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        ready()
        writing.withLock {
            loaded.set(0)
            store.clear()
        }
    }

    private val loaded = LoadedState(scope, 0, io) { store.load() ?: 0 }

    /** The count the notice shows, or 0 while it shows none. */
    val state: StateFlow<Int> = loaded.state

    override val isReady: Boolean get() = loaded.isReady

    override suspend fun ready() = loaded.ready()

    /** Orders the changes of the count with their writes, so the disk keeps the latest. */
    private val writing = Mutex()

    /** [count] more changes were discarded. Called on the drain's thread, without waiting for the write. */
    override fun add(count: Int) {
        if (count <= 0) return
        set { it + count }
    }

    /** The user read the notice. Sign-out sets the count to 0 too, and deletes it. */
    fun dismiss() = set { 0 }

    /** Changes the count, once it has loaded, and writes it in the background. */
    private fun set(change: (Int) -> Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            writing.withLock {
                val count = change(loaded.value)
                loaded.set(count)
                store.save(count)
            }
        }
    }
}

/**
 * [DiscardedChanges]'s count, in its own private preferences file. It stays on the phone, because
 * the backup and data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class DiscardedChangesStore(context: Context, private val io: CoroutineContext) : ClearableStore<Int> {

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): Int = file.read { getInt(KEY_COUNT, 0) }

    /** Written in the background: a lost write only shows the notice once more, or not at all. */
    override suspend fun save(value: Int) {
        file.write { if (value > 0) putInt(KEY_COUNT, value) else remove(KEY_COUNT) }
    }

    /** Forgets the count at once, for a sign-out. */
    @Throws(IOException::class)
    override suspend fun clear() {
        file.commit { clear() }
    }

    private companion object {
        const val FILE_NAME = "discarded_changes"
        const val KEY_COUNT = "count"
    }
}
