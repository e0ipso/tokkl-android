package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.storage.LoadedState
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The account settings for the whole process, and the one place screens read them from.
 * Sign-in stores the settings from its own `GET /api/v9/me`, and [AccountRefresh] reads them
 * again, so a change made in the web app reaches the phone. They persist, so screens have them
 * offline and after a restart.
 *
 * @param disk keeps the settings on the phone. They load from it as this is made, in [scope] on
 *   [io], off the main thread.
 */
class AccountSettingsSource(
    private val disk: ValueStore<AccountSettings>,
    scope: CoroutineScope,
    io: CoroutineContext,
) : StartupLoad {

    private val held = LoadedState<AccountSettings?>(scope, null, io) { disk.load() }

    /** The stored settings, or null when none are stored yet or before they load. */
    val state: StateFlow<AccountSettings?> = held.state

    /** [state] once loaded, for readers that may run before the load ends. */
    val loaded: Flow<AccountSettings?> = held.loaded

    override val isReady: Boolean get() = held.isReady

    override suspend fun ready() = held.ready()

    suspend fun current(): AccountSettings? = held.current()

    /** The account's default workspace, for the screens, which show once the settings have loaded. */
    fun defaultWorkspaceId(): WorkspaceId? = state.value?.defaultWorkspaceId

    /** Orders the writes and [writes] with them. */
    private val writing = Mutex()

    /** Counts the writes, so a refresh drops an answer that a newer write has replaced. */
    private var writes = 0L

    /** Sign-in calls this with the settings from its own `GET /api/v9/me`. */
    @Throws(IOException::class)
    suspend fun store(settings: AccountSettings) {
        ready()
        writing.withLock { write(settings) }
    }

    /** How many writes and forgets have happened, for [storeRefreshed]. */
    internal suspend fun writeCount(): Long = writing.withLock { writes }

    /**
     * Writes what [merge] makes of the stored settings, unless a write or [forget] came after
     * [writesBefore], as when sign-in stored newer settings while a refresh ran. Settings equal to
     * the stored ones are not written again, and a failed write keeps the stored ones until the
     * next refresh. Returns false when a newer write came.
     */
    internal suspend fun storeRefreshed(
        writesBefore: Long,
        merge: (stored: AccountSettings?) -> AccountSettings,
    ): Boolean {
        ready()
        return writing.withLock {
            if (writes != writesBefore) return@withLock false
            val settings = merge(held.value)
            if (settings != held.value) {
                try {
                    write(settings)
                } catch (_: IOException) {
                    // Keeps the stored settings until the next refresh.
                }
            }
            true
        }
    }

    /**
     * Shows no settings, so a refresh that is running drops its answer. Signing out calls this
     * through [AccountRefresh.forget], and then deletes the stored settings.
     */
    internal suspend fun forget() {
        ready()
        writing.withLock {
            writes++
            held.set(null)
        }
    }

    /** Only while holding [writing]. */
    private suspend fun write(settings: AccountSettings) {
        disk.save(settings)
        writes++
        held.set(settings)
    }
}
