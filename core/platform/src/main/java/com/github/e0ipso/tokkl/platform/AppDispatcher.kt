package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

/** The dispatchers the graph holds, each under a qualifier of its own type. */
sealed interface AppDispatcher {
    /** [Dispatchers.Main.immediate], the main thread, which runs at once when already on it. */
    data object Main : AppDispatcher

    /** [Dispatchers.IO], for disk and network work. */
    data object Io : AppDispatcher

    /** [Dispatchers.Default], for computation, such as the timer event stream's clock and the history's grouping. */
    data object Default : AppDispatcher
}

/** [Dispatchers.Main.immediate], the main thread, which runs at once when already on it. */
val MainDispatcher: Qualifier = named<AppDispatcher.Main>()

/** [Dispatchers.IO], for disk and network work. */
val IoDispatcher: Qualifier = named<AppDispatcher.Io>()

/** [Dispatchers.Default], for computation, such as the timer event stream's clock and the history's grouping. */
val DefaultDispatcher: Qualifier = named<AppDispatcher.Default>()

/** The IO dispatcher, for the Koin definitions of the app and its features. */
fun Scope.io(): CoroutineDispatcher = get(IoDispatcher)

/** The dispatcher for work that keeps the CPU busy, such as grouping a list of entries. */
fun Scope.computation(): CoroutineDispatcher = get(DefaultDispatcher)

/**
 * The scope a view model gets, as `viewModelScope` makes it by default: the main thread, here the
 * graph's [MainDispatcher], and a failed child does not cancel the others. The view model cancels
 * it when it is cleared.
 */
fun Scope.viewModelCoroutineScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(MainDispatcher))

/**
 * How a view model shares a flow that its screen reads: while the screen reads it, and for five
 * seconds after, so a rotation does not start it over.
 */
val whileShown: SharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHOWN_TIMEOUT_MILLIS)

/** How long a view model keeps sharing a screen's state after the screen stops reading it. */
private const val SHOWN_TIMEOUT_MILLIS = 5_000L
