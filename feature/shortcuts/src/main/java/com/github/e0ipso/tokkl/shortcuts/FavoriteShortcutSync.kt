package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.collectDurably
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

interface PublishedShortcuts {

    fun published(): List<FavoriteShortcut>

    /** Replaces them; an empty list removes them all. */
    fun publish(shortcuts: List<FavoriteShortcut>)
}

/**
 * Keeps the launcher shortcuts in step with the cached favorites (docs/PRD.md N6). Every change
 * to the favorites lands in the cache, from the Timer page or from a refresh that read the web
 * app's, so following the cache covers both. A sign-out clears the cache and the capabilities, so
 * the shortcuts go too.
 *
 * Android limits how often an app in the background may set its shortcuts, so a list the
 * launcher already has is not set again, as when a new process starts.
 *
 * @param io where the launcher calls run.
 */
class FavoriteShortcutSync(
    private val shortcuts: Flow<List<FavoriteShortcut>>,
    private val launcher: PublishedShortcuts,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    private val lock = Mutex()

    /** Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the favorites until [shortcuts] ends, reading them again after a failure, as [collectDurably] does. */
    suspend fun follow() {
        shortcuts.distinctUntilChanged().collectDurably("FavoriteShortcutSync", action = ::apply)
    }

    suspend fun apply(next: List<FavoriteShortcut>) {
        lock.withLock {
            if (launcher.published() != next) launcher.publish(next)
        }
    }

    /** Removes every shortcut. Signing out calls this. */
    suspend fun forget() {
        lock.withLock { launcher.publish(emptyList()) }
    }
}

/** Publishes the favorites as launcher shortcuts from process start, after the widgets. */
class FavoriteShortcutsHook(private val sync: Lazy<FavoriteShortcutSync>) : AppHook {
    override fun processStart() {
        sync.value.start()
    }
}
