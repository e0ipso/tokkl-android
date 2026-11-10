package com.github.e0ipso.tokkl.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * What a store holds, loaded once and kept in memory for the whole process, as each store's
 * holder makes it at process start ([StartupLoads]).
 */
interface StartupLoad {

    val isReady: Boolean

    suspend fun ready()
}

/** How the load of a [LoadedState] ended. */
sealed interface LoadResult {

    /** The value is the stored one, or a change made since. */
    data object Loaded : LoadResult

    /** The store could not be read, so the value is still the one it was made with. */
    data class Failed(val cause: IOException) : LoadResult
}

/**
 * A value the phone keeps on disk, loaded once and held in memory for the process. The load
 * starts as this is made: [load] runs in [scope] on [loadContext], the I/O dispatcher in the app,
 * so making it never blocks and the load never waits for the main thread. [state] holds [initial]
 * until the load ends, and still after a load that failed. A reader that can run before then,
 * outside the screens, waits with [ready] or reads [current] or [loaded]. The screens need not:
 * MainActivity draws nothing until every startup load has ended. Only [set] changes the value,
 * once the load has ended, so the load can never replace a change. A writer that must not store
 * over a value it could not read asks [loadResult], which reads the store again after a failure.
 */
class LoadedState<T>(
    scope: CoroutineScope,
    initial: T,
    private val loadContext: CoroutineContext,
    private val load: suspend () -> T,
) : StartupLoad {

    private val mutable = MutableStateFlow(initial)

    /** The value: [initial] until the load ends, then the stored one and every change after it. */
    val state: StateFlow<T> = mutable.asStateFlow()

    /** Orders a load read again after a failure with [set]. */
    private val reading = Mutex()

    /** How the last load ended; null until the first one has. Written under [reading] after the first. */
    @Volatile
    private var result: LoadResult? = null

    private val loading = scope.launch(loadContext) { result = read() }

    override val isReady: Boolean get() = loading.isCompleted

    override suspend fun ready() = loading.join()

    val loaded: Flow<T> = flow {
        ready()
        emitAll(state)
    }

    suspend fun current(): T {
        ready()
        return state.value
    }

    /**
     * How the load ended, once it has. After a failure, unless [set] changed the value since, it
     * reads the store again first, so a failure that has passed shows the stored value.
     */
    suspend fun loadResult(): LoadResult {
        ready()
        return reading.withLock {
            if (result is LoadResult.Failed) result = withContext(loadContext) { read() }
            checkNotNull(result)
        }
    }

    /** The value stays as it was made after a failure, as when nothing is stored. */
    private suspend fun read(): LoadResult = try {
        mutable.value = load()
        LoadResult.Loaded
    } catch (failed: IOException) {
        LoadResult.Failed(failed)
    }

    val value: T get() = mutable.value

    /**
     * Sets the value once the load has ended, so the load cannot replace it. Once it has, as for
     * every change the screens make, the value changes before this returns, without suspending,
     * unless [loadResult] is reading the store again, which it waits for.
     */
    suspend fun set(value: T) {
        ready()
        reading.withLock {
            mutable.value = value
            result = LoadResult.Loaded
        }
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
