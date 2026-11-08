package com.github.e0ipso.tokkl.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * What a store holds, loaded once and kept in memory for the whole process, as each store's
 * holder makes it at process start ([StartupLoads]).
 */
interface StartupLoad {

    /** Whether the load has ended. */
    val isReady: Boolean

    /** Waits for the load to end. */
    suspend fun ready()
}

/**
 * A value the phone keeps on disk, loaded once and held in memory for the process. The load
 * starts as this is made: [load] runs in [scope] and reads the disk on the store's own I/O
 * dispatcher, so making it never blocks. [state] holds [initial] until the load ends, or for good
 * when it fails. A reader that can run before then, outside the screens, waits with [ready] or
 * reads [current] or [loaded]. The screens need not: MainActivity draws nothing until every
 * startup load has ended.
 */
class LoadedState<T>(scope: CoroutineScope, initial: T, load: suspend () -> T) : StartupLoad {

    private val mutable = MutableStateFlow(initial)

    /** The value: [initial] until the load ends, then the stored one and every change after it. */
    val state: StateFlow<T> = mutable.asStateFlow()

    // Unconfined, so the load ends on the store's I/O thread and never waits for the main thread.
    private val loading = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            mutable.value = load()
        } catch (_: IOException) {
            // The value stays as it was made, as when nothing is stored.
        }
    }

    override val isReady: Boolean get() = loading.isCompleted

    override suspend fun ready() = loading.join()

    /** [state] once the load has ended. */
    val loaded: Flow<T> = flow {
        ready()
        emitAll(state)
    }

    /** The value once the load has ended. */
    suspend fun current(): T {
        ready()
        return state.value
    }

    /** Sets the value. Only after [ready], so the load cannot replace it. */
    var value: T
        get() = mutable.value
        set(value) {
            mutable.value = value
        }
}

/**
 * Every store's [StartupLoad], which the app makes at process start, so each load starts then.
 * MainActivity draws its first frame once they have all ended, so no screen shows a value from
 * before its store loaded.
 */
class StartupLoads(private val loads: List<StartupLoad>) {

    val isReady: Boolean get() = loads.all { it.isReady }

    suspend fun ready() = loads.forEach { it.ready() }
}
