package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

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
