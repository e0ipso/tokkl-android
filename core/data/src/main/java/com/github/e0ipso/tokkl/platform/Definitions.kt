package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.scope.Scope

/** The IO dispatcher, for the Koin definitions of the app and its features. */
fun Scope.io(): CoroutineDispatcher = get(IoDispatcher)

/**
 * The scope a view model gets, as `viewModelScope` makes it by default: the main thread, and a
 * failed child does not cancel the others. The view model cancels it when it is cleared.
 */
fun viewModelCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
