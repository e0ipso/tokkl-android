package com.github.e0ipso.tokkl.shortcuts

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the launcher shortcuts in step with the cached favorites (docs/PRD.md N6). Every change
 * to the favorites lands in the cache, from the Timer page or from a refresh that read the web
 * app's, so following the cache covers both. A sign-out clears the cache and the capabilities, so
 * the shortcuts go too.
 *
 * Android limits how often an app in the background may set its shortcuts, so a list the
 * launcher already has is not set again, as when a new process starts.
 *
 * @param shortcuts the shortcuts the favorites make now.
 * @param published the shortcuts the launcher has, in their order.
 * @param publish replaces them; an empty list removes them all.
 * @param io where the launcher calls run.
 */
class FavoriteShortcutSync(
    private val shortcuts: Flow<List<FavoriteShortcut>>,
    private val published: () -> List<FavoriteShortcut>,
    private val publish: (List<FavoriteShortcut>) -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val lock = Mutex()

    /** Follows the favorites until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the favorites until [shortcuts] ends. */
    suspend fun follow() {
        shortcuts.distinctUntilChanged().collect(::apply)
    }

    /** Publishes [next], unless the launcher has it already. */
    suspend fun apply(next: List<FavoriteShortcut>) {
        lock.withLock {
            if (published() != next) publish(next)
        }
    }

    /** Removes every shortcut. Signing out calls this. */
    suspend fun forget() {
        lock.withLock { publish(emptyList()) }
    }
}
